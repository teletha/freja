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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MpvNativeTest {

    @Test
    void sha256MatchesKnownDigest() throws IOException {
        Path file = Files.createTempFile("freja-mpv-", ".txt");
        try {
            Files.writeString(file, "abc");
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    MpvNative.sha256(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
