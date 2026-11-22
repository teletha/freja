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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.PhotoFilter.SortOrder;
import freja.util.PathUtil;

class LibraryTest {

    @TempDir
    Path temp;

    @Test
    void storesAndFiltersPhotos() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("photos"));
        Path file = Files.write(folder.resolve("sample.jpg"), new byte[] {1, 2, 3});
        long captured = System.currentTimeMillis() - 100_000;
        Exif.Data exif = new Exif.Data("Canon", "EOS R5", "RF 50mm F1.2", captured, 6, "1/250 秒", 2.8, 400, 50.0);
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(6000, 4000, captured, exif, new byte[] {9, 8, 7}, 420, 280);

        try (Library library = new Library(temp.resolve("library.db"))) {
            Folder added = library.addFolder(folder);
            assertEquals(SortOrder.NAME, added.sort());
            assertEquals(1, library.folders().size());
            library.setFolderSort(added.id(), SortOrder.DATE_OLDEST);
            assertEquals(SortOrder.DATE_OLDEST, library.folders().get(0).sort());
            long id = library.savePhoto(added.id(), file, 3, 1234, analyzed, "scan-1");
            assertTrue(id > 0);
            assertEquals(id, library.savePhoto(added.id(), file, 3, 1234, analyzed, "scan-1"));

            Photo photo = library.photo(id).orElseThrow();
            assertEquals("sample.jpg", photo.fileName());
            assertEquals("jpg", photo.extension());
            assertEquals(6000, photo.width());
            assertEquals(4000, photo.height());
            assertEquals(4000, photo.displayWidth());
            assertEquals(6000, photo.displayHeight());
            assertEquals("Canon EOS R5", photo.cameraLabel());
            assertEquals("RF 50mm F1.2", photo.lens());
            assertEquals(400, photo.iso());
            assertEquals(2.8, photo.aperture(), 0.001);
            assertEquals(50.0, photo.focalLength(), 0.001);
            assertEquals(6, photo.orientation());
            assertEquals(captured, photo.capturedAt());
            assertEquals(1, library.photos(PhotoFilter.all()).size());
            assertTrue(library.thumbnail(id).isPresent());

            library.setFavorite(id, true);
            library.setRating(id, 4);
            library.setCaption(id, "Summer trip");
            photo = library.photo(id).orElseThrow();
            assertTrue(photo.favorite());
            assertEquals(4, photo.rating());
            assertEquals("Summer trip", photo.caption());
            assertEquals(1, library.photos(PhotoFilter.favorites()).size());
            assertEquals(1, library.photos(PhotoFilter.all().withSearch("Canon")).size());
            assertEquals(1, library.photos(PhotoFilter.all().withSearch("trip")).size());
            assertEquals(1, library.photos(PhotoFilter.all().withSearch("photos")).size());
            assertEquals(0, library.photos(PhotoFilter.all().withSearch("missing")).size());
            assertEquals(1, library.photos(PhotoFilter.folder(added.id())).size());

            LibraryStats stats = library.stats();
            assertEquals(1, stats.photos());
            assertEquals(1, stats.favorites());
            assertEquals(1, stats.recent());
            assertEquals(1, stats.folders());
            assertEquals(3, stats.bytes());

            library.setting("theme", "light");
            assertEquals("light", library.setting("theme").orElseThrow());
        }
    }

    @Test
    void reimportKeepsUserPropertiesAndPrunesMissingFiles() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("photos"));
        Path first = Files.write(folder.resolve("first.jpg"), new byte[] {1});
        Path second = Files.write(folder.resolve("second.jpg"), new byte[] {2});
        Path database = temp.resolve("library.db");
        long captured = System.currentTimeMillis();
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(100, 80, captured, Exif.Data.EMPTY, new byte[] {1}, 100, 80);

        long firstId;
        long secondId;
        try (Library library = new Library(database)) {
            Folder added = library.addFolder(folder);
            firstId = library.savePhoto(added.id(), first, 1, 10, analyzed, "scan-1");
            secondId = library.savePhoto(added.id(), second, 1, 20, analyzed, "scan-1");
            library.setFavorite(firstId, true);
            library.setCaption(firstId, "keep me");
            assertEquals(0, library.finishScan(added.id(), "scan-1"));

            // A full scan that no longer sees the second file removes only that row.
            library.touch(freja.util.PathUtil.key(first), "scan-2");
            assertEquals(1, library.finishScan(added.id(), "scan-2"));
            assertTrue(library.photo(firstId).isPresent());
            assertTrue(library.photo(secondId).isEmpty());

            // Re-importing the first file must preserve the favorite and caption.
            library.savePhoto(added.id(), first, 1, 11, analyzed, "scan-2");
            Photo photo = library.photo(firstId).orElseThrow();
            assertTrue(photo.favorite());
            assertEquals("keep me", photo.caption());
        }

        try (Library reopened = new Library(database)) {
            assertEquals(1, reopened.photos(PhotoFilter.all()).size());
            assertTrue(reopened.photo(firstId).orElseThrow().favorite());
        }
    }

    @Test
    void managesNestedFoldersAndRejectsANewParentRoot() throws Exception {
        Path root = Files.createDirectories(temp.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        try (Library library = new Library(temp.resolve("library.db"))) {
            Folder added = library.addFolder(root);
            assertTrue(added.explicit());
            Folder managed = library.addFolder(nested);
            assertFalse(managed.explicit());
            assertEquals(2, library.folders().size());
            assertThrows(IllegalArgumentException.class, () -> library.addFolder(temp));
            assertThrows(IllegalArgumentException.class, () -> library.addFolder(temp.resolve("missing")));
        }
    }

    @Test
    void removesPhotosBelowDeletedDirectory() throws Exception {
        Path root = Files.createDirectories(temp.resolve("root"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path file = Files.write(nested.resolve("photo.jpg"), new byte[] {1});
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(10, 10, 1, Exif.Data.EMPTY, new byte[] {1}, 10, 10);
        try (Library library = new Library(temp.resolve("library.db"))) {
            Folder added = library.addFolder(root);
            long id = library.savePhoto(added.id(), file, 1, 1, analyzed, null);
            assertEquals(1, library.deleteUnder(added.id(), nested));
            assertTrue(library.photo(id).isEmpty());
        }
    }

    @Test
    void upgradesAnOlderFolderTableWithTheDefaultSortOrder() throws Exception {
        Path root = Files.createDirectories(temp.resolve("photos"));
        Path database = temp.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        path TEXT NOT NULL,
                        path_key TEXT NOT NULL UNIQUE,
                        added_at INTEGER NOT NULL,
                        last_scan_at INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("INSERT INTO folders(path, path_key, added_at) VALUES('" + root.toString().replace("'", "''") + "','" + PathUtil.key(root).replace("'", "''") + "',1)");
        }
        try (Library library = new Library(database)) {
            assertEquals(1, library.folders().size());
            assertEquals(SortOrder.NAME, library.folders().get(0).sort());
            assertTrue(library.folders().get(0).explicit());
            assertEquals("photos", library.folders().get(0).name());
        }
    }

    @Test
    void olderThumbnailsAreDiscardedForRegeneration() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("photos"));
        Path file = Files.write(folder.resolve("photo.jpg"), new byte[] {1});
        Path database = temp.resolve("thumbs.db");
        long id;
        long size;
        long modified = 1234;
        try (Library library = new Library(database)) {
            Folder added = library.addFolder(folder);
            PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(10, 10, 1, Exif.Data.EMPTY, new byte[] {1}, 10, 10);
            id = library.savePhoto(added.id(), file, 1, modified, analyzed, null);
            size = 1;
            assertTrue(library.thumbnail(id).isPresent());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE photos SET thumbnail_revision=2 WHERE id=" + id);
        }

        try (Library library = new Library(database)) {
            assertTrue(library.thumbnail(id).isEmpty());
            assertFalse(library.isCurrent(PathUtil.key(file), size, modified));
        }
    }
}
