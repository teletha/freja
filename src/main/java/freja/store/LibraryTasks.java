/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.store;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import freja.model.Photo;
import freja.model.PhotoFilter.SortOrder;

/**
 * Runs catalog work on a dedicated background thread and returns results through a UI dispatcher.
 * <p>
 * All UI-originated library access goes through this class. The single task thread serialises UI
 * writes with each other; the {@link Library} lock still serialises them with the scanner. Results
 * are always handed back through the dispatcher supplied by the caller (for example Swing's
 * {@code SwingUtilities::invokeLater}), never on the task thread itself.
 * </p>
 */
public final class LibraryTasks implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LibraryTasks.class.getName());

    /** Outcome of a "delete from disk" request: successfully removed files and failures. */
    public record DeleteResult(int deleted, int failed) {
    }

    private final Library library;

    private final Consumer<Runnable> ui;

    private final ExecutorService executor;

    private Consumer<Throwable> errorHandler;

    public LibraryTasks(Library library, Consumer<Runnable> uiDispatch) {
        this(library, uiDispatch, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "freja-library-task");
            thread.setDaemon(true);
            return thread;
        }));
    }

    LibraryTasks(Library library, Consumer<Runnable> uiDispatch, ExecutorService executor) {
        this.library = library;
        this.ui = uiDispatch;
        this.executor = executor;
    }

    /** Installs an optional handler which is notified on the UI thread when a task fails. */
    public void setErrorHandler(Consumer<Throwable> handler) {
        errorHandler = handler;
    }

    private <T> void submit(Callable<T> work, Consumer<T> onResult) {
        executor.execute(() -> {
            T value = null;
            Throwable failure = null;
            try {
                value = work.call();
            } catch (Exception e) {
                failure = e;
                LOG.log(Level.SEVERE, "Catalog task failed", e);
            } finally {
                Consumer<Throwable> handler = failure == null ? null : errorHandler;
                if (handler != null) {
                    Throwable reported = failure;
                    ui.accept(() -> handler.accept(reported));
                }
                if (onResult != null) {
                    T result = value;
                    ui.accept(() -> onResult.accept(result));
                }
            }
        });
    }

    /** Runs a write off the UI thread, optionally invoking {@code onDone} back on it. */
    public void execute(Runnable work, Runnable onDone) {
        submit(() -> {
            work.run();
            return null;
        }, result -> {
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    public void execute(Runnable work) {
        execute(work, null);
    }

    /** Runs a read off the UI thread and delivers its result through the UI dispatcher. */
    public <T> void query(Callable<T> work, Consumer<T> onResult) {
        submit(work, onResult);
    }

    /** Deletes catalog rows in one transaction; the files on disk are left alone. */
    public void deletePhotos(List<Long> ids, Runnable onDone) {
        execute(() -> library.deletePhotos(ids), onDone);
    }

    /** Deletes the files first, then their catalog rows in one transaction. */
    public void deleteFromDisk(List<Photo> photos, Consumer<DeleteResult> onDone) {
        submit(() -> {
            List<Long> ids = new ArrayList<>();
            int failed = 0;
            for (Photo photo : photos) {
                try {
                    if (photo.archived()) {
                        // Archive entries are virtual; deleting the container would remove other photos.
                        continue;
                    }
                    if (Files.deleteIfExists(photo.path())) {
                        ids.add(photo.id());
                    } else {
                        failed++;
                    }
                } catch (IOException | RuntimeException e) {
                    failed++;
                }
            }
            library.deletePhotos(ids);
            return new DeleteResult(ids.size(), failed);
        }, onDone);
    }

    public void setFavorites(List<Long> ids, boolean favorite, Runnable onDone) {
        execute(() -> library.setFavorites(ids, favorite), onDone);
    }

    public void setRating(long photoId, int rating, Runnable onDone) {
        execute(() -> library.setRating(photoId, rating), onDone);
    }

    public void setRatings(Map<Long, Integer> ratings, Runnable onDone) {
        execute(() -> library.setRatings(ratings), onDone);
    }

    public void setCaption(long photoId, String caption, Runnable onDone) {
        execute(() -> library.setCaption(photoId, caption), onDone);
    }

    public void setCaptions(Map<Long, String> captions, Runnable onDone) {
        execute(() -> library.setCaptions(captions), onDone);
    }

    /** Removes folder trees (including explicit roots and descendants) in one transaction. */
    public void removeFolders(List<Long> ids, Runnable onDone) {
        execute(() -> library.removeFolders(ids), onDone);
    }

    public void deleteFolders(List<Long> ids, Runnable onDone) {
        execute(() -> library.deleteFolders(ids), onDone);
    }

    public void setFolderScanEnabled(long folderId, boolean enabled, Runnable onDone) {
        execute(() -> library.setFolderScanEnabled(folderId, enabled), onDone);
    }

    public void setFolderScanEnabled(List<Long> ids, boolean enabled, Runnable onDone) {
        execute(() -> library.setFolderScanEnabled(ids, enabled), onDone);
    }

    public void setFolderSort(long folderId, SortOrder sort, Runnable onDone) {
        execute(() -> library.setFolderSort(folderId, sort), onDone);
    }

    public void renameFolder(long folderId, String name, Runnable onDone) {
        execute(() -> library.renameFolder(folderId, name), onDone);
    }

    public void clearFolders(Runnable onDone) {
        execute(library::clearFolders, onDone);
    }

    public Library library() {
        return library;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
