/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.scan;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import freja.model.Folder;
import freja.model.Photo;
import freja.store.Library;
import freja.store.Library.FileStamp;
import freja.store.PhotoImporter;
import freja.util.PathUtil;

/**
 * Keeps the catalog in sync with the folders on disk.
 * <p>
 * A scan walks the folder tree and only decodes files whose size or modification time changed.
 * While the application is running, a {@link WatchService} catches new, changed and deleted files,
 * and a periodic sweep repairs anything the platform watcher missed. Scans and watch events share
 * one path-level guard, so the same image is never imported twice at the same time.
 * </p>
 */
public final class LibraryScanner implements AutoCloseable {

    /** A snapshot of the scanner state for the status bar. */
    public record State(boolean scanning, boolean watching, int total, int processed, int imported, int errors,
            String message, Duration elapsed) {

        public static final State IDLE = new State(false, false, 0, 0, 0, 0, "", Duration.ZERO);
    }

    /** Receives scanner events. Methods may be called from a background thread. */
    public interface Listener {

        default void onStateChanged(State state) {
        }

        default void onLibraryChanged() {
        }
    }

    /** How long a file system event is debounced. */
    private static final long WATCH_DELAY_MS = 700;

    /** How often a full sweep runs when watching is enabled. */
    private static final long SWEEP_MINUTES = 5;

    /** How many path keys are sent to SQLite in one touch statement. */
    private static final int TOUCH_BATCH = 400;

    /** How many photo rows share one commit during a scan. */
    private static final int SAVE_BATCH = 100;

    private final Library library;

    private final ExecutorService importers;

    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "freja-scan"));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> daemon(runnable, "freja-schedule"));

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Long, Folder> folders = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Folder> foldersByKey = new ConcurrentHashMap<>();

    /** Keeps the directory walk from queueing the whole library for decoding at once. */
    private final Semaphore importPermits;

    private final Object importMonitor = new Object();

    private int activeImports;

    private final Set<String> importing = ConcurrentHashMap.newKeySet();

    /** Decoded photos waiting for a grouped commit; watch events bypass this queue. */
    private final ConcurrentLinkedQueue<Library.PhotoSave> pendingSaves = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap<Long, String> activeTokens = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingEvents = new ConcurrentHashMap<>();

    private final Set<Long> requestedFolders = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean scanAllRequested = new AtomicBoolean();

    private final AtomicBoolean scanTaskRunning = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicInteger busy = new AtomicInteger();

    private final AtomicInteger total = new AtomicInteger();

    private final AtomicInteger processed = new AtomicInteger();

    private final AtomicInteger imported = new AtomicInteger();

    private final AtomicInteger errors = new AtomicInteger();

    private final AtomicLong lastState = new AtomicLong();

    private final AtomicLong lastChange = new AtomicLong();

    private final AtomicBoolean notifyScheduled = new AtomicBoolean();

    private final ConcurrentHashMap<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();

    private final Set<Path> watchedPaths = ConcurrentHashMap.newKeySet();

    private volatile boolean scanning;

    /** When the current scan started, in nanoseconds, or 0 when none is running. */
    private volatile long scanStartedNanos;

    /** How long the last completed scan took, kept for the completion message. */
    private volatile long lastElapsedNanos;

    private volatile boolean watching;

    private volatile String message = "";

    private volatile String lastError;

    private WatchService watchService;

    private Thread watchThread;

    /**
     * Creates a scanner for a library.
     *
     * @param library the catalog to maintain
     */
    public LibraryScanner(Library library) {
        this.library = library;
        int threads = Math.max(2, Math.min(12, Runtime.getRuntime().availableProcessors() - 1));
        // Importing is background work: let the event dispatch thread and the UI loaders win whenever
        // they wake up. The threads still use every idle core while the user is not interacting.
        this.importers = Executors.newFixedThreadPool(threads,
                runnable -> daemon(runnable, "freja-import", Thread.NORM_PRIORITY - 2));
        this.importPermits = new Semaphore(threads * 2);
    }

    /**
     * Adds a listener.
     *
     * @param listener a listener
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener a listener
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Starts the scanner, registers the file system watcher and schedules the initial scan.
     */
    public void start() {
        start(true);
    }

    /**
     * Starts the scanner.
     *
     * @param watch whether file system events should be observed continuously
     */
    public void start(boolean watch) {
        refreshFolders();
        if (watch && !closed.get()) {
            try {
                watchService = watchService == null ? java.nio.file.FileSystems.getDefault().newWatchService() : watchService;
                watching = true;
                watchThread = daemon(() -> watchLoop(), "freja-watch");
                watchThread.start();
                scheduler.execute(() -> {
                    for (Folder folder : folders.values()) {
                        if (folder.explicit()) {
                            registerTree(folder.path());
                        }
                    }
                });
            } catch (IOException | RuntimeException e) {
                watching = false;
                lastError = "ファイル監視を開始できませんでした: " + e.getMessage();
            }
            // Even when the platform watcher is unavailable, the sweep keeps the catalog current.
            scheduler.scheduleWithFixedDelay(() -> {
                if (!closed.get()) {
                    requestScan();
                }
            }, SWEEP_MINUTES, SWEEP_MINUTES, TimeUnit.MINUTES);
        }
        publishState(true);
        requestScan();
    }

    /**
     * Registers a directory and scans it.
     *
     * @param directory a directory
     * @return the registered folder
     */
    public Folder addFolder(Path directory) {
        Folder folder = library.addFolder(directory);
        remember(folder);
        if (watching) {
            scheduler.execute(() -> registerTree(folder.path()));
        }
        publishState(true);
        requestFolderScan(folder.id());
        return folder;
    }

    /**
     * Removes a folder from the catalog. Files on disk are not touched.
     *
     * @param folderId the folder identifier
     */
    public void removeFolder(long folderId) {
        Folder folder = folders.get(folderId);
        if (folder != null) {
            if (watching) {
                unregisterTree(folder.path());
            }
            folders.entrySet().removeIf(entry -> PathUtil.contains(folder.path(), entry.getValue().path()));
            foldersByKey.entrySet().removeIf(entry -> PathUtil.contains(folder.path(), Path.of(entry.getKey())));
        }
        library.removeFolder(folderId);
        notifyChanged(true);
        publishState(true);
    }

    /**
     * Scans every registered folder in the background.
     */
    public void scanAll() {
        requestScan();
    }

    /**
     * Scans one folder in the background.
     *
     * @param folderId the folder identifier
     */
    public void scanFolder(long folderId) {
        requestFolderScan(folderId);
    }

    /**
     * Forces one photo to be decoded again and updates its catalog row.
     *
     * @param photo a photo
     */
    public void reimport(Photo photo) {
        library.invalidate(photo.id());
        Folder owner = folderFor(photo.path()).orElse(null);
        Path parent = photo.path().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            owner = library.ensureFolder(parent);
            remember(owner);
        }
        requestImport(owner == null ? photo.folderId() : owner.id(), photo.path());
    }

    /**
     * Returns the latest state.
     *
     * @return the scanner state
     */
    public State state() {
        long nanos = scanning && scanStartedNanos > 0 ? System.nanoTime() - scanStartedNanos : lastElapsedNanos;
        return new State(scanning, watching, total.get(), processed.get(), imported.get(), errors.get(), message,
                Duration.ofNanos(Math.max(0, nanos)));
    }

    /**
     * Waits until no scan or import is running.
     *
     * @param timeout the maximum wait
     * @return true when the scanner became idle
     */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!scanTaskRunning.get() && busy.get() == 0 && pendingEvents.isEmpty()) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void refreshFolders() {
        folders.clear();
        foldersByKey.clear();
        for (Folder folder : library.folders()) {
            folders.put(folder.id(), folder);
            foldersByKey.put(PathUtil.key(folder.path()), folder);
        }
    }

    private void remember(Folder folder) {
        folders.put(folder.id(), folder);
        foldersByKey.put(PathUtil.key(folder.path()), folder);
    }

    private void requestScan() {
        scanAllRequested.set(true);
        launchScanTask();
    }

    private void requestFolderScan(long folderId) {
        requestedFolders.add(folderId);
        launchScanTask();
    }

    private void launchScanTask() {
        if (closed.get()) {
            return;
        }
        if (scanTaskRunning.compareAndSet(false, true)) {
            coordinator.execute(this::runScanLoop);
        }
    }

    private void runScanLoop() {
        try {
            while (!closed.get()) {
                boolean all = scanAllRequested.getAndSet(false);
                Set<Long> specific = new HashSet<>(requestedFolders);
                requestedFolders.removeAll(specific);
                if (!all && specific.isEmpty()) {
                    break;
                }
                refreshFolders();
                List<Folder> targets;
                if (all) {
                    targets = folders.values().stream().filter(Folder::explicit).toList();
                } else {
                    targets = specific.stream().map(folders::get).filter(folder -> folder != null).toList();
                }
                if (targets.isEmpty()) {
                    continue;
                }
                scanning = true;
                scanStartedNanos = System.nanoTime();
                lastElapsedNanos = 0;
                total.set(0);
                processed.set(0);
                imported.set(0);
                errors.set(0);
                lastError = null;
                message = "";
                publishState(true);
                for (Folder folder : targets) {
                    if (closed.get()) {
                        break;
                    }
                    scanFolderInternal(folder);
                }
                lastElapsedNanos = System.nanoTime() - scanStartedNanos;
                scanStartedNanos = 0;
                scanning = false;
                message = lastError == null ? "" : lastError;
                publishState(true);
                notifyChanged(true);
            }
        } catch (RuntimeException e) {
            lastError = "スキャンに失敗しました: " + e.getMessage();
            errors.incrementAndGet();
        } finally {
            if (scanStartedNanos > 0) {
                lastElapsedNanos = System.nanoTime() - scanStartedNanos;
                scanStartedNanos = 0;
            }
            scanning = false;
            scanTaskRunning.set(false);
            publishState(true);
            if (!closed.get() && (scanAllRequested.get() || !requestedFolders.isEmpty())) {
                launchScanTask();
            }
        }
    }

    private void scanFolderInternal(Folder folder) {
        Path root = folder.path();
        if (!Files.isDirectory(root)) {
            lastError = "フォルダが見つかりません: " + root;
            errors.incrementAndGet();
            return;
        }
        String token = library.beginScan(folder.id());
        activeTokens.put(folder.id(), token);
        String rootKey = PathUtil.key(root);
        Map<Path, Long> owners = new HashMap<>();
        Map<String, FileStamp> stamps = library.currentFiles(root);
        Map<Long, List<String>> unchanged = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> candidates = new HashSet<>();
        for (Folder known : folders.values()) {
            if (!known.explicit() && known.id() != folder.id() && PathUtil.contains(root, known.path())) {
                candidates.add(known.id());
            }
        }
        AtomicBoolean walkFinished = new AtomicBoolean(true);
        message = folder.name() + " をスキャン中";
        publishState(true);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Reports the directory the walk is in, not the individual file being decoded.
                    message = PathUtil.folderName(dir) + " をスキャン中";
                    // Almost every directory is already known from refreshFolders(). Reusing that
                    // entry avoids a SELECT with a COUNT subquery per directory of the tree.
                    Folder managed = foldersByKey.get(PathUtil.key(dir));
                    if (managed == null) {
                        managed = library.ensureFolder(dir);
                    }
                    remember(managed);
                    visited.add(managed.id());
                    owners.put(dir, managed.id());
                    if (watching) {
                        register(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!PathUtil.isImage(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    total.incrementAndGet();
                    long ownerId = owners.getOrDefault(file.getParent(), folder.id());
                    String key = PathUtil.key(rootKey, root, file);
                    try {
                        FileStamp stamp = stamps.remove(key);
                        if (stamp != null && stamp.size() == attrs.size()
                                && stamp.modifiedAt() == attrs.lastModifiedTime().toMillis()) {
                            unchanged.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(key);
                            processed.incrementAndGet();
                            publishState(false);
                        } else {
                            submitImport(ownerId, file, token, attrs);
                        }
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                        lastError = e.getMessage();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    walkFinished.set(false);
                    errors.incrementAndGet();
                    lastError = "読み込めません: " + file.getFileName();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    if (e != null) {
                        walkFinished.set(false);
                        errors.incrementAndGet();
                        lastError = "読み込めません: " + dir;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            awaitImports();
            drainAllSaves();
            for (Map.Entry<Long, List<String>> entry : unchanged.entrySet()) {
                List<String> keys = entry.getValue();
                for (int from = 0; from < keys.size(); from += TOUCH_BATCH) {
                    library.touchBatch(keys.subList(from, Math.min(from + TOUCH_BATCH, keys.size())), token, entry.getKey());
                }
            }
            if (walkFinished.get() && !closed.get()) {
                library.finishScanTree(root, token);
                candidates.removeAll(visited);
                if (!candidates.isEmpty()) {
                    int removed = library.deleteFolders(candidates);
                    if (removed > 0) {
                        folders.keySet().removeAll(candidates);
                        foldersByKey.values().removeIf(candidates::contains);
                        notifyChanged(true);
                    }
                }
            }
            library.markFolderScanned(folder.id());
        } catch (IOException | RuntimeException e) {
            errors.incrementAndGet();
            lastError = "スキャンできません: " + root + " (" + e.getMessage() + ")";
        } finally {
            activeTokens.remove(folder.id());
            processed.set(total.get());
            publishState(true);
        }
    }

    /**
     * Queues one decode while the directory walk continues. The semaphore keeps only a bounded
     * number of files in flight; the monitor lets the scan wait for every queued decode before it
     * prunes rows that were not seen.
     */
    private void submitImport(long folderId, Path file, String token, BasicFileAttributes attrs) {
        if (closed.get()) {
            return;
        }
        try {
            importPermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (importMonitor) {
            activeImports++;
        }
        try {
            importers.submit(() -> {
                try {
                    importFile(folderId, file, token, attrs);
                } catch (RuntimeException e) {
                    errors.incrementAndGet();
                    lastError = e.getMessage();
                } finally {
                    releaseImport();
                }
            });
        } catch (RuntimeException e) {
            releaseImport();
            errors.incrementAndGet();
            lastError = e.getMessage();
        }
    }

    private void releaseImport() {
        importPermits.release();
        synchronized (importMonitor) {
            activeImports--;
            importMonitor.notifyAll();
        }
    }

    private void awaitImports() {
        synchronized (importMonitor) {
            while (activeImports > 0 && !closed.get()) {
                try {
                    importMonitor.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void recordSave(long folderId, Path file, long size, long modified,
            PhotoImporter.AnalyzedPhoto analyzed, String effectiveToken, String scanToken) {
        Library.PhotoSave save = new Library.PhotoSave(folderId, file, size, modified, analyzed, effectiveToken);
        if (scanToken == null) {
            // A watch event is a single write; a transaction of its own is cheaper than a batch.
            library.savePhoto(save);
            return;
        }
        pendingSaves.add(save);
        if (pendingSaves.size() >= SAVE_BATCH) {
            drainSaves();
        }
    }

    private void drainSaves() {
        List<Library.PhotoSave> batch = new ArrayList<>(SAVE_BATCH);
        Library.PhotoSave next;
        while (batch.size() < SAVE_BATCH && (next = pendingSaves.poll()) != null) {
            batch.add(next);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            library.savePhotoBatch(batch);
        } catch (RuntimeException e) {
            errors.incrementAndGet();
            lastError = e.getMessage();
        }
    }

    private void drainAllSaves() {
        while (!pendingSaves.isEmpty()) {
            drainSaves();
        }
    }

    private void requestImport(long folderId, Path file) {
        if (closed.get()) {
            return;
        }
        String key = PathUtil.key(file);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingEvents.remove(key);
            if (Files.isRegularFile(file)) {
                importFile(folderId, file, null);
            } else {
                library.deletePhoto(key);
                notifyChanged(true);
            }
        }, WATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pendingEvents.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void importFile(long folderId, Path file, String token) {
        importFile(folderId, file, token, null);
    }

    /**
     * @param supplied attributes already read by the directory walk, or null for a watch event
     */
    private void importFile(long folderId, Path file, String token, BasicFileAttributes supplied) {
        if (closed.get() || !PathUtil.isImage(file)) {
            return;
        }
        String key = PathUtil.key(file);
        if (!importing.add(key)) {
            return;
        }
        busy.incrementAndGet();
        try {
            BasicFileAttributes before = supplied != null ? supplied : Files.readAttributes(file, BasicFileAttributes.class);
            if (!before.isRegularFile()) {
                return;
            }
            long size = before.size();
            long modified = before.lastModifiedTime().toMillis();
            String effectiveToken = token != null ? token : activeTokenFor(file);
            // A scan has already compared the catalog stamp; only watch events need this check.
            if (supplied == null && library.isCurrent(key, size, modified)) {
                library.touch(key, effectiveToken, folderId);
                return;
            }
            // During a scan the directory walk owns the message. Only a watch event has no walk to
            // describe it, so name the containing directory rather than the individual file.
            if (!scanning) {
                Path parent = file.getParent();
                String directory = parent == null ? file.getFileName().toString() : PathUtil.folderName(parent);
                message = directory + " をスキャン中";
            }
            PhotoImporter.AnalyzedPhoto analyzed;
            try {
                analyzed = PhotoImporter.analyze(file, modified);
            } catch (IOException e) {
                recordSave(folderId, file, size, modified, null, effectiveToken, token);
                errors.incrementAndGet();
                lastError = e.getMessage();
                notifyChanged(false);
                return;
            }
            BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class);
            if (after.size() != size || after.lastModifiedTime().toMillis() != modified) {
                // The file changed while it was being decoded. Try again once it settles.
                requestImport(folderId, file);
                return;
            }
            recordSave(folderId, file, size, modified, analyzed, effectiveToken, token);
            imported.incrementAndGet();
            notifyChanged(false);
        } catch (IOException | RuntimeException e) {
            errors.incrementAndGet();
            lastError = "読み込めません: " + file.getFileName();
        } finally {
            importing.remove(key);
            processed.incrementAndGet();
            busy.decrementAndGet();
            publishState(false);
        }
    }

    /**
     * Finds the token of an active scan whose root contains the file.
     */
    private String activeTokenFor(Path file) {
        Path parent = file.getParent();
        if (parent != null) {
            Folder owner = foldersByKey.get(PathUtil.key(parent));
            if (owner != null) {
                return activeTokens.get(owner.id());
            }
        }
        String token = null;
        int length = -1;
        for (Map.Entry<Long, String> entry : activeTokens.entrySet()) {
            Folder folder = folders.get(entry.getKey());
            if (folder != null && PathUtil.contains(folder.path(), file)) {
                int candidate = folder.path().toString().length();
                if (candidate > length) {
                    length = candidate;
                    token = entry.getValue();
                }
            }
        }
        return token;
    }

    private void notifyChanged(boolean force) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastChange.get();
        if (force || elapsed >= 300) {
            fireChanged(now);
            return;
        }
        // A change which arrives just after a notification still has to reach the UI, so coalesce
        // it into one delayed notification instead of dropping it.
        if (notifyScheduled.compareAndSet(false, true)) {
            try {
                scheduler.schedule(() -> {
                    notifyScheduled.set(false);
                    fireChanged(System.currentTimeMillis());
                }, Math.max(1, 320 - elapsed), TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                notifyScheduled.set(false);
            }
        }
    }

    private void fireChanged(long now) {
        lastChange.set(now);
        for (Listener listener : listeners) {
            try {
                listener.onLibraryChanged();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void publishState(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastState.get() < 80) {
            return;
        }
        lastState.set(now);
        State state = state();
        for (Listener listener : listeners) {
            try {
                listener.onStateChanged(state);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void watchLoop() {
        while (!closed.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }
            Path directory = watchKeys.get(key);
            if (directory == null) {
                key.reset();
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    requestScan();
                    continue;
                }
                Path child = directory.resolve((Path) event.context());
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    registerTree(child);
                }
                scheduleWatchEvent(child);
            }
            if (!key.reset()) {
                watchKeys.remove(key);
                watchedPaths.remove(directory);
            }
        }
    }

    private void scheduleWatchEvent(Path path) {
        if (closed.get()) {
            return;
        }
        String key = PathUtil.key(path);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingEvents.remove(key);
            handleWatchEvent(path);
        }, WATCH_DELAY_MS, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pendingEvents.put(key, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void handleWatchEvent(Path path) {
        Optional<Folder> owner = folderFor(path);
        if (owner.isEmpty()) {
            return;
        }
        Folder folder = owner.get();
        String key = PathUtil.key(path);
        if (Files.isDirectory(path)) {
            boolean known = folders.values().stream().anyMatch(value -> PathUtil.key(value.path()).equals(key));
            if (!known) {
                Folder managed = library.ensureFolder(path);
                remember(managed);
                if (watching) {
                    registerTree(path);
                }
                notifyChanged(true);
                requestFolderScan(managed.id());
            }
        } else if (Files.isRegularFile(path)) {
            if (PathUtil.isImage(path)) {
                Path parent = path.getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    folder = library.ensureFolder(parent);
                    remember(folder);
                }
                importFile(folder.id(), path, null);
            }
        } else {
            boolean deleted = library.deletePhoto(key);
            int removedPhotos = 0;
            int removedFolders = 0;
            if (!deleted) {
                removedPhotos = library.deleteUnder(folder.id(), path);
                removedFolders = library.removeAutoFoldersUnder(path);
            }
            if (removedFolders > 0) {
                folders.entrySet().removeIf(entry -> PathUtil.contains(path, entry.getValue().path()));
                foldersByKey.entrySet().removeIf(entry -> PathUtil.contains(path, Path.of(entry.getKey())));
            }
            if (deleted || removedPhotos > 0 || removedFolders > 0) {
                notifyChanged(true);
            }
        }
    }

    private Optional<Folder> folderFor(Path path) {
        // The exact directory wins, matching the longest-containing-folder fallback below. Files
        // are not folders, so their parent is the usual fast path.
        Folder exact = foldersByKey.get(PathUtil.key(path));
        if (exact != null) {
            return Optional.of(exact);
        }
        Path parent = path.getParent();
        if (parent != null) {
            Folder owner = foldersByKey.get(PathUtil.key(parent));
            if (owner != null) {
                return Optional.of(owner);
            }
        }
        Folder best = null;
        int bestLength = -1;
        for (Folder folder : folders.values()) {
            if (PathUtil.contains(folder.path(), path)) {
                int length = folder.path().toString().length();
                if (length > bestLength) {
                    best = folder;
                    bestLength = length;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private void registerTree(Path root) {
        if (watchService == null || !Files.isDirectory(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void register(Path directory) {
        if (watchService == null) {
            return;
        }
        // Resolving every directory through toRealPath() costs one filesystem call per folder.
        // The walk already hands us absolute, normalized paths, so that call is unnecessary here.
        Path normalized = directory.toAbsolutePath().normalize();
        if (!watchedPaths.add(normalized)) {
            return;
        }
        try {
            WatchKey key = normalized.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(key, normalized);
        } catch (IOException | ClosedWatchServiceException e) {
            watchedPaths.remove(normalized);
        }
    }

    private void unregisterTree(Path root) {
        for (var entry : watchKeys.entrySet()) {
            if (PathUtil.contains(root, entry.getValue())) {
                entry.getKey().cancel();
                watchKeys.remove(entry.getKey());
                watchedPaths.remove(entry.getValue());
            }
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        return daemon(runnable, name, Thread.NORM_PRIORITY);
    }

    private static Thread daemon(Runnable runnable, String name, int priority) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.setPriority(priority);
        return thread;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        watching = false;
        for (ScheduledFuture<?> future : pendingEvents.values()) {
            future.cancel(false);
        }
        pendingEvents.clear();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
        scheduler.shutdownNow();
        coordinator.shutdownNow();
        importers.shutdownNow();
        try {
            coordinator.awaitTermination(3, TimeUnit.SECONDS);
            importers.awaitTermination(3, TimeUnit.SECONDS);
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
