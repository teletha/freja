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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import freja.media.store.Archives;
import freja.util.PathUtil;
import psychopath.Folder;
import psychopath.Locator;
import psychopath.Option;
import psychopath.Progress;

/** Thin adapter from the file manager UI to psychopath's copy/move/delete/rename operations. */
public final class FileOperations {

    public interface Listener {
        void progress(Progress progress);

        void done(Exception error);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-file-operations");
        thread.setDaemon(true);
        return thread;
    });

    public void copy(List<Path> sources, Path target, boolean replace, Listener listener) {
        run(listener, () -> sources(sources).trackCopyingTo(Locator.directory(target), conflict(replace)));
    }

    public void move(List<Path> sources, Path target, boolean replace, Listener listener) {
        run(listener, () -> sources(sources).trackMovingTo(Locator.directory(target), conflict(replace)));
    }

    public void delete(List<Path> sources, Listener listener) {
        run(listener, () -> sources(sources).trackDeleting(option -> option));
    }

    public void rename(Path source, String name, boolean replace, Listener listener) {
        worker.execute(() -> {
            Exception error = null;
            try {
                if (replace) {
                    Locator.locate(source).renameTo(name, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Locator.locate(source).renameTo(name);
                }
            } catch (Exception e) {
                error = e;
            }
            Exception failure = error;
            SwingUtilities.invokeLater(() -> listener.done(failure));
        });
    }

    /** Writes the selected paths into a ZIP archive. */
    public void pack(List<Path> sources, Path archive, Listener listener) {
        run(listener, () -> {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sources(sources).trackPackingTo(Locator.file(archive), option -> option);
        });
    }

    /** Extracts an archive into a directory. 7z and other containers use the catalog's reader. */
    public void extract(Path archive, Path target, Listener listener) {
        worker.execute(() -> {
            Exception error = null;
            try {
                if ("7z".equals(PathUtil.extension(archive))) {
                    SwingUtilities.invokeLater(() -> listener.progress(null));
                    extractLegacy(archive, target);
                } else {
                    Locator.file(archive).trackUnpackingTo(Locator.directory(target), option -> option)
                            .to(progress -> SwingUtilities.invokeLater(() -> listener.progress(progress)),
                                    failure -> error(failure, listener), () -> {
                                    });
                }
            } catch (Exception e) {
                error = e;
            }
            Exception failure = error;
            SwingUtilities.invokeLater(() -> listener.done(failure));
        });
    }

    private void error(Throwable failure, Listener listener) {
        Exception error = failure instanceof Exception exception ? exception : new RuntimeException(failure);
        SwingUtilities.invokeLater(() -> {
            listener.progress(null);
            listener.done(error);
        });
    }

    private static void extractLegacy(Path archive, Path target) throws IOException {
        String type = Archives.type(archive);
        for (Archives.Entry entry : Archives.entries(archive)) {
            if (entry.name().endsWith("/")) {
                continue;
            }
            Path output = target.resolve(entry.name().replace('\\', '/')).normalize();
            if (!output.startsWith(target)) {
                throw new IOException("Unsafe archive entry: " + entry.name());
            }
            Files.createDirectories(output.getParent());
            Files.write(output, Archives.read(archive, entry.name(), type));
        }
    }

    private Folder sources(List<Path> paths) {
        Folder folder = Locator.folder();
        for (Path path : paths) {
            folder.add(Locator.locate(path));
        }
        return folder;
    }

    private static Function<Option, Option> conflict(boolean replace) {
        return option -> replace ? option.replaceExisting() : option.skipExisting();
    }

    private void run(Listener listener, java.util.function.Supplier<kiss.Signal<Progress>> operation) {
        worker.execute(() -> {
            Exception[] failure = new Exception[1];
            try {
                operation.get().to(progress -> SwingUtilities.invokeLater(() -> listener.progress(progress)),
                        error -> failure[0] = error instanceof Exception exception ? exception : new RuntimeException(error),
                        () -> {
                        });
            } catch (Exception e) {
                failure[0] = e;
            }
            Exception error = failure[0];
            SwingUtilities.invokeLater(() -> listener.done(error));
        });
    }
}
