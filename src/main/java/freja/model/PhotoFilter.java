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

/**
 * A query against the photo library.
 */
public record PhotoFilter(Long folderId, boolean favoritesOnly, boolean recentOnly, String search, SortOrder sort) {

    /** How a result set is ordered. */
    public enum SortOrder {
        NAME("名前順"),
        DATE_NEWEST("日時順 (新しい順)"),
        DATE_OLDEST("日時順 (古い順)"),
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
    }

    /** A filter showing every photo. */
    public static PhotoFilter all() {
        return new PhotoFilter(null, false, false, null, SortOrder.NAME);
    }

    /** A filter showing every photo in the given folder. */
    public static PhotoFilter folder(long folderId) {
        return new PhotoFilter(folderId, false, false, null, SortOrder.NAME);
    }

    /** A filter showing favorites. */
    public static PhotoFilter favorites() {
        return new PhotoFilter(null, true, false, null, SortOrder.NAME);
    }

    /** A filter showing recently imported photos. */
    public static PhotoFilter recent() {
        return new PhotoFilter(null, false, true, null, SortOrder.NAME);
    }

    public PhotoFilter withSort(SortOrder sort) {
        return new PhotoFilter(folderId, favoritesOnly, recentOnly, search, sort);
    }

    public PhotoFilter withSearch(String search) {
        return new PhotoFilter(folderId, favoritesOnly, recentOnly, search, sort);
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
