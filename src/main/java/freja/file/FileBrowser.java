/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

import freja.media.store.Archives;
import freja.setting.FileSettings;
import psychopath.Location;
import psychopath.Locator;

/** Lists a directory lazily with psychopath and delivers the result on the EDT. */
public final class FileBrowser {

    /** One row of the file list. Home entries carry a section and drive capacities. */
    public record Entry(Path path, String name, boolean directory, boolean archive, long size, long modified,
            String extension, String section, long free, long total, boolean hidden) {

        public Entry(Path path, String name, boolean directory, boolean archive, long size, long modified, String extension) {
            this(path, name, directory, archive, size, modified, extension, "", 0, 0, false);
        }

        public Entry(Path path, String name, boolean directory, boolean archive, long size, long modified,
                String extension, String section, long free, long total) {
            this(path, name, directory, archive, size, modified, extension, section, free, total, false);
        }
    }

    private static final Pattern NETWORK_LINE = Pattern.compile("^\\s*\\S+\\s+([A-Za-z]:)\\s+(\\\\\\\\[^\\s]+)");

    public interface Callback {
        void done(List<Entry> entries, Exception error);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-file-browser");
        thread.setDaemon(true);
        return thread;
    });

    /** Network shares need many metadata calls, so read them in parallel. */
    private static final ExecutorService ATTRIBUTES = Executors.newFixedThreadPool(
            Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 4)), runnable -> {
        Thread thread = new Thread(runnable, "freja-file-attributes");
        thread.setDaemon(true);
        return thread;
    });

    public void list(Path directory, Callback callback) {
        worker.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            Exception failure = null;
            try {
                if (Files.isDirectory(directory)) {
                    // Plain NIO is much faster than psychopath on network shares. Psychopath stays
                    // in use for archives, where its virtual directories are needed.
                    List<Path> paths = new ArrayList<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                        for (Path child : stream) {
                            paths.add(child);
                        }
                    }
                    entries.addAll(toEntries(paths));
                } else if (Files.isRegularFile(directory) && Archives.isArchive(directory)) {
                    try {
                        // ZIP is handled by psychopath, including its virtual directories.
                        for (Location location : Locator.file(directory).asArchiveDirectory().children().toList()) {
                            entries.add(toEntry(location));
                        }
                    } catch (RuntimeException e) {
                        // 7z and other containers fall back to the catalog's archive reader.
                        entries.addAll(legacyEntries(directory));
                    }
                }
                if (FileSettings.hideHidden()) {
                    entries.removeIf(FileBrowser::hidden);
                }
                entries.sort(Comparator.comparing(Entry::directory).reversed()
                        .thenComparing(entry -> entry.name().toLowerCase(java.util.Locale.ROOT)));
            } catch (Exception e) {
                failure = e;
            }
            List<Entry> result = entries;
            Exception error = failure;
            SwingUtilities.invokeLater(() -> callback.done(result, error));
        });
    }

    /** Lists drives and mapped network locations for the virtual home, off the EDT. */
    public void listHome(Callback callback) {
        worker.execute(() -> {
            List<Entry> result = new ArrayList<>();
            Exception failure = null;
            try {
                List<Entry> drives = new ArrayList<>();
                FileSystemView view = FileSystemView.getFileSystemView();
                for (Path root : FileSystems.getDefault().getRootDirectories()) {
                    String name = view.getSystemDisplayName(root.toFile());
                    if (name == null || name.isBlank()) {
                        name = root.toString();
                    }
                    long total = 0;
                    long free = 0;
                    try {
                        FileStore store = Files.getFileStore(root);
                        total = store.getTotalSpace();
                        free = store.getUsableSpace();
                    } catch (IOException ignored) {
                        // Some roots do not expose capacity; the tile still navigates.
                    }
                    drives.add(new Entry(root, name, true, false, total, 0, "", "ドライブ", free, total));
                }
                drives.sort(Comparator.comparing(entry -> entry.path().toString(), String.CASE_INSENSITIVE_ORDER));
                result.addAll(drives);
                List<Entry> network = networkLocations();
                network.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
                result.addAll(network);
            } catch (Exception e) {
                failure = e;
            }
            List<Entry> found = result;
            Exception error = failure;
            SwingUtilities.invokeLater(() -> callback.done(found, error));
        });
    }

    /** Mapped network drives on Windows; other systems have no equivalent shell location. */
    private static List<Entry> networkLocations() {
        List<Entry> result = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return result;
        }
        Process process = null;
        try {
            process = new ProcessBuilder("net", "use").redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                for (String line; (line = reader.readLine()) != null;) {
                    Matcher matcher = NETWORK_LINE.matcher(line);
                    if (matcher.find()) {
                        Path path = Path.of(matcher.group(1) + "\\");
                        result.add(new Entry(path, matcher.group(2).trim(), true, false, 0, 0, "", "ネットワーク", 0, 0));
                    }
                }
            }
        } catch (Exception ignored) {
            // No mapped drives, or net.exe is unavailable: the section simply stays empty.
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return result;
    }

    /** Metadata reads are I/O bound; run them together so a network share does not serialize them. */
    private static List<Entry> toEntries(List<Path> paths) {
        List<CompletableFuture<Entry>> futures = paths.stream()
                .map(path -> CompletableFuture.supplyAsync(() -> toEntry(path), ATTRIBUTES))
                .toList();
        List<Entry> entries = new ArrayList<>(futures.size());
        for (CompletableFuture<Entry> future : futures) {
            entries.add(future.join());
        }
        return entries;
    }

    private static boolean hidden(Entry entry) {
        return entry.hidden();
    }

    /** Flat entry list for archives psychopath cannot open, read through the existing catalog API. */
    private static List<Entry> legacyEntries(Path archive) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (Archives.Entry entry : Archives.entries(archive)) {
            String name = entry.name();
            while (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            if (name.isEmpty()) {
                continue;
            }
            Path path = Archives.entryPath(archive, entry.name());
            String pathName = path.getFileName() == null ? name : path.getFileName().toString();
            boolean directory = entry.name().endsWith("/");
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
            entries.add(new Entry(path, pathName, directory, false, entry.size(), entry.modifiedAt(), extension));
        }
        return entries;
    }

    /** Entry from a real path, avoiding psychopath's attribute probes on network shares. */
    private static Entry toEntry(Path path) {
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        boolean directory = false;
        boolean hidden = false;
        long size = 0;
        long modified = 0;
        try {
            try {
                DosFileAttributes attributes = Files.readAttributes(path, DosFileAttributes.class);
                directory = attributes.isDirectory();
                hidden = attributes.isHidden();
                size = attributes.size();
                modified = attributes.lastModifiedTime().toMillis();
            } catch (UnsupportedOperationException unsupported) {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                directory = attributes.isDirectory();
                hidden = name.startsWith(".");
                size = attributes.size();
                modified = attributes.lastModifiedTime().toMillis();
            }
        } catch (IOException ignored) {
            // A file which vanished mid-listing still gets a row.
        }
        return new Entry(path, name, directory, freja.util.PathUtil.isArchive(path), size, modified,
                freja.util.PathUtil.extension(path), "", 0, 0, hidden);
    }

    private Entry toEntry(Location location) {
        Path path = location.asJavaPath();
        String name = location.name();
        String extension = location.extension();
        boolean archive = freja.util.PathUtil.isArchive(path);
        long size = 0;
        long modified = 0;
        try {
            size = location.size();
            modified = location.lastModifiedMilli();
        } catch (RuntimeException ignored) {
            // Virtual archive entries may not expose attributes; the row stays readable.
        }
        return new Entry(path, name, location.isDirectory(), archive, size, modified, extension);
    }
}
