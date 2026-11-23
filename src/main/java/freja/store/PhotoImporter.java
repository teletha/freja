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

import java.awt.image.BufferedImage;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import freja.util.ImageTransforms;
import freja.util.PathUtil;
import imagify.ImageFormat;
import imagify.ImageReader;
import imagify.ImageWriter;
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

    /** WebP quality of the thumbnail. */
    public static final double THUMBNAIL_QUALITY = 0.6;

    /**
     * WebP format for thumbnails, with a lower encoder effort than the default method 4. Thumbnails
     * are at most 220 px, so spending the default effort on them is not worth the scan time; the
     * quality stays {@link #THUMBNAIL_QUALITY} and only the file size grows slightly.
     */
    private static final ImageFormat THUMBNAIL_FORMAT = ImageFormat.WEBP.compressionMethod(2);

    private PhotoImporter() {
    }

    /** The result of inspecting one image file. */
    public record AnalyzedPhoto(int width, int height, long capturedAt, Exif.Data exif, byte[] thumbnail,
            int thumbnailWidth, int thumbnailHeight) {
    }

    /**
     * A decoded frame with the metadata and the dimensions the catalog has to record.
     *
     * @param opaque whether the pixels are known to be opaque, so no alpha scan is needed
     */
    private record Decoded(BufferedImage image, int width, int height, Exif.Data exif, boolean opaque) {
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

        // Decoders return an ARGB image even for an opaque JPEG, so the all-opaque alpha plane has
        // to be dropped. A JPEG's pixels are known to be opaque, which skips both the alpha scan and
        // the Java2D redraw; real transparency is still kept for formats that can carry it.
        BufferedImage thumbnail = decoded.opaque() || !ImageTransforms.hasTransparency(scaled)
                ? ImageTransforms.toOpaque(scaled) : scaled;

        byte[] encoded;
        try {
            encoded = ImageWriter.toBytes(thumbnail, THUMBNAIL_FORMAT, THUMBNAIL_QUALITY);
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
                    int[] size = jpegSize(encoded);
                    int denominator = scaleDenominator(Math.max(size[0], size[1]));
                    BufferedImage image = denominator == 1 ? decodeBytes(encoded, file)
                            : ImageReader.read(encoded, ImageFormat.JPEG, 1, denominator).toBufferedImage();
                    if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                        // A JPEG decoded here cannot carry transparency, so the alpha scan in
                        // analyze() can be skipped as well.
                        return new Decoded(image, size[0], size[1], exif, true);
                    }
                } catch (JpegException | IOException | RuntimeException e) {
                    // A header the decoder cannot read is no reason to give up on the file: the
                    // ordinary reader below knows how to report a damaged image as an IOException.
                }
            }
            BufferedImage image = decodeBytes(encoded, file);
            return new Decoded(image, image.getWidth(), image.getHeight(), exif, false);
        }
        // EXIF lives in JPEG segments only, so other formats need no metadata pass. Reading the
        // bytes lets imagify detect the real format from the header even if the extension lies.
        BufferedImage image = decodeBytes(Files.readAllBytes(file), file);
        return new Decoded(image, image.getWidth(), image.getHeight(), Exif.Data.EMPTY, false);
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
     * Reads the dimensions from a JPEG's SOF marker. jpegli's header reader copies the whole file
     * into native memory to answer the same two 16 bit numbers, so this scan avoids one full size
     * copy per photo. Segment payloads are skipped, not read.
     *
     * @param encoded the complete JPEG file
     * @return width and height, or null when no usable SOF marker was found
     */
    static int[] jpegDimensions(byte[] encoded) {
        if (encoded.length < 4 || u8(encoded, 0) != 0xFF || u8(encoded, 1) != 0xD8) {
            return null;
        }
        int position = 2;
        while (position + 3 < encoded.length) {
            if (u8(encoded, position) != 0xFF) {
                return null;
            }
            // A marker may be preceded by any number of fill bytes.
            while (position < encoded.length && u8(encoded, position) == 0xFF) {
                position++;
            }
            if (position >= encoded.length) {
                return null;
            }
            int marker = u8(encoded, position++);
            // Standalone markers carry no length; SOS and EOI mean no SOF is left to find.
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7) || marker == 0xD8) {
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return null;
            }
            if (position + 1 >= encoded.length) {
                return null;
            }
            int length = (u8(encoded, position) << 8) | u8(encoded, position + 1);
            if (length < 2 || position + length > encoded.length) {
                return null;
            }
            boolean frame = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) {
                if (length < 8) {
                    return null;
                }
                int height = (u8(encoded, position + 3) << 8) | u8(encoded, position + 4);
                int width = (u8(encoded, position + 5) << 8) | u8(encoded, position + 6);
                return width > 0 && height > 0 ? new int[] {width, height} : null;
            }
            position += length;
        }
        return null;
    }

    private static int u8(byte[] data, int index) {
        return data[index] & 0xFF;
    }

    /** Uses the cheap Java scan first and keeps jpegli's reader as the fallback. */
    private static int[] jpegSize(byte[] encoded) throws JpegException {
        int[] size = jpegDimensions(encoded);
        if (size != null) {
            return size;
        }
        JpegImageInfo info = JpegliCodec.readHeader(encoded);
        return new int[] {info.width(), info.height()};
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
