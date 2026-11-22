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

import static org.junit.jupiter.api.Assertions.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ImageTransformsTest {

    @Test
    void downscalePreservesAspectRatioAndTransparency() {
        BufferedImage source = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 1200, 800);
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, 200, 200);
        } finally {
            graphics.dispose();
        }

        BufferedImage reduced = ImageTransforms.downscale(source, 600);

        assertEquals(600, reduced.getWidth());
        assertEquals(400, reduced.getHeight());
        assertTrue(reduced.getColorModel().hasAlpha());
        assertTrue(ImageTransforms.hasTransparency(reduced));

        BufferedImage thumbnail = ImageTransforms.scale(reduced, 220);
        assertEquals(220, thumbnail.getWidth());
        assertEquals(147, thumbnail.getHeight());
    }

    @Test
    void downscaleLeavesSmallImagesAlone() {
        BufferedImage source = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        assertSame(source, ImageTransforms.downscale(source, 640));
        assertSame(source, ImageTransforms.scale(source, 320));
    }

    @Test
    void progressiveDownscaleEndsAtTheSameSizeAsDirectScale() {
        BufferedImage source = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        BufferedImage direct = ImageTransforms.scale(source, 320);
        BufferedImage progressive = ImageTransforms.scale(ImageTransforms.downscale(source, 640), 320);

        assertEquals(direct.getWidth(), progressive.getWidth());
        assertEquals(direct.getHeight(), progressive.getHeight());
    }
}
