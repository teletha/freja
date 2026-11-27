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

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import freja.util.PathUtil;

/**
 * One photo managed by the library.
 * <p>
 * The record contains metadata only. The thumbnail bytes are loaded separately so that a view of
 * ten thousand photos does not carry ten thousand images in memory.
 * </p>
 */
public record Photo(long id, long folderId, String folderPath, Path path, String fileName, String extension, long byteSize, long modifiedAt, int width, int height, long capturedAt, String cameraMake, String cameraModel, String lens, String exposure, Double aperture, Integer iso, Double focalLength, int orientation, boolean favorite, int rating, String caption, long importedAt, String archivePath, String archiveType, String entryName, long durationMillis) {

    /** Rows created before video support have no duration. */
    public Photo(long id, long folderId, String folderPath, Path path, String fileName, String extension, long byteSize, long modifiedAt, int width, int height, long capturedAt, String cameraMake, String cameraModel, String lens, String exposure, Double aperture, Integer iso, Double focalLength, int orientation, boolean favorite, int rating, String caption, long importedAt, String archivePath, String archiveType, String entryName) {
        this(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, favorite, rating, caption, importedAt, archivePath, archiveType, entryName, 0);
    }

    private static volatile DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPANESE);

    private static volatile DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.JAPANESE);

    private static volatile String formattedLanguage;

    /**
     * Rebuilds the date formatters when the interface language changes. Japanese keeps the
     * patterns the application was designed around; every other language gets its locale's style.
     */
    private static synchronized void followLanguage() {
        String language = laevatain.util.Language.language();
        if (language.equals(formattedLanguage)) {
            return;
        }
        formattedLanguage = language;
        Locale locale = laevatain.util.Language.locale();
        DATE = "ja".equals(language) ? DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
                : DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale);
        DATE_TIME = "ja".equals(language) ? DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", locale)
                : DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
                        .withLocale(locale);
    }

    /**
     * The width as displayed, after applying the EXIF orientation.
     *
     * @return the visible width in pixels
     */
    public int displayWidth() {
        return orientation >= 5 && orientation <= 8 ? height : width;
    }

    /**
     * The height as displayed, after applying the EXIF orientation.
     *
     * @return the visible height in pixels
     */
    public int displayHeight() {
        return orientation >= 5 && orientation <= 8 ? width : height;
    }

    /**
     * Whether the pixels of this photo could not be decoded when it was imported.
     * <p>
     * Such a file is still a catalog row: it has a generated placeholder thumbnail, and every
     * operation except opening the original works on it. Unknown dimensions are the marker,
     * because {@link freja.store.Library} records zero width and height for these rows.
     * </p>
     *
     * @return true when the original cannot be decoded
     */
    public boolean isUnreadable() {
        return width <= 0 || height <= 0;
    }

    /**
     * Returns the display name of the folder which directly contains this photo.
     *
     * @return the last element of the folder path
     */
    public String folderLabel() {
        if (folderPath == null || folderPath.isBlank()) {
            return "";
        }
        try {
            Path folder = Path.of(folderPath);
            Path name = folder.getFileName();
            return name == null ? folderPath : name.toString();
        } catch (RuntimeException e) {
            return folderPath;
        }
    }

    /**
     * Returns the capture time in the local zone.
     *
     * @return the capture date and time
     */
    public LocalDateTime capturedLocal() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(capturedAt), ZoneId.systemDefault());
    }

    /**
     * Returns the modification time in the local zone.
     *
     * @return the file modification date and time
     */
    public LocalDateTime modifiedLocal() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(modifiedAt), ZoneId.systemDefault());
    }

    /**
     * Returns a short capture date for the inspector.
     *
     * @return for example "2026年7月23日 09:44"
     */
    public String capturedLabel() {
        followLanguage();
        return DATE_TIME.format(capturedLocal());
    }

    /**
     * Returns a short date used by timeline headers.
     *
     * @return for example "2026年7月23日"
     */
    public String dateLabel() {
        followLanguage();
        return DATE.format(capturedLocal());
    }

    /**
     * Returns a file size label.
     *
     * @return for example "1.2 MB"
     */
    public String sizeLabel() {
        return formatSize(byteSize);
    }

    /**
     * Returns a dimensions label. Photos whose pixels could not be decoded have no known size, so
     * they get a label instead of reporting "0 × 0".
     *
     * @return for example "4032 × 3024"
     */
    public String dimensionsLabel() {
        if (isUnreadable()) {
            return "サイズ不明";
        }
        return displayWidth() + " × " + displayHeight();
    }

    /**
     * Returns the camera name, if any.
     *
     * @return for example "SONY ILCE-7M3"
     */
    public String cameraLabel() {
        if (cameraMake == null) {
            return cameraModel;
        }
        if (cameraModel == null) {
            return cameraMake;
        }
        return cameraModel.regionMatches(true, 0, cameraMake, 0, cameraMake.length()) ? cameraModel : cameraMake + " " + cameraModel;
    }

    /**
     * Returns the extension in upper case for badges.
     *
     * @return for example "JPG"
     */
    public String formatLabel() {
        return extension == null ? "" : extension.toUpperCase(Locale.ROOT);
    }

    /**
     * Formats a byte count for humans.
     *
     * @param bytes a byte count
     * @return a compact label
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Whether this row is a playable video file rather than a still image.
     *
     * @return true for a supported video extension
     */
    public boolean isVideo() {
        return !archived() && PathUtil.isVideo(path);
    }

    /**
     * Returns the playback duration as {@code m:ss}.
     *
     * @return the formatted duration, or an empty string for still images
     */
    public String durationLabel() {
        if (durationMillis <= 0) {
            return "";
        }
        long seconds = durationMillis / 1000;
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * Whether this row is an image inside a ZIP or 7z archive rather than a file on disk.
     * Such a row has a synthetic {@link #path()} and cannot be revealed or deleted on its own.
     *
     * @return true for an archive entry
     */
    public boolean archived() {
        return archiveType != null && archivePath != null;
    }

    /**
     * The real file behind this row: an archive for indexed entries, the photo file otherwise.
     * Use this for "reveal in Explorer" and similar operations which need an existing path.
     *
     * @return an existing file, or the synthetic path for regular photos
     */
    public Path origin() {
        return archived() ? Path.of(archivePath) : path;
    }

    public Photo withFavorite(boolean value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, value, rating, caption, importedAt, archivePath, archiveType, entryName, durationMillis);
    }

    public Photo withRating(int value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, favorite, value, caption, importedAt, archivePath, archiveType, entryName, durationMillis);
    }

    public Photo withCaption(String value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, favorite, rating, value, importedAt, archivePath, archiveType, entryName, durationMillis);
    }
}
