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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the folder-level description Picasa keeps in its hidden {@code .picasa.ini} file. Each
 * whitespace-separated word of that description becomes a tag, so an old Picasa album's keywords
 * survive a move to Freja.
 */
public final class PicasaTags {

    /** The hidden file Picasa writes into every album directory. */
    private static final String FILE_NAME = ".picasa.ini";

    private PicasaTags() {
    }

    /**
     * Reads the tags from a directory's Picasa description.
     *
     * @param folder the directory to inspect
     * @return normalised tags, or an empty list when there is no usable description
     */
    public static List<String> fromFolder(Path folder) {
        if (folder == null) {
            return List.of();
        }
        Path file = folder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> lines = read(file);
        String description = description(lines);
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String word : description.strip().split("\\s+")) {
            String tag = SmartTags.normalize(word);
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            // Picasa on Windows can write the file in the system code page instead of UTF-8.
            try {
                return Files.readAllLines(file, Charset.forName("Windows-31J"));
            } catch (IOException ignored) {
                return List.of();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Finds the description line. A {@code [Picasa]} section holds the album-wide value; a plain
     * file without sections is accepted as well. Per-photo descriptions are only a fallback.
     */
    private static String description(List<String> lines) {
        String album = null;
        String first = null;
        String section = "";
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).strip();
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0 || !line.substring(0, equals).strip().equalsIgnoreCase("description")) {
                continue;
            }
            String value = line.substring(equals + 1).strip();
            if (value.isEmpty()) {
                continue;
            }
            if (section.equalsIgnoreCase("Picasa") && album == null) {
                album = value;
            }
            if (first == null) {
                first = value;
            }
        }
        return album != null ? album : first;
    }
}
