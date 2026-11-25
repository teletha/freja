/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.web;

import java.util.List;
import java.util.Map;

import freja.model.LibraryStats;
import freja.model.Photo;

/**
 * The model records which cross the IPC boundary in the Web edition.
 * <p>
 * Apocrypha encodes records with Sinobu, so each type here is a plain data holder which the
 * browser decodes back into typed fields. The browser edition mirrors the JavaFX edition: the
 * renderer owns the DOM, these records carry catalog state, browse requests and results, and the
 * scanner report snapshot published to the status bar.
 * </p>
 */
public final class WebModels {

    private WebModels() {
    }

    /**
     * The payload published once the application is ready and the catalog is open.
     *
     * @param settings the persisted settings map, or null for none
     * @param libraryPath the absolute path of the SQLite library file
     * @param dataDirectory the directory where the application state lives
     * @param language the interface language resolved from the Java-side translation layer
     * @param startupFolders the folders passed on the command line at startup
     * @param folders the visible folders of the catalog
     * @param tags the tags attached to folders
     * @param stats the aggregate counters shown by the sidebar and status bar
     * @param scanner the latest scanner state snapshot, or null before the first report
     */
    public record Bootstrap(Map<String, String> settings, String libraryPath, String dataDirectory,
            String language, List<String> startupFolders, List<freja.model.Folder> folders, List<freja.model.Tag> tags,
            LibraryStats stats, ScannerReport scanner) {

        public Bootstrap {
            settings = settings == null ? Map.of() : Map.copyOf(settings);
            libraryPath = libraryPath == null || libraryPath.isBlank() ? "library.db" : libraryPath;
            dataDirectory = dataDirectory == null || dataDirectory.isBlank() ? ".freja-web" : dataDirectory;
            language = language == null ? "ja" : language;
            startupFolders = startupFolders == null ? List.of() : List.copyOf(startupFolders);
            folders = folders == null ? List.of() : List.copyOf(folders);
            tags = tags == null ? List.of() : List.copyOf(tags);
            stats = stats == null ? new LibraryStats(0, 0, 0, 0, 0) : stats;
        }
    }

    /**
     * A browse request sent by the renderer to the {@code freja:browse} channel.
     * <p>
     * The {@code folderId} is a string because sinobu cannot deserialize a JSON {@code null}
     * into a boxed {@code Long}: it converts null to an empty string first and
     * {@code Long.parseLong("")} throws a NumberFormatException. The handler parses the value
     * with a blank guard instead.
     * </p>
     *
     * @param mode the navigation mode: ALL, FAVORITES, RECENT or FOLDER
     * @param folderId the selected folder identifier as a string, or null/blank for none
     * @param sort the name of the {@code PhotoFilter.SortOrder} in effect
     * @param search the free-text search, or null for none
     * @param tagIds the tag identifiers currently selected, or empty for none
     * @param offset the index of the first photo to return, from the ordered result set
     * @param limit the maximum number of photo rows in the reply
     */
    public record BrowseRequest(String mode, String folderId, String sort, String search, List<Long> tagIds, int offset,
            int limit) {

        public BrowseRequest {
            mode = mode == null || mode.isBlank() ? "ALL" : mode.toUpperCase();
            folderId = folderId == null || folderId.isBlank() ? null : folderId.trim();
            sort = sort == null || sort.isBlank() ? "NAME" : sort.toUpperCase();
            tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
            offset = Math.max(0, offset);
            limit = limit <= 0 ? 300 : Math.min(2000, limit);
        }
    }

    /**
     * The result of a browse, published back to the renderer.
     *
     * @param mode the effective mode, after fallback
     * @param folderId the effective folder, or null
     * @param stats the aggregate counters
     * @param folders the visible folders of the catalog
     * @param sections the ordered section descriptors of the grid, without photo metadata
     * @param photos the current page of photos, flattened in section order
     * @param photoSections the section index of each photo in {@code photos}
     * @param offset the index of the first returned photo
     * @param total the total number of photos matching the query
     * @param hasMore whether another page is available
     */
    public record BrowseResult(String mode, Long folderId, LibraryStats stats, List<freja.model.Folder> folders,
            List<Section> sections, List<Photo> photos, List<Integer> photoSections, int offset, int total, boolean hasMore) {

        public BrowseResult {
            mode = mode == null ? "ALL" : mode;
            stats = stats == null ? new LibraryStats(0, 0, 0, 0, 0) : stats;
            folders = folders == null ? List.of() : List.copyOf(folders);
            sections = sections == null ? List.of() : List.copyOf(sections);
            photos = photos == null ? List.of() : List.copyOf(photos);
            photoSections = photoSections == null ? List.of() : List.copyOf(photoSections);
            offset = Math.max(0, offset);
            total = Math.max(0, total);
        }
    }

    /**
     * One photo section descriptor: a heading, the folder it belongs to, its tags and its size.
     * <p>
     * Photo metadata is no longer carried here. {@code start} is the section's first index in the
     * flattened result set, so the renderer can reserve the full scroll height and request only
     * the pages which are actually visible.
     * </p>
     *
     * @param folderId the folder identifier, or -1 for a synthetic section
     * @param label the section heading, or null when the section is unnamed
     * @param tags the tags attached to the folder, or empty
     * @param start the index of the section's first photo in the ordered result
     * @param count the number of photos under the heading
     */
    public record Section(long folderId, String label, List<String> tags, int start, int count) {

        public Section {
            tags = tags == null ? List.of() : List.copyOf(tags);
            start = Math.max(0, start);
            count = Math.max(0, count);
        }
    }

    /**
     * A snapshot of the scanner state published to the status bar. The {@code elapsed} duration is
     * flattened to milliseconds so the browser never sees a {@code java.time.Duration}.
     *
     * @param scanning whether a scan is currently running
     * @param watching whether the file system watcher is active
     * @param processed the number of photo rows seen by the current scan
     * @param total the number of photo rows in the current scan
     * @param errors the number of directories which failed
     * @param imported the number of photos imported so far
     * @param message the latest scanner message, or empty
     * @param elapsedMillis the scan elapsed time in milliseconds
     */
    public record ScannerReport(boolean scanning, boolean watching, int processed, int total, int errors, int imported,
            String message, long elapsedMillis) {

        public ScannerReport {
            message = message == null ? "" : message;
        }
    }

    /**
     * The settings map sent by the renderer on every change.
     *
     * @param values the full map
     */
    public record SettingsMap(Map<String, String> values) {
    }

    /**
     * A list of photo identifiers for the photo action channels.
     *
     * @param ids the identifiers
     */
    public record PhotoIds(List<Long> ids) {

        public PhotoIds {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /**
     * A favorite request: the identifiers and the flag.
     *
     * @param ids the identifiers
     * @param value the favorite flag
     */
    public record FavoriteRequest(List<Long> ids, boolean value) {

        public FavoriteRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /**
     * A list of folder identifiers for the folder action channels.
     *
     * @param ids the identifiers
     */
    public record FolderIds(List<Long> ids) {

        public FolderIds {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /**
     * A list of tag identifiers for the tag action channels.
     *
     * @param ids the identifiers
     */
    public record TagIds(List<Long> ids) {

        public TagIds {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /**
     * A bulk rename request.
     *
     * @param ids the identifiers
     * @param from the text to replace
     * @param to the replacement text
     */
    public record BulkRename(List<Long> ids, String from, String to) {

        public BulkRename {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    /**
     * A link request: the source tag and its targets.
     *
     * @param id the tag identifier
     * @param targets the linked tag identifiers
     */
    public record SetLinks(long id, List<Long> targets) {

        public SetLinks {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    /**
     * A folder tag request: the folder and its tag names.
     *
     * @param folderId the folder identifier
     * @param names the tag names
     */
    public record SetFolderTags(long folderId, List<String> names) {

        public SetFolderTags {
            names = names == null ? List.of() : List.copyOf(names);
        }
    }
}
