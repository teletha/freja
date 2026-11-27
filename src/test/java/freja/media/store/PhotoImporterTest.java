/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.store;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import imagify.ImageFormat;
import imagify.Imagify;

class PhotoImporterTest {

    @TempDir
    Path temp;

    @Test
    void jpegKeepsItsOriginalDimensionsWhileTheThumbnailIsSmall() throws Exception {
        BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            for (int y = 0; y < 600; y++) {
                int level = y * 255 / 599;
                graphics.setColor(new Color(level, (level * 3) & 0xFF, 255 - level));
                graphics.drawLine(0, y, 800, y);
            }
        } finally {
            graphics.dispose();
        }
        Path file = Files.createFile(temp.resolve("photo.jpg"));
        assertTrue(ImageIO.write(source, "jpg", file.toFile()));

        PhotoImporter.AnalyzedPhoto analyzed = PhotoImporter.analyze(file);

        // The catalog records the size from the JPEG header, even when the decoder reduced the
        // pixels it handed back for the thumbnail.
        assertEquals(800, analyzed.width());
        assertEquals(600, analyzed.height());

        byte[] thumbnail = analyzed.thumbnail();
        assertEquals('R', thumbnail[0]);
        assertEquals('I', thumbnail[1]);
        assertEquals('F', thumbnail[2]);
        assertEquals('F', thumbnail[3]);
        BufferedImage decoded = Imagify.read(thumbnail).toBufferedImage();
        assertEquals(PhotoImporter.THUMBNAIL_SIZE, decoded.getWidth());
        assertEquals(165, decoded.getHeight());
    }

    @Test
    void readsJpegDimensionsFromTheSofMarker() throws Exception {
        BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Path file = Files.createFile(temp.resolve("dims.jpg"));
        assertTrue(ImageIO.write(source, "jpg", file.toFile()));

        assertArrayEquals(new int[] {800, 600}, PhotoImporter.jpegDimensions(Files.readAllBytes(file)));
    }

    @Test
    void rejectsDataWithoutAJpegMarker() {
        assertNull(PhotoImporter.jpegDimensions(new byte[0]));
        assertNull(PhotoImporter.jpegDimensions(new byte[] {1, 2, 3, 4}));
    }

    @Test
    void detectsTheRealFormatWhenTheExtensionIsWrong() throws Exception {
        BufferedImage source = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        try {
            graphics.setColor(Color.CYAN);
            graphics.fillRect(0, 0, 640, 480);
        } finally {
            graphics.dispose();
        }
        Path file = temp.resolve("mislabeled.jpg");
        Files.write(file, Imagify.read(source).writeToBytes(ImageFormat.WEBP, 0.8));

        PhotoImporter.AnalyzedPhoto analyzed = PhotoImporter.analyze(file);

        assertEquals(640, analyzed.width());
        assertEquals(480, analyzed.height());
    }
}
