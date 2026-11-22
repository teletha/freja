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
import freja.util.PathUtil;
import imagify.ImageFormat;
import imagify.ImageReader;
import imagify.Imagify;
import imagify.jpeg.JpegException;
import imagify.jpeg.JpegImageInfo;
import imagify.jpeg.ffm.JpegliCodec;

/**
 * Decodes an image once and produces everything the library stores about it.
 */
public final class PhotoImporter {

    /** The longest edge of a cached thumbnail. */
    public static final int THUMBNAIL_SIZE = 220;

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

    /** A decoded frame with the metadata and the dimensions the catalog has to record. */
    private record Decoded(BufferedImage image, int width, int height, Exif.Data exif) {
    }

    /**
     * Decodes an image and extracts its metadata and thumbnail.
     *
     * @param file an image file
     * @return the analysis
     * @throws IOException when the file cannot be decoded
     */
    public static AnalyzedPhoto analyze(Path file) throws IOException {
        return analyze(file, -1);
    }

    /**
     * Decodes an image with the modification time already known by the scanner.
     *
     * @param file an image file
     * @param modifiedAt the file's modification time, or -1 to read it when needed
     * @return the analysis
     * @throws IOException when the file cannot be decoded
     */
    public static AnalyzedPhoto analyze(Path file, long modifiedAt) throws IOException {
        Decoded decoded = decode(file);
        BufferedImage image = decoded.image();
        Exif.Data exif = decoded.exif();

        // Reduce before rotating: rotating a 12 MP image is far more expensive than rotating the few
        // hundred pixels the thumbnail ends up with. A JPEG decoded with DCT scaling is already
        // small, so this pass becomes a no-op for it. The final CATROM pass still does the precision
        // work on a small image.
        BufferedImage reduced = ImageTransforms.downscale(image, THUMBNAIL_SIZE * 2);
        BufferedImage oriented = ImageTransforms.orient(reduced, exif.orientation());
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

        long capturedAt = exif.capturedAt() != null ? exif.capturedAt()
                : modifiedAt >= 0 ? modifiedAt : Files.getLastModifiedTime(file).toMillis();
        return new AnalyzedPhoto(decoded.width(), decoded.height(), capturedAt, exif, encoded,
                thumbnail.getWidth(), thumbnail.getHeight());
    }

    /**
     * Decodes a file, asking JPEG's decoder to scale while decoding when the loaded native library
     * supports it. The catalog still records the original dimensions, which come from the header,
     * not from the reduced frame. A JPEG is read into memory once: the same bytes serve the header,
     * the EXIF block and the decoder.
     */
    private static Decoded decode(Path file) throws IOException {
        if (isJpeg(file)) {
            byte[] encoded = Files.readAllBytes(file);
            Exif.Data exif = Exif.read(encoded);
            if (JpegliCodec.isAvailable() && JpegliCodec.supportsScaledDecode()) {
                try {
                    JpegImageInfo info = JpegliCodec.readHeader(encoded);
                    int denominator = scaleDenominator(Math.max(info.width(), info.height()));
                    BufferedImage image = denominator == 1 ? decodeBytes(encoded, file)
                            : ImageReader.read(encoded, 1, denominator).toBufferedImage();
                    if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                        return new Decoded(image, info.width(), info.height(), exif);
                    }
                } catch (JpegException | IOException | RuntimeException e) {
                    // A header the decoder cannot read is no reason to give up on the file: the
                    // ordinary reader below knows how to report a damaged image as an IOException.
                }
            }
            BufferedImage image = decodeBytes(encoded, file);
            return new Decoded(image, image.getWidth(), image.getHeight(), exif);
        }
        // EXIF lives in JPEG segments only, so other formats need no metadata pass at all.
        BufferedImage image;
        try {
            image = Imagify.read(file).toBufferedImage();
        } catch (RuntimeException | IOError | LinkageError e) {
            throw new IOException("Unsupported or damaged image: " + file.getFileName(), e);
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Image has no pixels: " + file.getFileName());
        }
        return new Decoded(image, image.getWidth(), image.getHeight(), Exif.Data.EMPTY);
    }

    private static BufferedImage decodeBytes(byte[] encoded, Path file) throws IOException {
        BufferedImage image;
        try {
            image = Imagify.read(encoded).toBufferedImage();
        } catch (RuntimeException | IOError | LinkageError e) {
            throw new IOException("Unsupported or damaged image: " + file.getFileName(), e);
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Image has no pixels: " + file.getFileName());
        }
        return image;
    }

    private static boolean isJpeg(Path file) {
        String extension = PathUtil.extension(file);
        return extension.equals("jpg") || extension.equals("jpeg");
    }

    /**
     * Picks the largest DCT scale that still leaves enough pixels for a 220 px thumbnail. The
     * ratios are the ones imagify's scaled decode can produce: 1/2, 1/4 and 1/8.
     */
    private static int scaleDenominator(int longestEdge) {
        if (longestEdge >= 1760) {
            return 8;
        }
        if (longestEdge >= 880) {
            return 4;
        }
        if (longestEdge >= 440) {
            return 2;
        }
        return 1;
    }
}
