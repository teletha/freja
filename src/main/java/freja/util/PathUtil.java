/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.util;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Path normalization helpers shared by the database and the scanner.
 * <p>
 * SQLite compares text byte by byte, while Windows compares paths without regard to case. Every
 * path that is stored therefore goes through {@link #key(Path)}, and every lookup uses the same
 * normalized form.
 * </p>
 */
public final class PathUtil {

    /** File extensions this application is able to decode. */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp");

    /** The folder separator as it appears in normalized path keys. */
    public static final String SEPARATOR = File.separator;

    private PathUtil() {
    }

    /**
     * Returns a stable, comparable key for the given path.
     *
     * @param path a file system path
     * @return an absolute, normalized path key which is lower case on Windows
     */
    public static String key(Path path) {
        String key = path.toAbsolutePath().normalize().toString();
        return File.separatorChar == '\\' ? key.toLowerCase(Locale.ROOT) : key;
    }

    /**
     * Builds a key for a file below a root whose key is already known. This avoids re-resolving and
     * re-normalizing every absolute path in a large directory walk.
     *
     * @param rootKey the normalized key of the containing root
     * @param root the containing root, as handed to the walk
     * @param file a file below the root
     * @return the same key {@link #key(Path)} would return for the file
     */
    public static String key(String rootKey, Path root, Path file) {
        String relative = root.relativize(file).toString();
        String key = rootKey.endsWith(SEPARATOR) ? rootKey + relative : rootKey + SEPARATOR + relative;
        return File.separatorChar == '\\' ? key.toLowerCase(Locale.ROOT) : key;
    }

    /**
     * Normalizes the path for storage and display.
     *
     * @param path a file system path
     * @return an absolute normalized path
     */
    public static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Returns the lower-cased extension of a file name, without the leading dot.
     *
     * @param path a file
     * @return the extension or an empty string
     */
    public static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Tests whether the file looks like an image this application supports.
     *
     * @param path a file
     * @return true when the extension is a supported image extension
     */
    public static boolean isImage(Path path) {
        return IMAGE_EXTENSIONS.contains(extension(path));
    }

    /**
     * Tests whether one path contains another.
     *
     * @param parent a path
     * @param child another path
     * @return true when {@code child} is at or below {@code parent}
     */
    public static boolean contains(Path parent, Path child) {
        String parentKey = key(parent);
        String childKey = key(child);
        return childKey.equals(parentKey) || childKey.startsWith(prefix(parentKey));
    }

    /**
     * Returns the path key of a directory as a prefix suitable for containment checks.
     *
     * @param key a normalized path key
     * @return the key followed by a separator unless it already ends with one
     */
    public static String prefix(String key) {
        return key.endsWith(SEPARATOR) ? key : key + SEPARATOR;
    }

    /**
     * Escapes the wildcards used by SQL {@code LIKE}.
     *
     * @param value a literal value
     * @return the value escaped for use with {@code ESCAPE '\'}
     */
    public static String likeValue(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Returns a human readable name for a folder path.
     *
     * @param path a folder
     * @return the last element of the path, or the path itself for a drive root
     */
    public static String folderName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }
}
