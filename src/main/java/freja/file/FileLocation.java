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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Where a file tab is looking: a real folder or archive, or the virtual home which gathers drives
 * and network locations. Keeping it separate from {@link Path} lets the home take part in the same
 * history and persistence as ordinary folders.
 */
public sealed interface FileLocation {

    /** An ordinary directory, archive or archive entry. */
    record Folder(Path path) implements FileLocation {

        public Folder {
            path = path.toAbsolutePath().normalize();
        }

        @Override
        public String key() {
            return "folder:" + path;
        }
    }

    /** The Windows-style "HOME" view, with drives and network locations. */
    enum Home implements FileLocation {
        INSTANCE;

        @Override
        public String key() {
            return "home";
        }
    }

    /** Persisted form of this location. */
    String key();

    /** The real path, or {@code null} for the virtual home. */
    default Path path() {
        return this instanceof Folder folder ? folder.path() : null;
    }

    static FileLocation folder(Path path) {
        return new Folder(path);
    }

    /** Reads a saved location, accepting plain paths written by earlier versions. */
    static FileLocation from(String key) {
        if (key == null || key.isBlank() || Home.INSTANCE.key().equals(key)) {
            return Home.INSTANCE;
        }
        String value = key.startsWith("folder:") ? key.substring("folder:".length()) : key;
        try {
            return new Folder(Path.of(value));
        } catch (InvalidPathException ignored) {
            return Home.INSTANCE;
        }
    }
}
