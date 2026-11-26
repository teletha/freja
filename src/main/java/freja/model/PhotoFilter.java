/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.model;

import java.util.List;

/**
 * A query against the photo library.
 */
public record PhotoFilter(Long folderId, boolean includeSubfolders, boolean favoritesOnly, boolean recentOnly,
        String search, SortOrder sort, List<Long> tagIds) {

    /** How a result set is ordered. */
    public enum SortOrder {
        NAME("名前順"),
        DATE_NEWEST("新しい順"),
        DATE_OLDEST("古い順"),
        SIZE("サイズ順");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public PhotoFilter {
        search = search == null || search.isBlank() ? null : search.trim();
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }

    /** A filter showing every photo. */
    public static PhotoFilter all() {
        return new PhotoFilter(null, false, false, false, null, SortOrder.NAME, List.of());
    }

    /** A filter showing every photo in the given folder. */
    public static PhotoFilter folder(long folderId) {
        return new PhotoFilter(folderId, false, false, false, null, SortOrder.NAME, List.of());
    }

    /** A filter showing every photo in the given folder and its subfolders. */
    public static PhotoFilter folderTree(long folderId) {
        return new PhotoFilter(folderId, true, false, false, null, SortOrder.NAME, List.of());
    }

    /** A filter showing favorites. */
    public static PhotoFilter favorites() {
        return new PhotoFilter(null, false, true, false, null, SortOrder.NAME, List.of());
    }

    /** A filter showing recently imported photos. */
    public static PhotoFilter recent() {
        return new PhotoFilter(null, false, false, true, null, SortOrder.NAME, List.of());
    }

    public PhotoFilter withSort(SortOrder sort) {
        return new PhotoFilter(folderId, includeSubfolders, favoritesOnly, recentOnly, search, sort, tagIds);
    }

    public PhotoFilter withSearch(String search) {
        return new PhotoFilter(folderId, includeSubfolders, favoritesOnly, recentOnly, search, sort, tagIds);
    }

    /**
     * Restricts the photos to folders carrying any of the given tags.
     *
     * @param tagIds the tag identifiers, or an empty list for no tag restriction
     * @return a filter with those tags
     */
    public PhotoFilter withTags(List<Long> tagIds) {
        return new PhotoFilter(folderId, includeSubfolders, favoritesOnly, recentOnly, search, sort, tagIds);
    }

    /**
     * Tests whether this filter groups by capture date.
     *
     * @return true for the two date orders
     */
    public boolean groupsByDate() {
        return sort == SortOrder.DATE_NEWEST || sort == SortOrder.DATE_OLDEST;
    }
}
