/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import freja.model.Photo;
import freja.store.Library;
import freja.util.ImageTransforms;
import imagify.Imagify;

/**
 * JavaFX image helpers shared by the grid, the inspector and the viewer. The Swing edition converts
 * decoded bitmaps with {@code SwingFXUtils}; this edition avoids the {@code javafx.swing} module by
 * copying the ARGB raster directly.
 */
final class FxImages {

    private FxImages() {
    }

    /**
     * Wraps stored thumbnail bytes as a JavaFX image.
     *
     * @param thumbnail a catalog thumbnail, or null
     * @return the image, or null when there is no thumbnail
     */
    static Image from(Library.Thumbnail thumbnail) {
        return thumbnail == null ? null : new Image(new ByteArrayInputStream(thumbnail.data()));
    }

    /**
     * Converts a decoded AWT bitmap, e.g. one with the EXIF orientation already applied.
     *
     * @param source a bitmap
     * @return an equivalent JavaFX image
     */
    static Image from(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }

    /**
     * Decodes a photo at full size with the EXIF orientation applied. Never call this on the UI
     * thread.
     *
     * @param photo the photo
     * @return the full-size image
     * @throws Exception when the file is missing or undecodable
     */
    static Image full(Photo photo) throws Exception {
        BufferedImage image = ImageTransforms.orient(Imagify.read(java.nio.file.Files.readAllBytes(photo.path())).toBufferedImage(), photo
                .orientation());
        return from(image);
    }
}
