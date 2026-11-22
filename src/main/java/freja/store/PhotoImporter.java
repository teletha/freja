/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.store;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import freja.util.ImageTransforms;
import imagify.ImageFormat;
import imagify.Imagify;

/**
 * Decodes an image once and produces everything the library stores about it.
 */
public final class PhotoImporter {

    /** The longest edge of a cached thumbnail. */
    public static final int THUMBNAIL_SIZE = 320;

    /** The background used behind transparent images before flattening. */
    public static final Color THUMBNAIL_BACKGROUND = new Color(0xF4F5F7);

    /** WebP quality of the thumbnail. */
    public static final double THUMBNAIL_QUALITY = 0.6;

    private PhotoImporter() {
    }

    /** The result of inspecting one image file. */
    public record AnalyzedPhoto(int width, int height, long capturedAt, Exif.Data exif, byte[] thumbnail,
            int thumbnailWidth, int thumbnailHeight) {
    }

    /**
     * Decodes an image and extracts its metadata and thumbnail.
     *
     * @param file an image file
     * @return the analysis
     * @throws IOException when the file cannot be decoded
     */
    public static AnalyzedPhoto analyze(Path file) throws IOException {
        Exif.Data exif = Exif.read(file);
        BufferedImage image;
        try {
            image = Imagify.read(file).toBufferedImage();
        } catch (RuntimeException | IOError | LinkageError e) {
            throw new IOException("Unsupported or damaged image: " + file.getFileName(), e);
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Image has no pixels: " + file.getFileName());
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage oriented = ImageTransforms.orient(image, exif.orientation());
        BufferedImage scaled = ImageTransforms.scale(oriented, THUMBNAIL_SIZE);

        // Many decoders return an ARGB image even for an opaque JPEG. Flatten those images so the
        // WebP encoder does not spend bytes on an all-opaque alpha plane.
        BufferedImage thumbnail = ImageTransforms.hasTransparency(scaled) ? scaled : ImageTransforms.flatten(scaled, 0, THUMBNAIL_BACKGROUND);

        byte[] encoded;
        try {
            encoded = Imagify.read(thumbnail).writeToBytes(ImageFormat.WEBP, THUMBNAIL_QUALITY);
        } catch (RuntimeException | IOError | LinkageError e) {
            throw new IOException("Failed to encode the thumbnail: " + file.getFileName(), e);
        }

        long capturedAt = exif.capturedAt() != null ? exif.capturedAt() : Files.getLastModifiedTime(file).toMillis();
        return new AnalyzedPhoto(width, height, capturedAt, exif, encoded, thumbnail.getWidth(), thumbnail.getHeight());
    }
}
