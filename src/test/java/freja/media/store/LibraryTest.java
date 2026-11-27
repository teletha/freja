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

import java.awt.EventQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import freja.media.Folder;
import freja.media.LibraryStats;
import freja.media.Photo;
import freja.media.PhotoFilter;
import freja.media.PhotoFilter.SortOrder;
import freja.media.Tag;
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
            // Search matches folder names only, not camera or caption fields.
            assertEquals(0, library.photos(PhotoFilter.all().withSearch("Canon")).size());
            assertEquals(0, library.photos(PhotoFilter.all().withSearch("trip")).size());
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
    void foldersAreOrderedByNameRatherThanPath() throws Exception {
        Path root = Files.createDirectories(temp.resolve("order"));
        Path zPathFirst = Files.createDirectories(root.resolve("a").resolve("zzz"));
        Path aPathSecond = Files.createDirectories(root.resolve("b").resolve("aaa"));

        try (Library library = new Library(temp.resolve("library.db"))) {
            library.addFolder(zPathFirst);
            library.addFolder(aPathSecond);
            assertEquals(List.of("aaa", "zzz"), library.folders().stream().map(Folder::name).toList());
        }
    }

    @Test
    void foldersCanBeDisabledAsScanTargets() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("toggle"));
        try (Library library = new Library(temp.resolve("library.db"))) {
            Folder added = library.addFolder(folder);
            assertTrue(added.scanEnabled());

            library.setFolderScanEnabled(added.id(), false);
            assertFalse(library.folder(added.id()).orElseThrow().scanEnabled());
            assertEquals(1, library.folders().size(), "disabling a folder must not delete it");
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

    @Test
    void oldPipelineThumbnailsAreRegeneratedOnNextScan() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("old-thumbs"));
        Path file = Files.write(folder.resolve("photo.jpg"), new byte[] {1});
        Path database = temp.resolve("thumbs.db");
        long id;
        try (Library library = new Library(database)) {
            Folder added = library.addFolder(folder);
            PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(10, 10, 1, Exif.Data.EMPTY, new byte[] {1}, 10, 10);
            id = library.savePhoto(added.id(), file, 1, 1234, analyzed, null);
        }

        // A library written by the previous pipeline has revision 7. Its WebP thumbnail is still
        // usable, so it is kept until the scanner decodes the file and replaces it.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath()); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE photos SET thumbnail_revision=7 WHERE id=" + id);
        }

        try (Library library = new Library(database)) {
            assertTrue(library.thumbnail(id).isPresent());
            assertFalse(library.isCurrent(PathUtil.key(file), 1, 1234));
        }
    }

    @Test
    void tagsCanBeAssignedRenamedFilteredAndRemoved() throws Exception {
        Path first = Files.createDirectories(temp.resolve("tagged-first"));
        Path second = Files.createDirectories(temp.resolve("tagged-second"));
        Path a = Files.write(first.resolve("a.jpg"), new byte[] {1});
        Path b = Files.write(second.resolve("b.jpg"), new byte[] {2});
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(100, 100, 1, Exif.Data.EMPTY,
                new byte[] {1}, 100, 100);
        try (Library library = new Library(temp.resolve("tags.db"))) {
            Folder f1 = library.addFolder(first);
            Folder f2 = library.addFolder(second);
            library.savePhoto(f1.id(), a, 1, 1, analyzed, null);
            library.savePhoto(f2.id(), b, 1, 1, analyzed, null);

            library.setFolderTags(f1.id(), List.of("旅行", "家族"));
            library.setFolderTags(f2.id(), List.of("旅行"));

            assertEquals(List.of("家族", "旅行"), library.folder(f1.id()).orElseThrow().tags());
            long travel = library.tags().stream().filter(t -> t.name().equals("旅行")).findFirst().orElseThrow().id();
            long family = library.tags().stream().filter(t -> t.name().equals("家族")).findFirst().orElseThrow().id();
            assertEquals(2, library.tags().stream().filter(t -> t.id() == travel).findFirst().orElseThrow().folderCount());
            assertEquals(2, library.photos(PhotoFilter.all().withTags(List.of(travel))).size());
            assertEquals(1, library.photos(PhotoFilter.all().withTags(List.of(family))).size());
            assertEquals(2, library.photos(PhotoFilter.all().withTags(List.of(travel, family))).size());

            library.renameTag(family, "家族アルバム");
            assertEquals("家族アルバム", library.tags().stream().filter(t -> t.id() == family).findFirst().orElseThrow().name());
            assertEquals(2, library.bulkRenameTags(List.of(travel, family), "旅行", "旅行記"));
            assertEquals("旅行記", library.tags().stream().filter(t -> t.id() == travel).findFirst().orElseThrow().name());

            library.deleteTags(List.of(family));
            assertEquals(List.of("旅行記"), library.folder(f1.id()).orElseThrow().tags());
            assertTrue(library.tags().stream().noneMatch(t -> t.name().equals("家族アルバム")));
        }
    }

    @Test
    void clearingFoldersRemovesCataloguedPhotosButKeepsTags() throws Exception {
        Path root = Files.createDirectories(temp.resolve("to-clear"));
        try (Library library = new Library(temp.resolve("clear.db"))) {
            Folder folder = library.addFolder(root);
            library.savePhoto(folder.id(), root.resolve("a.jpg"), 1, 1,
                    new PhotoImporter.AnalyzedPhoto(100, 100, 1, Exif.Data.EMPTY, new byte[] {1}, 100, 100), null);
            library.addTag("旅行");

            library.clearFolders();

            assertTrue(library.folders().isEmpty());
            assertTrue(library.photos(PhotoFilter.all()).isEmpty());
            assertEquals(1, library.tags().size());
        }
    }

    @Test
    void tagLinksAreOneWayAndExpandSearch() throws Exception {
        Path first = Files.createDirectories(temp.resolve("link-first"));
        Path second = Files.createDirectories(temp.resolve("link-second"));
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(100, 100, 1, Exif.Data.EMPTY,
                new byte[] {1}, 100, 100);
        try (Library library = new Library(temp.resolve("links.db"))) {
            Folder f1 = library.addFolder(first);
            Folder f2 = library.addFolder(second);
            library.savePhoto(f1.id(), first.resolve("a.jpg"), 1, 1, analyzed, null);
            library.savePhoto(f2.id(), second.resolve("b.jpg"), 1, 1, analyzed, null);

            long inoue = library.addTag("井上和彦");
            long fujiwara = library.addTag("藤原カムイ");
            long manga = library.addTag("漫画");
            library.setFolderTags(f1.id(), List.of("井上和彦"));
            library.setFolderTags(f2.id(), List.of("藤原カムイ"));
            library.setTagLinks(inoue, List.of(manga));
            library.setTagLinks(fujiwara, List.of(manga));

            assertEquals(2, library.photos(PhotoFilter.all().withTags(List.of(manga))).size());
            assertEquals(1, library.photos(PhotoFilter.all().withTags(List.of(inoue))).size());
            assertEquals(2, library.photos(PhotoFilter.all().withSearch("漫画")).size());
            assertEquals(1, library.photos(PhotoFilter.all().withSearch("井上")).size());
            assertEquals(List.of(manga), library.linkedTargets(inoue).stream().map(Tag::id).toList());
            assertEquals(2, library.linkedSources(manga).size());

            library.deleteTags(List.of(inoue));
            assertEquals(1, library.linkedSources(manga).size());
            assertEquals(1, library.photos(PhotoFilter.all().withTags(List.of(manga))).size());
        }
    }

    @Test
    void smartTagsComeFromLeadingBracketsInFolderNames() throws Exception {
        Path root = Files.createDirectories(temp.resolve("(Trip) 2024"));
        try (Library library = new Library(temp.resolve("smart-tags.db"))) {
            Folder folder = library.addFolder(root);
            assertEquals(List.of("Trip"), folder.tags());

            library.renameFolder(folder.id(), "【家族】アルバム");
            assertEquals(List.of("Trip", "家族"), library.folder(folder.id()).orElseThrow().tags());
        }
    }

    @Test
    void rejectsDatabaseAccessOnTheUiThread() throws Exception {
        try (Library library = new Library(temp.resolve("guard.db"))) {
            library.setUiThreadCheck(EventQueue::isDispatchThread);
            // A background caller is unaffected.
            assertDoesNotThrow(() -> library.tags());

            AtomicReference<Throwable> failure = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    library.tags();
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            assertTrue(failure.get() instanceof IllegalStateException, "EDT access must fail fast");
        }
    }

    @Test
    void removesFolderTreesInOneBatch() throws Exception {
        Path root = Files.createDirectories(temp.resolve("tree"));
        Path child = Files.createDirectories(root.resolve("child"));
        try (Library library = new Library(temp.resolve("tree.db"))) {
            Folder parent = library.addFolder(root);
            library.ensureFolder(child);
            assertEquals(2, library.folders().size());

            library.removeFolders(List.of(parent.id()));
            assertTrue(library.folders().isEmpty());
        }
    }

    @Test
    void batchesDeletesAndUpdates() throws Exception {
        Path folder = Files.createDirectories(temp.resolve("batch"));
        Exif.Data exif = Exif.Data.EMPTY;
        PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(100, 80, 0, exif, new byte[] {1, 2, 3}, 40, 30);
        try (Library library = new Library(temp.resolve("batch.db"))) {
            Folder added = library.addFolder(folder);
            long first = library.savePhoto(added.id(), Files.write(folder.resolve("a.jpg"), new byte[] {1}), 1, 1, analyzed, "scan");
            long second = library.savePhoto(added.id(), Files.write(folder.resolve("b.jpg"), new byte[] {2}), 1, 1, analyzed, "scan");
            long third = library.savePhoto(added.id(), Files.write(folder.resolve("c.jpg"), new byte[] {3}), 1, 1, analyzed, "scan");
            assertEquals(3, library.photos(PhotoFilter.all()).size());

            library.setFavorites(List.of(first, second), true);
            assertEquals(2, library.photos(PhotoFilter.favorites()).size());
            library.setFolderScanEnabled(List.of(added.id()), false);
            assertFalse(library.folder(added.id()).orElseThrow().scanEnabled());

            assertEquals(2, library.deletePhotos(List.of(first, third)));
            assertTrue(library.photo(first).isEmpty());
            assertTrue(library.thumbnail(first).isEmpty());
            assertTrue(library.photo(second).isPresent());
            assertTrue(library.thumbnail(second).isPresent());
            assertEquals(0, library.deletePhotos(List.of()));
            assertEquals(0, library.deletePhotos(null));
        }
    }
}
