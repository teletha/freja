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

/**
 * One photo managed by the library.
 * <p>
 * The record contains metadata only. The thumbnail bytes are loaded separately so that a view of
 * ten thousand photos does not carry ten thousand images in memory.
 * </p>
 */
public record Photo(long id, long folderId, String folderPath, Path path, String fileName, String extension,
        long byteSize, long modifiedAt, int width, int height, long capturedAt, String cameraMake, String cameraModel,
        String lens, String exposure, Double aperture, Integer iso, Double focalLength, int orientation,
        boolean favorite, int rating, String caption, long importedAt) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.JAPANESE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.JAPANESE);

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
        return DATE_TIME.format(capturedLocal());
    }

    /**
     * Returns a short date used by timeline headers.
     *
     * @return for example "2026年7月23日"
     */
    public String dateLabel() {
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
     * Returns a dimensions label.
     *
     * @return for example "4032 × 3024"
     */
    public String dimensionsLabel() {
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

    public Photo withFavorite(boolean value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, value, rating, caption, importedAt);
    }

    public Photo withRating(int value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, favorite, value, caption, importedAt);
    }

    public Photo withCaption(String value) {
        return new Photo(id, folderId, folderPath, path, fileName, extension, byteSize, modifiedAt, width, height, capturedAt, cameraMake, cameraModel, lens, exposure, aperture, iso, focalLength, orientation, favorite, rating, value, importedAt);
    }
}
