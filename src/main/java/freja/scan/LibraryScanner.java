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

import java.io.IOError;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import freja.model.Folder;
import freja.model.Photo;
import freja.store.Archives;
import freja.store.Library;
import freja.store.Library.FileStamp;
import freja.store.PhotoImporter;
import freja.util.ArchiveSettings;
import freja.util.PathUtil;
import freja.util.PicasaTags;

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

    /** Give up on a single unresponsive directory and keep scanning the rest of the tree. */
    private static final long DIRECTORY_TIMEOUT_SECONDS = Long.getLong("freja.scan.directoryTimeoutSeconds", 10);

    /**
     * A share which is merely slow - a NAS over SMB, a spindle disk spun down, an antivirus making
     * its first pass - needs far longer than the first budget without being unresponsive, so a
     * timed-out listing is waited on once more at this length before the directory is skipped.
     */
    private static final long DIRECTORY_RETRY_SECONDS = Long.getLong("freja.scan.directoryRetrySeconds", 300);

    /** How long a walk waits for a free decode slot before looking whether anything still moves. */
    private static final long IMPORT_WAIT_SECONDS = Long.getLong("freja.scan.importWaitSeconds", 5);

    /** No import has finished for this long while slots stay full: the media really is dead. */
    private static final long IMPORT_STALL_SECONDS = Long.getLong("freja.scan.importStallSeconds", 90);

    /** How long a share which answers nothing at all is waited on before the folder is given up on. */
    private static final int MAX_CONSECUTIVE_DIRECTORY_FAILURES = Integer
            .getInteger("freja.scan.consecutiveDirectoryFailures", 8);

    /** Consecutive directory listings which failed, so a departed share is noticed early. */
    private final AtomicInteger directoryFailures = new AtomicInteger();

    private record Entry(Path path, BasicFileAttributes attrs) {
    }

    private record Listing(BasicFileAttributes attrs, List<Entry> children) {
    }

    private final Library library;

    private final ExecutorService importers;

    /** Walks run separately so a blocked filesystem call can be abandoned with a timeout. */
    private final ExecutorService walkers = Executors.newCachedThreadPool(runnable -> daemon(runnable, "freja-walk"));

    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "freja-scan"));

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> daemon(runnable, "freja-schedule"));

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Long, Folder> folders = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Folder> foldersByKey = new ConcurrentHashMap<>();

    /** Keeps the directory walk from queueing the whole library for decoding at once. */
    private final Semaphore importPermits;

    private final Object importMonitor = new Object();

    private int activeImports;

    /**
     * When the last import finished. Progress, not the age of one file, decides whether the media is
     * dead: a network share keeps serving while its decoders are simply busy, and only a run of
     * silence means nothing is coming back.
     */
    private final AtomicLong lastImportProgress = new AtomicLong();

    /** Set when no decoder finishes any more, so the walk skips the rest instead of waiting per file. */
    private volatile boolean importsBlocked;

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
        start(watch, true);
    }

    /** Starts the scanner, optionally skipping the initial full scan and the periodic sweep. */
    public void start(boolean watch, boolean initialScan) {
        start(watch, initialScan, false);
    }

    public void start(boolean watch, boolean initialScan, boolean sweep) {
        refreshFolders();
        if (watch && !closed.get()) {
            try {
                watchService = watchService == null ? java.nio.file.FileSystems.getDefault().newWatchService() : watchService;
                watching = true;
                watchThread = daemon(() -> watchLoop(), "freja-watch");
                watchThread.start();
                scheduler.execute(() -> {
                    for (Folder folder : folders.values()) {
                        if (folder.explicit() && folder.scanEnabled()) {
                            registerTree(folder.path());
                        }
                    }
                });
            } catch (IOException | RuntimeException e) {
                watching = false;
                lastError = "ファイル監視を開始できませんでした: " + e.getMessage();
            }
            if (sweep) {
                // Even when the platform watcher is unavailable, the sweep keeps the catalog current.
                scheduler.scheduleWithFixedDelay(() -> {
                    if (!closed.get()) {
                        requestScan();
                    }
                }, SWEEP_MINUTES, SWEEP_MINUTES, TimeUnit.MINUTES);
            }
        }
        publishState(true);
        if (initialScan) {
            requestScan();
        }
    }

    /**
     * Registers a directory and scans it.
     *
     * @param directory a directory
     * @return the registered folder
     */
    public Folder addFolder(Path directory) {
        return addFolder(directory, true);
    }

    /** Registers a folder and optionally scans it right away. */
    public Folder addFolder(Path directory, boolean scan) {
        Folder folder = library.addFolder(directory);
        remember(folder);
        if (watching && folder.scanEnabled()) {
            scheduler.execute(() -> registerTree(folder.path()));
        }
        publishState(true);
        if (scan) {
            requestFolderScan(folder.id());
        }
        return folder;
    }

    /** Removes every folder and its photos from the catalog, and stops watching all of them. */
    public void clearFolders() {
        for (WatchKey key : watchKeys.keySet()) {
            key.cancel();
        }
        watchKeys.clear();
        watchedPaths.clear();
        for (ScheduledFuture<?> future : pendingEvents.values()) {
            future.cancel(false);
        }
        pendingEvents.clear();
        pendingSaves.clear();
        requestedFolders.clear();
        scanAllRequested.set(false);
        folders.clear();
        foldersByKey.clear();
        activeTokens.clear();
        library.clearFolders();
        notifyChanged(true);
        publishState(true);
    }

    /**
     * Removes a folder from the catalog. Files on disk are not touched.
     *
     * @param folderId the folder identifier
     */
    public void removeFolder(long folderId) {
        removeFolders(java.util.List.of(folderId));
    }

    /**
     * Removes several folder trees from the catalog in one transaction and drops their watch
     * registrations. Files on disk are not touched.
     *
     * @param folderIds folder identifiers
     */
    public void removeFolders(Collection<Long> folderIds) {
        for (Long folderId : folderIds) {
            Folder folder = folders.get(folderId);
            if (folder != null) {
                if (watching) {
                    unregisterTree(folder.path());
                }
                folders.entrySet().removeIf(entry -> PathUtil.contains(folder.path(), entry.getValue().path()));
                foldersByKey.entrySet().removeIf(entry -> PathUtil.contains(folder.path(), Path.of(entry.getKey())));
            }
        }
        library.removeFolders(folderIds);
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
        Folder folder = folders.get(folderId);
        if (folder != null && Archives.isArchive(folder.path())) {
            // An archive folder has no directory to walk; rescan the folder which owns the file.
            Path parent = folder.path().getParent();
            Folder owner = parent == null ? null : folderFor(parent).orElse(null);
            requestFolderScan(owner == null ? folderId : owner.id());
            return;
        }
        requestFolderScan(folderId);
    }

    /**
     * Forces one photo to be decoded again and updates its catalog row.
     *
     * @param photo a photo
     */
    public void reimport(Photo photo) {
        library.invalidate(photo.id());
        if (photo.archived()) {
            // A virtual entry has no file of its own: rescan the real folder holding its archive.
            Path archive = Path.of(photo.archivePath());
            Path parent = archive.getParent();
            Folder owner = parent == null ? null : folderFor(parent).orElse(null);
            requestFolderScan(owner == null ? photo.folderId() : owner.id());
            return;
        }
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
                    targets = folders.values().stream().filter(Folder::explicit).filter(Folder::scanEnabled).toList();
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
                    // Let the UI pick up this folder's photos before the next one starts.
                    notifyChanged(true);
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

    private Listing listDirectory(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Entry> children = new ArrayList<>();
            for (Path child : stream) {
                children.add(new Entry(child, Files.readAttributes(child, BasicFileAttributes.class)));
            }
            return new Listing(Files.readAttributes(dir, BasicFileAttributes.class), children);
        }
    }

    /**
     * Walks one directory with a timeout. An unresponsive directory is skipped; its siblings and
     * the rest of the tree are still scanned. A directory which merely takes longer than the first
     * budget - a large folder on a share - is waited on once more before it is given up on, because
     * skipping it silently loses every photo inside it.
     */
    private void walkTree(Path dir, SimpleFileVisitor<Path> visitor, AtomicBoolean complete) throws IOException {
        if (directoryFailures.get() >= MAX_CONSECUTIVE_DIRECTORY_FAILURES) {
            // The share stopped answering entirely; waiting through every remaining folder would
            // take hours, so stop this one and let the next scan try again.
            complete.set(false);
            lastError = "応答しないため中断しました: " + dir;
            return;
        }
        Listing listing;
        Future<Listing> first = null;
        try {
            first = walkers.submit(() -> listDirectory(dir));
            listing = first.get(DIRECTORY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            directoryFailures.set(0);
        } catch (TimeoutException e) {
            try {
                listing = first.get(DIRECTORY_RETRY_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException retry) {
                abandon(dir, visitor, complete, retry);
                return;
            }
        } catch (ExecutionException e) {
            abandon(dir, visitor, complete, e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        visitor.preVisitDirectory(dir, listing.attrs());
        for (Entry entry : listing.children()) {
            if (entry.attrs().isDirectory() && !entry.attrs().isSymbolicLink()) {
                walkTree(entry.path(), visitor, complete);
            } else {
                visitor.visitFile(entry.path(), entry.attrs());
            }
        }
        visitor.postVisitDirectory(dir, null);
    }

    /** Records one directory as unreadable and lets the walk carry on with its siblings. */
    private void abandon(Path dir, SimpleFileVisitor<Path> visitor, AtomicBoolean complete, Exception cause)
            throws IOException {
        complete.set(false);
        errors.incrementAndGet();
        directoryFailures.incrementAndGet();
        lastError = "読み込めません: " + dir;
        IOException failure = new IOException(lastError, cause);
        visitor.visitFileFailed(dir, failure);
        visitor.postVisitDirectory(dir, failure);
    }

    /**
     * Adds the words of a Picasa {@code .picasa.ini} description to a folder's tags. Tags assigned
     * by hand are kept, and a repeated scan never writes the catalog when nothing changed.
     */
    private void applyPicasaTags(Folder folder, Path dir) {
        List<String> found = PicasaTags.fromFolder(dir);
        if (found.isEmpty()) {
            return;
        }
        List<String> tags = new ArrayList<>(library.folderTags(folder.id()));
        boolean changed = false;
        for (String tag : found) {
            if (tags.stream().noneMatch(existing -> existing.equalsIgnoreCase(tag))) {
                tags.add(tag);
                changed = true;
            }
        }
        if (changed) {
            library.setFolderTags(folder.id(), tags);
        }
    }

    private void scanFolderInternal(Folder folder) {
        Path root = folder.path();
        String token = library.beginScan(folder.id());
        // Slow media is only unresponsive when nothing completes at all, so the silence is measured
        // from the start of this scan rather than from some earlier import.
        lastImportProgress.set(System.nanoTime());
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
        directoryFailures.set(0);
        importsBlocked = false;
        message = folder.name() + " をスキャン中";
        publishState(true);
        try {
            SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
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
                    applyPicasaTags(managed, dir);
                    if (watching) {
                        register(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Archives.isArchive(file)) {
                        if (ArchiveSettings.scanArchives()) {
                            total.incrementAndGet();
                            // Each archive gets its own catalog folder, so its entries are browsable
                            // as a group instead of being lumped into the containing directory.
                            Folder archiveFolder = library.ensureFolder(file);
                            remember(archiveFolder);
                            visited.add(archiveFolder.id());
                            scanArchive(archiveFolder.id(), file, token, attrs, walkFinished, stamps, unchanged);
                        }
                        return FileVisitResult.CONTINUE;
                    }
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
                            submitImport(ownerId, file, token, attrs, walkFinished);
                        }
                    } catch (RuntimeException | IOError | LinkageError | OutOfMemoryError e) {
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
            };
            Callable<Void> walk = () -> {
                if (!Files.isDirectory(root)) {
                    lastError = "フォルダが見つかりません: " + root;
                    errors.incrementAndGet();
                    walkFinished.set(false);
                    return null;
                }
                walkTree(root, visitor, walkFinished);
                return null;
            };
            try {
                walkers.submit(walk).get();
            } catch (ExecutionException e) {
                walkFinished.set(false);
                errors.incrementAndGet();
                lastError = "スキャンできません: " + root + " (" + e.getCause() + ")";
                drainAllSaves();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                walkFinished.set(false);
                drainAllSaves();
                return;
            }

            boolean importsDone = awaitImports();
            drainAllSaves();
            for (Map.Entry<Long, List<String>> entry : unchanged.entrySet()) {
                List<String> keys = entry.getValue();
                for (int from = 0; from < keys.size(); from += TOUCH_BATCH) {
                    library.touchBatch(keys.subList(from, Math.min(from + TOUCH_BATCH, keys.size())), token, entry.getKey());
                }
            }
            if (walkFinished.get() && importsDone && !closed.get()) {
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
        } catch (RuntimeException e) {
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
    private void submitImport(long folderId, Path file, String token, BasicFileAttributes attrs, AtomicBoolean complete) {
        submitWork(file, complete, () -> importFile(folderId, file, token, attrs));
    }

    /**
     * Indexes the image entries of one archive. The archive file itself is never treated as a photo.
     * Entries whose stamp still matches are touched, changed ones are queued for decoding, and
     * entries which disappeared are left untouched so the scan token prunes them.
     */
    private void scanArchive(long folderId, Path archive, String token, BasicFileAttributes attrs,
            AtomicBoolean complete, Map<String, FileStamp> stamps, Map<Long, List<String>> unchanged) {
        String archiveKey = PathUtil.key(archive);
        long modified = attrs.lastModifiedTime().toMillis();
        FileStamp previous = stamps.remove(archiveKey);
        if (previous != null && previous.size() == attrs.size() && previous.modifiedAt() == modified) {
            List<String> keys = library.archiveKeys(archiveKey);
            for (int from = 0; from < keys.size(); from += TOUCH_BATCH) {
                library.touchBatch(keys.subList(from, Math.min(from + TOUCH_BATCH, keys.size())), token, folderId);
            }
            return;
        }
        String type = Archives.type(archive);
        List<Archives.Entry> entries;
        try {
            entries = Archives.entries(archive);
        } catch (IOException | RuntimeException e) {
            errors.incrementAndGet();
            lastError = "読み込めません: " + archive.getFileName();
            complete.set(false);
            // A transient read error must not erase the rows from a previous, successful scan.
            for (String key : library.archiveKeys(archiveKey)) {
                library.touch(key, token, folderId);
            }
            return;
        }
        for (Archives.Entry entry : entries) {
            Path virtual = Archives.entryPath(archive, entry.name());
            String key = PathUtil.key(virtual);
            FileStamp stamp = stamps.remove(key);
            if (stamp != null && stamp.size() == entry.size() && stamp.modifiedAt() == modified) {
                unchanged.computeIfAbsent(folderId, ignored -> new ArrayList<>()).add(key);
            } else {
                submitArchiveImport(folderId, archive, virtual, type, entry.name(), entry.size(), modified, token, complete);
            }
        }
    }

    private void submitArchiveImport(long folderId, Path archive, Path virtual, String type, String entryName,
            long size, long modified, String token, AtomicBoolean complete) {
        submitWork(virtual, complete, () -> importArchiveEntry(folderId, archive, virtual, type, entryName, size, modified, token));
    }

    /** Decodes one archive entry into the same catalog row shape as a normal photo. */
    private void importArchiveEntry(long folderId, Path archive, Path virtual, String type, String entryName,
            long size, long modified, String token) {
        String key = PathUtil.key(virtual);
        if (!importing.add(key)) {
            return;
        }
        busy.incrementAndGet();
        try {
            String effectiveToken = token != null ? token : activeTokenFor(archive);
            PhotoImporter.AnalyzedPhoto analyzed;
            try {
                analyzed = PhotoImporter.analyze(Archives.read(archive, type, entryName), entryName, modified);
            } catch (IOException | RuntimeException | Error e) {
                errors.incrementAndGet();
                lastError = "読み込めません: " + entryName;
                analyzed = PhotoImporter.placeholder(virtual, modified);
            }
            recordSave(new Library.PhotoSave(folderId, virtual, size, modified, analyzed, effectiveToken,
                    PathUtil.key(archive), type, entryName), token);
            imported.incrementAndGet();
            notifyChanged(false);
        } catch (RuntimeException | IOError | LinkageError | OutOfMemoryError e) {
            errors.incrementAndGet();
            lastError = "読み込めません: " + entryName;
        } finally {
            importing.remove(key);
            processed.incrementAndGet();
            busy.decrementAndGet();
            publishState(false);
        }
    }

    /** Queues one bounded decode, shared by files and archive entries. */
    private void submitWork(Path file, AtomicBoolean complete, Runnable work) {
        if (closed.get() || importsBlocked) {
            return;
        }
        boolean acquired = false;
        // Waiting for a slot is not the same as the media being dead: on a share the decoders are
        // simply busy, and imports keep finishing. Only a run of silence gives the rest up.
        while (!closed.get() && !importsBlocked) {
            try {
                if (acquired = importPermits.tryAcquire(IMPORT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (stalled()) {
                importsBlocked = true;
            }
        }
        if (!acquired) {
            // Nothing is completing any more. Skip the rest of this scan rather than waiting per file.
            importsBlocked = true;
            complete.set(false);
            errors.incrementAndGet();
            lastError = "画像の読み込みが応答しないためスキップしました: " + file.getFileName();
            return;
        }
        synchronized (importMonitor) {
            activeImports++;
        }
        try {
            importers.submit(() -> {
                try {
                    work.run();
                } catch (RuntimeException | IOError | LinkageError | OutOfMemoryError e) {
                    // One entry must never take the worker down with it: the scan has to go on.
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

    /** Whether no import has completed for as long as even a loaded share should need. */
    private boolean stalled() {
        return System.nanoTime() - lastImportProgress.get() > TimeUnit.SECONDS.toNanos(IMPORT_STALL_SECONDS);
    }

    private void releaseImport() {
        importPermits.release();
        synchronized (importMonitor) {
            activeImports--;
            lastImportProgress.set(System.nanoTime());
            importMonitor.notifyAll();
        }
    }

    /** Waits for decodes, but gives up when nothing completes any more, not when one is merely slow. */
    private boolean awaitImports() {
        synchronized (importMonitor) {
            while (activeImports > 0 && !closed.get()) {
                if (stalled()) {
                    errors.incrementAndGet();
                    lastError = "読み込みが応答しませんでした";
                    return false;
                }
                try {
                    importMonitor.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private void recordSave(long folderId, Path file, long size, long modified,
            PhotoImporter.AnalyzedPhoto analyzed, String effectiveToken, String scanToken) {
        recordSave(new Library.PhotoSave(folderId, file, size, modified, analyzed, effectiveToken), scanToken);
    }

    private void recordSave(Library.PhotoSave save, String scanToken) {
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
            } catch (OutOfMemoryError e) {
                // The heap is already exhausted, so there is no room to build a placeholder. Keep
                // this file a skip, as before, instead of risking the rest of the scan.
                errors.incrementAndGet();
                lastError = "読み込めません: " + file.getFileName();
                notifyChanged(false);
                return;
            } catch (IOException | RuntimeException | IOError | LinkageError e) {
                // A damaged or unsupported file still becomes a catalog row with a placeholder
                // thumbnail, so the user can see it and delete it. The failure stays counted for
                // diagnostics, and the normal save path (token, change check) still applies.
                errors.incrementAndGet();
                lastError = "読み込めません: " + file.getFileName();
                analyzed = PhotoImporter.placeholder(file, modified);
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
        } catch (IOException | RuntimeException | IOError | LinkageError | OutOfMemoryError e) {
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
            if (Archives.isArchive(path)) {
                // The archive is a container: reindex it so added and removed entries follow the file.
                if (ArchiveSettings.scanArchives()) {
                    Path parent = path.getParent();
                    Folder parentFolder = parent == null ? folder : folderFor(parent).orElse(folder);
                    requestFolderScan(parentFolder.id());
                }
            } else if (PathUtil.isImage(path)) {
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
        // Interrupt the take() loop before closing the service: on some platforms close() waits
        // for an in-flight take() to return.
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
        scheduler.shutdownNow();
        coordinator.shutdownNow();
        importers.shutdownNow();
        walkers.shutdownNow();
        // A thread blocked in native filesystem I/O ignores interrupts. Every pool thread is a
        // daemon, so keep the waits short and let the JVM exit even if one is still stuck.
        try {
            coordinator.awaitTermination(1, TimeUnit.SECONDS);
            importers.awaitTermination(1, TimeUnit.SECONDS);
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
