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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PathUtilTest {

    @Test
    void recognizesVideoExtensions() {
        assertTrue(PathUtil.isVideo(Path.of("clip.MP4")));
        assertTrue(PathUtil.isVideo(Path.of("clip.mkv")));
        assertTrue(PathUtil.isVideo(Path.of("clip.webm")));
        assertFalse(PathUtil.isVideo(Path.of("clip.jpg")));
        assertFalse(PathUtil.isVideo(Path.of("clip.txt")));
    }

    @Test
    void supportedMediaCoversImagesAndVideos() {
        assertTrue(PathUtil.isSupported(Path.of("photo.png")));
        assertTrue(PathUtil.isSupported(Path.of("clip.mp4")));
        assertFalse(PathUtil.isSupported(Path.of("notes.txt")));
    }
}
