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
 *
 * <p>Nested brackets are split into separate tags, so a doujin-style name such as
 * {@code (C71) [サークル (作者)] 作品} yields {@code C71}, {@code サークル} and {@code 作者}.
 */
public final class SmartTags {

    /** Half/full-width parentheses, square brackets and the two Japanese bracket styles. */
    private static final String OPEN = "(（[［「【";

    private static final String CLOSE = ")）]］」】";

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
        int index = skipWhitespace(name, 0);
        List<String> tags = new ArrayList<>();
        while (index < name.length() && isOpen(name.charAt(index))) {
            int close = findMatchingClose(name, index);
            if (close < 0) {
                break;
            }
            collectTags(name.substring(index + 1, close), tags);
            index = skipWhitespace(name, close + 1);
        }
        return List.copyOf(tags);
    }

    /**
     * Splits the content of a bracket group into tags. Plain text before a nested bracket becomes a
     * tag of its own, and the content of each nested bracket is collected recursively.
     */
    private static void collectTags(String content, List<String> tags) {
        int segmentStart = 0;
        int index = 0;
        while (index < content.length()) {
            if (isOpen(content.charAt(index))) {
                int close = findMatchingClose(content, index);
                if (close >= 0) {
                    addTag(content.substring(segmentStart, index), tags);
                    collectTags(content.substring(index + 1, close), tags);
                    index = close + 1;
                    segmentStart = index;
                    continue;
                }
            }
            index++;
        }
        addTag(content.substring(segmentStart), tags);
    }

    private static void addTag(String raw, List<String> tags) {
        String tag = normalize(raw);
        if (!tag.isEmpty() && !tags.contains(tag)) {
            tags.add(tag);
        }
    }

    /** Finds the close matching the bracket at {@code openIndex}, accounting for nested brackets. */
    private static int findMatchingClose(String value, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isOpen(c)) {
                depth++;
            } else if (isClose(c)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isOpen(char c) {
        return OPEN.indexOf(c) >= 0;
    }

    private static boolean isClose(char c) {
        return CLOSE.indexOf(c) >= 0;
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
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
