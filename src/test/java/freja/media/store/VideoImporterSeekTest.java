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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import freja.util.VideoThumbnailSettings;

class VideoImporterSeekTest {

    @Test
    void usesTheConfiguredPosition() {
        assertEquals(30, VideoImporter.seekTarget(600, 30));
    }

    @Test
    void clampsToTheMiddleOfShortClips() {
        assertEquals(5, VideoImporter.seekTarget(10, 30));
        assertEquals(2.5, VideoImporter.seekTarget(5, 30));
    }

    @Test
    void keepsTheFirstFrameWhenRequested() {
        assertEquals(0, VideoImporter.seekTarget(600, 0));
        assertEquals(0, VideoImporter.seekTarget(600, -1));
    }

    @Test
    void doesNotSeekInVeryShortOrUnknownClips() {
        assertEquals(0, VideoImporter.seekTarget(1, 30));
        assertEquals(0, VideoImporter.seekTarget(0, 30));
    }

    @Test
    void keepsSmallPositivePositions() {
        assertEquals(1, VideoImporter.seekTarget(600, VideoThumbnailSettings.MIN_SECONDS + 1));
    }
}
