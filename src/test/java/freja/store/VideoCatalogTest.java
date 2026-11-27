/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import freja.model.Photo;

class VideoCatalogTest {

    @TempDir
    Path temp;

    @Test
    void storesDurationAndKeepsItThroughCopyOperations() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("videos"));
        Path file = Files.write(folder.resolve("clip.mp4"), new byte[] {1, 2, 3});
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(1920, 1080,
                System.currentTimeMillis(), Exif.Data.EMPTY, new byte[] {9, 8, 7}, 220, 124, 5055);

        try (Library library = new Library(temp.resolve("library.db"))) {
            long folderId = library.addFolder(folder).id();
            long id = library.savePhoto(folderId, file, 3, 1234, analyzed, "scan-1");
            Photo photo = library.photo(id).orElseThrow();

            assertTrue(photo.isVideo());
            assertEquals(1920, photo.width());
            assertEquals(1080, photo.height());
            assertEquals(5055, photo.durationMillis());
            assertEquals("0:05", photo.durationLabel());
            assertEquals(5055, photo.withFavorite(true).durationMillis());
            assertEquals(5055, photo.withRating(3).durationMillis());
            assertEquals(5055, photo.withCaption("clip").durationMillis());
        }
    }
}
