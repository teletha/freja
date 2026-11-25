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

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.scan.LibraryScanner;
import freja.model.Tag;

/**
 * The JSON documents exchanged between the Java backend and the Apocrypha renderer.
 * <p>
 * These are transfer models only. The catalog records ({@link Photo}, {@link Folder}) carry
 * {@code Path} values and lazily computed labels; the cards here hold the flat, string-safe shape
 * the browser needs, so a refresh never leaks file system objects into JavaScript.
 * </p>
 */
public final class WebApi {

    /** How a request selects the photos to display. */
    public record BrowseRequest(String mode, Long folderId, String sort, String search, List<Long> tagIds) {
    }

    /** One grid section: a header plus the photos below it. */
    public record Section(long folderId, String label, List<String> tags, List<PhotoCard> photos) {
    }

    /** Everything the window needs after a browse. */
    public record BrowseResult(LibraryStats stats, List<FolderCard> folders, List<Section> sections, String mode, Long folderId) {
    }

    /** One thumbnail cell. */
    public record PhotoCard(long id, long folderId, String folderName, String fileName, String extension, long byteSize,
            long modifiedAt, int width, int height, int orientation, long capturedAt, boolean favorite, int rating,
            String caption) {
    }

    /** The full record shown by the inspector. */
    public record PhotoDetail(long id, long folderId, String folderName, String folderPath, String path,
            String fileName, String extension, long byteSize, long modifiedAt, int width, int height, int orientation,
            long capturedAt, String cameraMake, String cameraModel, String lens, String exposure, Double aperture,
            Integer iso, Double focalLength, boolean favorite, int rating, String caption) {
    }

    /** One sidebar folder row. */
    public record FolderCard(long id, String name, String path, int photoCount, List<String> tags, boolean explicit,
            boolean scanEnabled, String sort) {
    }

    /** One tag with its folder count. */
    public record TagCard(long id, String name, int folderCount) {
    }

    /** A scanner snapshot for the status bar. */
    public record ScannerReport(boolean scanning, boolean watching, int total, int processed, int imported, int errors,
            String message, long elapsedMillis) {

        /** Converts the catalog state into its JSON shape. */
        public static ScannerReport of(LibraryScanner.State state) {
            return new ScannerReport(state.scanning(), state.watching(), state.total(), state.processed(),
                    state.imported(), state.errors(), state.message(), state.elapsed().toMillis());
        }
    }

    /** The payload delivered once the catalog and the scanner are live. */
    public record Bootstrap(String version, String libraryPath, String dataDirectory, String javaVersion,
            String platform, String language, Map<String, String> settings, List<String> startupFolders,
            LibraryStats stats, List<FolderCard> folders, List<TagCard> tags, ScannerReport scanner) {
    }

    /** A batch of photo identifiers. */
    public record PhotoIds(List<Long> ids) {
    }

    /** A batch of folder identifiers. */
    public record FolderIds(List<Long> ids) {
    }

    /** Favorite changes for many photos at once. */
    public record FavoriteRequest(List<Long> ids, boolean value) {
    }

    /** Tag deletions by identifier. */
    public record TagIds(List<Long> ids) {
    }

    /** The complete outgoing link set of one tag. */
    public record TagLinks(long id, List<Long> targets) {
    }

    /** One directed tag link, source implies target. */
    public record Link(long source, long target) {
    }

    /** The complete tag name set of one folder. */
    public record FolderTags(long folderId, List<String> names) {
    }

    /** A bulk find-and-replace across tag names. */
    public record BulkRename(List<Long> ids, String from, String to) {
    }

    /** The complete settings map of this edition, sent by the renderer. */
    public record SettingsMap(Map<String, String> values) {
    }

    /** The result of deleting files from disk. */
    public record DeleteResult(int deleted, int failed) {
    }

    private WebApi() {
    }

    /**
     * Projects a catalog photo onto a grid card.
     *
     * @param photo a catalog photo
     * @return the card for the renderer
     */
    static PhotoCard card(Photo photo) {
        return new PhotoCard(photo.id(), photo.folderId(), photo.folderLabel(), photo.fileName(), photo.extension(),
                photo.byteSize(), photo.modifiedAt(), photo.width(), photo.height(), photo.orientation(),
                photo.capturedAt(), photo.favorite(), photo.rating(), photo.caption());
    }

    /**
     * Projects a catalog photo onto the inspector payload.
     *
     * @param photo a catalog photo
     * @return the detail for the renderer
     */
    static PhotoDetail detail(Photo photo) {
        return new PhotoDetail(photo.id(), photo.folderId(), photo.folderLabel(), photo.folderPath(),
                photo.path().toString(), photo.fileName(), photo.extension(), photo.byteSize(), photo.modifiedAt(),
                photo.width(), photo.height(), photo.orientation(), photo.capturedAt(), photo.cameraMake(),
                photo.cameraModel(), photo.lens(), photo.exposure(), photo.aperture(), photo.iso(),
                photo.focalLength(), photo.favorite(), photo.rating(), photo.caption());
    }

    /**
     * Projects a catalog folder onto a sidebar row.
     *
     * @param folder a catalog folder
     * @return the card for the renderer
     */
    static FolderCard card(Folder folder) {
        PhotoFilter.SortOrder sort = folder.sort() == null ? PhotoFilter.SortOrder.NAME : folder.sort();
        return new FolderCard(folder.id(), folder.name(), folder.path().toString(), folder.photoCount(), folder.tags(),
                folder.explicit(), folder.scanEnabled(), sort.name());
    }

    /**
     * Projects a catalog tag.
     *
     * @param tag a catalog tag
     * @return the card for the renderer
     */
    static TagCard card(Tag tag) {
        return new TagCard(tag.id(), tag.name(), tag.folderCount());
    }
}
