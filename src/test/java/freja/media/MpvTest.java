/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MpvTest {

    @Test
    void extractsStillFrameFromVideo() throws IOException {
        String sample = System.getProperty("freja.mpv.sample");
        assumeTrue(sample != null && !sample.isBlank(), "Set -Dfreja.mpv.sample=<path to a local mp4>");
        assumeTrue(MpvNative.isAvailable(), "libmpv is not available on this machine");

        Path video = Path.of(sample);
        assumeTrue(Files.isRegularFile(video), "Sample video does not exist: " + sample);

        Path image = Files.createTempFile("freja-mpv-", ".png");
        try {
            try (Mpv mpv = Mpv.open(Map.of("vo", "null", "audio", "no", "pause", "yes", "config", "no"))) {
                mpv.load(video);
                mpv.screenshot(image);
            }
            byte[] bytes = Files.readAllBytes(image);
            assertTrue(bytes.length > 100, "The screenshot is empty");
            assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                    Arrays.copyOf(bytes, 8));
        } finally {
            Files.deleteIfExists(image);
        }
    }
}
