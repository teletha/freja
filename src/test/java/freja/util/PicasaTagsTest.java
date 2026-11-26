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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PicasaTagsTest {

    @TempDir
    Path folder;

    @Test
    void readsAlbumDescriptionAsTags() throws IOException {
        Files.writeString(folder.resolve(".picasa.ini"), """
                [Picasa]
                version=9
                description=旅行 家族 ２０２４
                """);
        assertEquals(List.of("旅行", "家族", "2024"), PicasaTags.fromFolder(folder));
    }

    @Test
    void ignoresOtherSectionsAndDuplicates() throws IOException {
        Files.writeString(folder.resolve(".picasa.ini"), """
                [Picasa]
                description=旅行  家族 旅行
                [IMG_0001.JPG]
                description=違う タグ
                """);
        assertEquals(List.of("旅行", "家族"), PicasaTags.fromFolder(folder));
    }

    @Test
    void ignoresFoldersWithoutDescription() throws IOException {
        assertTrue(PicasaTags.fromFolder(folder).isEmpty());
        Files.writeString(folder.resolve(".picasa.ini"), "[Picasa]\nversion=9\n");
        assertTrue(PicasaTags.fromFolder(folder).isEmpty());
    }
}
