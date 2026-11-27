/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import freja.store.Archives;
import psychopath.Location;
import psychopath.Locator;

/** Lists a directory lazily with psychopath and delivers the result on the EDT. */
final class FileBrowser {

    /** One row of the file list. */
    record Entry(Path path, String name, boolean directory, boolean archive, long size, long modified, String extension) {
    }

    interface Callback {
        void done(List<Entry> entries, Exception error);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-file-browser");
        thread.setDaemon(true);
        return thread;
    });

    void list(Path directory, Callback callback) {
        worker.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            Exception failure = null;
            try {
                if (Files.isDirectory(directory)) {
                    for (Location location : Locator.directory(directory).children().toList()) {
                        entries.add(toEntry(location));
                    }
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
