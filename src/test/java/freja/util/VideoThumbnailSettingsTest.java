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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VideoThumbnailSettingsTest {

    @Test
    void remembersTheChosenPosition() {
        int original = VideoThumbnailSettings.thumbnailSeconds();
        try {
            VideoThumbnailSettings.thumbnailSeconds(45);
            assertEquals(45, VideoThumbnailSettings.thumbnailSeconds());
        } finally {
            VideoThumbnailSettings.thumbnailSeconds(original);
        }
    }

    @Test
    void clampsOutOfRangePositions() {
        int original = VideoThumbnailSettings.thumbnailSeconds();
        try {
            VideoThumbnailSettings.thumbnailSeconds(-10);
            assertEquals(VideoThumbnailSettings.MIN_SECONDS, VideoThumbnailSettings.thumbnailSeconds());
            VideoThumbnailSettings.thumbnailSeconds(VideoThumbnailSettings.MAX_SECONDS + 10);
            assertEquals(VideoThumbnailSettings.MAX_SECONDS, VideoThumbnailSettings.thumbnailSeconds());
        } finally {
            VideoThumbnailSettings.thumbnailSeconds(original);
        }
    }
}
