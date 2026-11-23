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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Derives tags from folder names. A folder whose name starts with one or more bracketed strings is
 * tagged with them, e.g. {@code 【旅行】(２０２４) 写真} yields {@code 旅行} and {@code 2024}.
 */
public final class SmartTags {

    /** Half/full-width parentheses and the two Japanese bracket styles. */
    private static final String OPEN = "(（「【";

    private static final String CLOSE = ")）」】";

    private SmartTags() {
    }

    /**
     * Reads the bracketed groups at the beginning of a folder name.
     *
     * @param name a folder name
     * @return normalised tags, in the order they appear
     */
    public static List<String> fromFolderName(String name) {
        if (name == null) {
            return List.of();
        }
        int index = 0;
        while (index < name.length() && Character.isWhitespace(name.charAt(index))) {
            index++;
        }
        List<String> tags = new ArrayList<>();
        while (index < name.length() && OPEN.indexOf(name.charAt(index)) >= 0) {
            int start = ++index;
            while (index < name.length() && CLOSE.indexOf(name.charAt(index)) < 0) {
                index++;
            }
            if (index >= name.length()) {
                break;
            }
            String tag = normalize(name.substring(start, index));
            index++;
            if (!tag.isEmpty() && !tags.contains(tag)) {
                tags.add(tag);
            }
            while (index < name.length() && Character.isWhitespace(name.charAt(index))) {
                index++;
            }
        }
        return List.copyOf(tags);
    }

    /**
     * Normalises a tag: full-width letters and digits become half width, while Japanese kana stay
     * full width, and surrounding whitespace is removed.
     *
     * @param value the raw tag name
     * @return the normalised tag name, or an empty string when there is nothing usable
     */
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFKC);
    }
}
