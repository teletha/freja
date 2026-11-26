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

import java.awt.image.BufferedImage;
import java.io.IOError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import freja.model.Photo;
import freja.store.Library;
import imagify.Imagify;

/**
 * A bounded in-memory cache of decoded thumbnails with background loading.
 * <p>
 * The cache key includes the file modification time, so a photo which is edited on disk gets a
 * fresh entry instead of keeping the stale thumbnail.
 * </p>
 */
final class ThumbnailCache implements AutoCloseable {

    /** Enough cells to cover a large window at the largest zoom with room to scroll. */
    private static final int MAX_IMAGES = 600;

    private record Key(long photoId, long modifiedAt) {
    }

    private final Library library;

    private final ExecutorService loader = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "freja-thumbnail");
        thread.setDaemon(true);
        return thread;
    });

    private final ConcurrentHashMap<Key, BufferedImage> images = new ConcurrentHashMap<>();

    private final Set<Key> loading = ConcurrentHashMap.newKeySet();

    private final Set<Key> missing = ConcurrentHashMap.newKeySet();

    private final LinkedHashMap<Key, Boolean> order = new LinkedHashMap<>(256, 0.75f, true);

    ThumbnailCache(Library library) {
        this.library = library;
    }

    /**
     * Returns a cached thumbnail without blocking.
     *
     * @param photoId a photo identifier
     * @param modifiedAt the modification time of the source file
     * @return the image, or null while it is loading or absent
     */
    BufferedImage get(long photoId, long modifiedAt) {
        Key key = new Key(photoId, modifiedAt);
        BufferedImage image = images.get(key);
        if (image != null) {
            synchronized (order) {
                order.get(key);
            }
        }
        return image;
    }

    /**
     * Requests a thumbnail and calls {@code whenLoaded} on the event dispatch thread.
     *
     * @param photoId a photo identifier
     * @param modifiedAt the modification time of the source file
     * @param whenLoaded a callback, may be null
     */
    void request(long photoId, long modifiedAt, Runnable whenLoaded) {
        Key key = new Key(photoId, modifiedAt);
        if (images.containsKey(key) || missing.contains(key) || !loading.add(key)) {
            return;
        }
        loader.execute(() -> {
            try {
                library.thumbnail(photoId).ifPresent(thumbnail -> {
                    try {
                        BufferedImage image = Imagify.read(thumbnail.data()).toBufferedImage();
                        if (image != null) {
                            put(key, image);
                        } else {
                            missing.add(key);
                        }
                    } catch (RuntimeException | IOError | LinkageError e) {
                        missing.add(key);
                    }
                });
                if (!images.containsKey(key)) {
                    missing.add(key);
                }
            } catch (RuntimeException e) {
                missing.add(key);
            } finally {
                loading.remove(key);
                if (whenLoaded != null) {
                    SwingUtilities.invokeLater(whenLoaded);
                }
            }
        });
    }

    /**
     * Decodes the first thumbnails of a view before the grid paints it. This runs on the refresh
     * thread, so the first frame already has images instead of showing empty cells for a moment.
     * Only a bounded prefix is warmed: scrolling still loads the rest lazily.
     *
     * @param photos the photos in display order
     * @param limit the maximum number to warm
     */
    void warm(List<Photo> photos, int limit) {
        warm(photos, 0, limit);
    }

    /**
     * Same as {@link #warm(List, int)} but starts at an arbitrary row, e.g. the folder restored at
     * startup which may be far down the list.
     *
     * @param photos the photos in display order
     * @param from the first index to warm
     * @param limit the maximum number to warm
     */
    void warm(List<Photo> photos, int from, int limit) {
        int start = Math.max(0, Math.min(from, photos.size()));
        int end = Math.min(photos.size(), start + Math.max(0, limit));
        for (int i = start; i < end; i++) {
            Photo photo = photos.get(i);
            Key key = new Key(photo.id(), photo.modifiedAt());
            if (images.containsKey(key) || missing.contains(key) || !loading.add(key)) {
                continue;
            }
            try {
                library.thumbnail(photo.id()).ifPresent(thumbnail -> {
                    BufferedImage image = Imagify.read(thumbnail.data()).toBufferedImage();
                    if (image != null) {
                        put(key, image);
                    } else {
                        missing.add(key);
                    }
                });
                if (!images.containsKey(key)) {
                    missing.add(key);
                }
            } catch (RuntimeException | IOError | LinkageError e) {
                missing.add(key);
            } finally {
                loading.remove(key);
            }
        }
    }

    private void put(Key key, BufferedImage image) {
        images.put(key, image);
        synchronized (order) {
            order.put(key, Boolean.TRUE);
            while (order.size() > MAX_IMAGES) {
                Map.Entry<Key, Boolean> eldest = order.entrySet().iterator().next();
                order.remove(eldest.getKey());
                images.remove(eldest.getKey());
            }
        }
    }

    /**
     * Allows thumbnails which were previously absent to be requested again.
     */
    void retryMissing() {
        missing.clear();
    }

    /**
     * Drops every cached image, for example after a re-import.
     */
    void clear() {
        images.clear();
        missing.clear();
        synchronized (order) {
            order.clear();
        }
    }

    @Override
    public void close() {
        loader.shutdownNow();
        images.clear();
        synchronized (order) {
            order.clear();
        }
    }
}
