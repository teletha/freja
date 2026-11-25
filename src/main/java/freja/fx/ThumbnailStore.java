/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javafx.scene.image.Image;
import kiss.Variable;
import freja.model.Photo;
import freja.store.Library;
import viewtify.Viewtify;

/**
 * A bounded in-memory cache of JavaFX thumbnails, the counterpart of the Swing edition's
 * {@code ThumbnailCache}. Decoding happens on Viewtify's worker pool and the cached
 * {@link Variable} delivers the image to every cell which asked for it, on the UI thread.
 */
final class ThumbnailStore implements AutoCloseable {

    /** Enough cells to cover a large window at the largest zoom with room to scroll. */
    private static final int MAX_IMAGES = 600;

    private record Key(long photoId, long modifiedAt) {
    }

    private final Library library;

    private final Map<Key, Image> images = new ConcurrentHashMap<>();

    private final Map<Key, Variable<Image>> slots = new ConcurrentHashMap<>();

    private final Set<Key> missing = ConcurrentHashMap.newKeySet();

    private final Set<Key> loading = ConcurrentHashMap.newKeySet();

    private final LinkedHashMap<Key, Boolean> order = new LinkedHashMap<>(256, 0.75f, true);

    ThumbnailStore(Library library) {
        this.library = library;
    }

    /**
     * Returns the live image holder for a photo. The variable starts empty when the thumbnail is
     * not decoded yet and fills once the background load finishes.
     *
     * @param photo the photo
     * @return the observable image slot
     */
    Variable<Image> imageFor(Photo photo) {
        Key key = new Key(photo.id(), photo.modifiedAt());
        return slots.computeIfAbsent(key, this::request);
    }

    /** @return the cached image without triggering a load, or null */
    Image peek(Photo photo) {
        return images.get(new Key(photo.id(), photo.modifiedAt()));
    }

    private Variable<Image> request(Key key) {
        Variable<Image> slot = Variable.empty();
        Image cached = images.get(key);
        if (cached != null) {
            slot.set(cached);
            return slot;
        }
        if (missing.contains(key) || !loading.add(key)) {
            return slot;
        }
        Viewtify.inWorker(() -> {
            try {
                Library.Thumbnail thumbnail = library.thumbnail(key.photoId()).orElse(null);
                if (thumbnail == null) {
                    missing.add(key);
                    return;
                }
                Image image = FxImages.from(thumbnail);
                images.put(key, image);
                synchronized (order) {
                    order.put(key, Boolean.TRUE);
                    while (order.size() > MAX_IMAGES) {
                        Map.Entry<Key, Boolean> eldest = order.entrySet().iterator().next();
                        order.remove(eldest.getKey());
                        images.remove(eldest.getKey());
                        slots.remove(eldest.getKey());
                    }
                }
                Viewtify.inUI(() -> slot.set(image));
            } catch (Exception | LinkageError e) {
                missing.add(key);
            } finally {
                loading.remove(key);
            }
        });
        return slot;
    }

    /** Allows thumbnails which were previously absent to be requested again, e.g. after a scan. */
    void retryMissing() {
        missing.clear();
        // Forget the empty slots so the rebuilt cells request a fresh load.
        slots.entrySet().removeIf(entry -> entry.getValue().isAbsent());
    }

    /** Drops one photo's cached image, for example after a re-import. */
    void clear(Photo photo) {
        Key key = new Key(photo.id(), photo.modifiedAt());
        images.remove(key);
        slots.remove(key);
        missing.remove(key);
        synchronized (order) {
            order.remove(key);
        }
    }

    /** Drops every cached image. */
    void clearAll() {
        images.clear();
        missing.clear();
        slots.clear();
        synchronized (order) {
            order.clear();
        }
    }

    @Override
    public void close() {
        clearAll();
    }
}
