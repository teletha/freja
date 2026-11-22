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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import imagify.BufferedImageResize;
import imagify.ResizeAlgorithm;

/**
 * Pixel operations shared by thumbnail generation and the full screen viewer.
 */
public final class ImageTransforms {

    private ImageTransforms() {
    }

    /**
     * Applies an EXIF orientation to an image.
     * <p>
     * The mapping is the one described by the EXIF specification. Orientations outside the known
     * range leave the image untouched.
     * </p>
     *
     * @param source the decoded image
     * @param orientation the EXIF orientation, from 1 to 8
     * @return a correctly oriented image, or the source itself when no rotation is needed
     */
    public static BufferedImage orient(BufferedImage source, int orientation) {
        return switch (orientation) {
        case 2 -> flipHorizontal(source);
        case 3 -> rotate(source, 180);
        case 4 -> flipVertical(source);
        case 5 -> flipHorizontal(rotate(source, 90));
        case 6 -> rotate(source, 90);
        case 7 -> flipHorizontal(rotate(source, 270));
        case 8 -> rotate(source, 270);
        default -> source;
        };
    }

    /**
     * Rotates an image clockwise.
     *
     * @param source the source image
     * @param degrees 90, 180 or 270
     * @return the rotated image
     */
    public static BufferedImage rotate(BufferedImage source, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) {
            return source;
        }
        boolean quarter = normalized == 90 || normalized == 270;
        int width = quarter ? source.getHeight() : source.getWidth();
        int height = quarter ? source.getWidth() : source.getHeight();
        BufferedImage target = new BufferedImage(width, height, withAlpha(source, BufferedImage.TYPE_INT_ARGB));
        Graphics2D graphics = target.createGraphics();
        try {
            switch (normalized) {
            case 90 -> {
                graphics.translate(width, 0);
                graphics.rotate(Math.PI / 2);
            }
            case 180 -> {
                graphics.translate(width, height);
                graphics.rotate(Math.PI);
            }
            default -> {
                graphics.translate(0, height);
                graphics.rotate(-Math.PI / 2);
            }
            }
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Mirrors an image left to right.
     *
     * @param source the source image
     * @return the mirrored image
     */
    public static BufferedImage flipHorizontal(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), withAlpha(source, BufferedImage.TYPE_INT_ARGB));
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.translate(source.getWidth(), 0);
            graphics.scale(-1, 1);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Mirrors an image top to bottom.
     *
     * @param source the source image
     * @return the mirrored image
     */
    public static BufferedImage flipVertical(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), withAlpha(source, BufferedImage.TYPE_INT_ARGB));
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.translate(0, source.getHeight());
            graphics.scale(1, -1);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Scales an image down while keeping its alpha channel intact.
     * <p>
     * The resampling is done by imagify's Catmull-Rom filter. Catmull-Rom is sharper than Java2D's
     * default bicubic interpolation and is the algorithm imagify uses as its own default.
     * </p>
     *
     * @param source the source image
     * @param maxSize the maximum length of either edge
     * @return the scaled image
     */
    public static BufferedImage scale(BufferedImage source, int maxSize) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int width = sourceWidth;
        int height = sourceHeight;
        if (maxSize > 0 && (sourceWidth > maxSize || sourceHeight > maxSize)) {
            double scale = (double) maxSize / Math.max(sourceWidth, sourceHeight);
            width = Math.max(1, (int) Math.round(sourceWidth * scale));
            height = Math.max(1, (int) Math.round(sourceHeight * scale));
        }
        if (width == sourceWidth && height == sourceHeight) {
            return source;
        }
        return BufferedImageResize.resize(width, height, ResizeAlgorithm.CATROM).apply(source);
    }

    /**
     * Tests whether an image contains at least one pixel which is not fully opaque.
     * <p>
     * Decoders frequently return an ARGB image for an opaque source, so the presence of an alpha
     * channel alone is not enough.
     * </p>
     *
     * @param image the image
     * @return true when the alpha channel actually carries transparency
     */
    public static boolean hasTransparency(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Flattens an image onto an opaque background and optionally scales it down.
     *
     * @param source the source image
     * @param maxSize the maximum length of either edge, or 0 to keep the original size
     * @param background the colour behind transparent pixels
     * @return an opaque RGB image
     */
    public static BufferedImage flatten(BufferedImage source, int maxSize, Color background) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (maxSize > 0 && (width > maxSize || height > maxSize)) {
            double scale = (double) maxSize / Math.max(width, height);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
        }
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static int withAlpha(BufferedImage source, int opaque) {
        return source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : opaque;
    }
}
