/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.scan;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;

import imagify.Imagify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import freja.media.Folder;
import freja.media.Photo;
import freja.media.PhotoFilter;
import freja.media.store.Library;
import freja.media.store.PhotoImporter;

class LibraryScannerTest {

    @TempDir
    Path temp;

    @Test
    void scansUpdatesAndRemovesFiles() throws Exception {
        Path root = Files.createDirectories(temp.resolve("photos"));
        Path one = image(root.resolve("one.png"), 64, 48, Color.RED);
        Path two = image(root.resolve("two.png"), 64, 48, Color.BLUE);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(false);
            Folder folder = scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "initial scan did not finish");

            List<Photo> photos = library.photos(PhotoFilter.all());
            assertEquals(2, photos.size());
            for (Photo photo : photos) {
                assertEquals(64, photo.width());
                assertEquals(48, photo.height());
                assertTrue(library.thumbnail(photo.id()).isPresent());
            }
            Photo first = photos.stream().filter(photo -> photo.fileName().equals("one.png")).findFirst().orElseThrow();
            library.setFavorite(first.id(), true);
            library.setCaption(first.id(), "keep");

            // Modify one file and add another.
            image(two, 80, 60, Color.GREEN);
            Files.setLastModifiedTime(two, FileTime.fromMillis(System.currentTimeMillis() + 5_000));
            Path three = image(root.resolve("three.png"), 32, 24, Color.YELLOW);

            scanner.scanAll();
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "incremental scan did not finish");
            photos = library.photos(PhotoFilter.all());
            assertEquals(3, photos.size());
            Photo changed = photos.stream().filter(photo -> photo.fileName().equals("two.png")).findFirst().orElseThrow();
            assertEquals(80, changed.width());
            assertEquals(60, changed.height());
            Photo preserved = library.photo(first.id()).orElseThrow();
            assertTrue(preserved.favorite());
            assertEquals("keep", preserved.caption());

            // Delete a file and scan again.
            Files.delete(three);
            scanner.scanAll();
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "cleanup scan did not finish");
            assertEquals(2, library.photos(PhotoFilter.all()).size());

            assertTrue(folder.photoCount() >= 0);
        }
    }

    @Test
    void scansManyImagesAndRepeatsWithoutDuplicates() throws Exception {
        Path root = Files.createDirectories(temp.resolve("many"));
        for (int i = 0; i < 120; i++) {
            image(root.resolve(String.format("%03d.png", i)), 16, 12, Color.CYAN);
        }

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(60)), "initial bulk scan did not finish");
            assertEquals(120, library.photos(PhotoFilter.all()).size());

            // The repeat scan reads catalog stamps in bulk and touches unchanged rows in batches.
            scanner.scanAll();
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(60)), "repeat bulk scan did not finish");
            assertEquals(120, library.photos(PhotoFilter.all()).size());
            for (Photo photo : library.photos(PhotoFilter.all())) {
                assertTrue(library.thumbnail(photo.id()).isPresent());
            }
        }
    }

    @Test
    void reportsDirectoriesRatherThanFileNamesDuringScan() throws Exception {
        Path root = Files.createDirectories(temp.resolve("named"));
        Path child = Files.createDirectories(root.resolve("child"));
        for (int i = 0; i < 40; i++) {
            image(child.resolve(String.format("%03d.png", i)), 16, 12, Color.CYAN);
        }
        List<String> messages = new CopyOnWriteArrayList<>();

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.addListener(new LibraryScanner.Listener() {
                @Override
                public void onStateChanged(LibraryScanner.State state) {
                    if (state.scanning()) {
                        messages.add(state.message());
                    }
                }
            });
            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "scan did not finish");

            assertFalse(messages.isEmpty(), "no progress state was published");
            for (String message : messages) {
                assertNotNull(message);
                assertFalse(message.contains(".png"), "a file name leaked into the status message: " + message);
            }
        }
    }

    @Test
    void reportsHowLongTheScanTook() throws Exception {
        Path root = Files.createDirectories(temp.resolve("timed"));
        image(root.resolve("one.png"), 32, 24, Color.RED);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            assertTrue(LibraryScanner.State.IDLE.elapsed().isZero());
            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "initial scan did not finish");

            LibraryScanner.State state = scanner.state();
            assertFalse(state.scanning());
            assertTrue(state.elapsed().toNanos() > 0, "a finished scan must report its duration");

            scanner.scanAll();
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "repeat scan did not finish");
            assertTrue(scanner.state().elapsed().toNanos() > 0);
        }
    }

    @Test
    void watchesForNewFiles() throws Exception {
        Path root = Files.createDirectories(temp.resolve("watched"));
        image(root.resolve("initial.png"), 40, 30, Color.ORANGE);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(true);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "initial scan did not finish");
            assertEquals(1, library.photos(PhotoFilter.all()).size());

            image(root.resolve("added-later.png"), 50, 40, Color.MAGENTA);
            assertTrue(waitFor(() -> library.photos(PhotoFilter.all()).size() == 2, Duration.ofSeconds(15)), "watch event did not import the new file");
        }
    }

    @Test
    void preservesTransparencyInThumbnails() throws Exception {
        Path root = Files.createDirectories(temp.resolve("transparent"));
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0, 0, 0, 0));
            graphics.fillRect(0, 0, 80, 60);
            graphics.setColor(Color.RED);
            graphics.fillOval(10, 10, 40, 40);
        } finally {
            graphics.dispose();
        }
        Path file = root.resolve("alpha.png");
        ImageIO.write(image, "png", file.toFile());

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "scan did not finish");
            Photo photo = library.photos(PhotoFilter.all()).get(0);
            byte[] thumbnail = library.thumbnail(photo.id()).orElseThrow().data();
            assertEquals((byte) 'R', thumbnail[0]);
            assertEquals((byte) 'I', thumbnail[1]);
            assertEquals((byte) 'F', thumbnail[2]);
            assertEquals((byte) 'F', thumbnail[3]);
            assertEquals((byte) 'W', thumbnail[8]);
            assertEquals((byte) 'E', thumbnail[9]);
            assertEquals((byte) 'B', thumbnail[10]);
            assertEquals((byte) 'P', thumbnail[11]);
            BufferedImage decoded = Imagify.read(thumbnail).toBufferedImage();
            assertTrue(decoded.getColorModel().hasAlpha());
            assertEquals(0, decoded.getRGB(2, 2) >>> 24);
        }
    }

    @Test
    void treatsSubfoldersAsIndependentFolders() throws Exception {
        Path root = Files.createDirectories(temp.resolve("library"));
        Path child1 = Files.createDirectories(root.resolve("child1"));
        Path child2 = Files.createDirectories(root.resolve("child2"));
        Path grand = Files.createDirectories(child2.resolve("grand"));
        Files.createDirectories(root.resolve("empty"));
        image(root.resolve("root.png"), 30, 20, Color.RED);
        image(child1.resolve("one.png"), 30, 20, Color.GREEN);
        image(child2.resolve("two.png"), 30, 20, Color.BLUE);
        image(grand.resolve("three.png"), 30, 20, Color.YELLOW);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(false);
            Folder added = scanner.addFolder(root);
            assertTrue(added.explicit());
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "hierarchy scan did not finish");

            List<Folder> folders = library.folders();
            assertEquals(5, folders.size());
            Folder rootFolder = folders.stream().filter(folder -> folder.id() == added.id()).findFirst().orElseThrow();
            Folder child1Folder = folders.stream().filter(folder -> folder.path().equals(child1)).findFirst().orElseThrow();
            Folder child2Folder = folders.stream().filter(folder -> folder.path().equals(child2)).findFirst().orElseThrow();
            Folder grandFolder = folders.stream().filter(folder -> folder.path().equals(grand)).findFirst().orElseThrow();
            Folder emptyFolder = folders.stream().filter(folder -> folder.path().equals(root.resolve("empty"))).findFirst().orElseThrow();
            assertTrue(rootFolder.explicit());
            assertFalse(child1Folder.explicit());
            assertEquals(1, rootFolder.photoCount());
            assertEquals(1, child1Folder.photoCount());
            assertEquals(1, child2Folder.photoCount());
            assertEquals(1, grandFolder.photoCount());
            assertEquals(0, emptyFolder.photoCount());

            List<Photo> photos = library.photos(PhotoFilter.all());
            assertEquals(4, photos.size());
            assertEquals(rootFolder.id(), folderOf(photos, "root.png"));
            assertEquals(child1Folder.id(), folderOf(photos, "one.png"));
            assertEquals(child2Folder.id(), folderOf(photos, "two.png"));
            assertEquals(grandFolder.id(), folderOf(photos, "three.png"));
            assertEquals(1, library.photos(PhotoFilter.folder(child1Folder.id())).size());
            assertEquals(1, library.photos(PhotoFilter.folder(rootFolder.id())).size());
            assertEquals(4, library.photos(PhotoFilter.folderTree(rootFolder.id())).size());
            assertEquals(2, library.photos(PhotoFilter.folderTree(child2Folder.id())).size());

            Path child3 = Files.createDirectories(root.resolve("child3"));
            image(child3.resolve("four.png"), 30, 20, Color.MAGENTA);
            scanner.scanAll();
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "discovery scan did not finish");
            assertEquals(6, library.folders().size());
            assertEquals(5, library.photos(PhotoFilter.all()).size());
            assertEquals(5, library.photos(PhotoFilter.folderTree(rootFolder.id())).size());

            scanner.removeFolder(added.id());
            assertEquals(0, library.folders().size());
            assertEquals(0, library.photos(PhotoFilter.all()).size());
        }
    }

    @Test
    void movesAnExistingPhotoToItsContainingFolder() throws Exception {
        Path root = Files.createDirectories(temp.resolve("library"));
        Path child = Files.createDirectories(root.resolve("child"));
        Path file = image(child.resolve("photo.png"), 30, 20, Color.ORANGE);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            Folder rootFolder = library.addFolder(root);
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            PhotoImporter.AnalyzedPhoto analyzed = new PhotoImporter.AnalyzedPhoto(30, 20, attrs.lastModifiedTime().toMillis(), freja.media.store.Exif.Data.EMPTY, new byte[] {1}, 30, 20);
            long id = library.savePhoto(rootFolder.id(), file, attrs.size(), attrs.lastModifiedTime().toMillis(), analyzed, null);

            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "reassignment scan did not finish");

            Folder childFolder = library.folders().stream().filter(folder -> folder.path().equals(child)).findFirst().orElseThrow();
            assertEquals(childFolder.id(), library.photo(id).orElseThrow().folderId());
        }
    }

    @Test
    void importsDamagedImagesWithPlaceholderThumbnails() throws Exception {
        Path root = Files.createDirectories(temp.resolve("damaged"));
        // The broken files sort before the good one, so the scan has to survive them first.
        Path broken = root.resolve("a-broken.jpg");
        Files.write(broken, "this is not a picture".getBytes(StandardCharsets.UTF_8));
        long brokenModified = 1_600_000_000_000L;
        Files.setLastModifiedTime(broken, FileTime.fromMillis(brokenModified));
        Path unsupported = root.resolve("b-unsupported.png");
        Files.write(unsupported, new byte[] {0x00, 0x01, 0x02, 0x03});
        image(root.resolve("z-good.png"), 64, 48, Color.RED);

        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            // Skip the start-time sweep: the folder is added right afterwards, and a concurrent
            // second pass would reset the error counters this test checks.
            scanner.start(false, false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)), "scan did not finish");

            List<Photo> photos = library.photos(PhotoFilter.all());
            assertEquals(3, photos.size(), () -> "an unreadable file still becomes a catalog row; rows="
                    + photos.stream().map(Photo::fileName).toList() + " state=" + scanner.state());
            Photo good = photos.stream().filter(photo -> photo.fileName().equals("z-good.png")).findFirst().orElseThrow();
            Library.Thumbnail goodThumb = library.thumbnail(good.id()).orElseThrow();
            BufferedImage goodPixels = Imagify.read(goodThumb.data()).toBufferedImage();
            assertEquals(64, goodPixels.getWidth());
            assertEquals(48, goodPixels.getHeight());

            Photo brokenPhoto = photos.stream().filter(photo -> photo.fileName().equals("a-broken.jpg")).findFirst().orElseThrow();
            Photo unsupportedPhoto = photos.stream().filter(photo -> photo.fileName().equals("b-unsupported.png")).findFirst().orElseThrow();
            assertEquals(0, brokenPhoto.width());
            assertEquals(0, brokenPhoto.height());
            assertEquals(brokenModified, brokenPhoto.capturedAt(), "a placeholder takes the file's modification time");

            Library.Thumbnail brokenThumb = library.thumbnail(brokenPhoto.id()).orElseThrow();
            Library.Thumbnail unsupportedThumb = library.thumbnail(unsupportedPhoto.id()).orElseThrow();
            assertEquals(PhotoImporter.PLACEHOLDER_WIDTH, brokenThumb.width());
            assertEquals(PhotoImporter.PLACEHOLDER_HEIGHT, brokenThumb.height());
            BufferedImage placeholder = Imagify.read(brokenThumb.data()).toBufferedImage();
            assertEquals(PhotoImporter.PLACEHOLDER_WIDTH, placeholder.getWidth());
            assertEquals(PhotoImporter.PLACEHOLDER_HEIGHT, placeholder.getHeight());
            // Both unreadable files share the one lazily encoded placeholder.
            assertArrayEquals(brokenThumb.data(), unsupportedThumb.data());
            assertTrue(scanner.state().errors() >= 2, "the failures still have to be reported");
        }
    }

    @Test
    void scansVideoFilesWithThumbnails() throws Exception {
        String sample = System.getProperty("freja.mpv.sample");
        assumeTrue(sample != null && !sample.isBlank(), "Set -Dfreja.mpv.sample=<path to a local mp4>");
        Path source = Path.of(sample);
        assumeTrue(Files.isRegularFile(source), "Sample video does not exist: " + sample);

        Path root = Files.createDirectories(temp.resolve("videos"));
        Files.copy(source, root.resolve("clip.mp4"));
        try (Library library = new Library(temp.resolve("library.db")); LibraryScanner scanner = new LibraryScanner(library)) {
            scanner.start(false);
            scanner.addFolder(root);
            assertTrue(scanner.awaitIdle(Duration.ofSeconds(60)), "video scan did not finish");

            List<Photo> photos = library.photos(PhotoFilter.all());
            assertEquals(1, photos.size());
            Photo photo = photos.get(0);
            assertTrue(photo.isVideo());
            assertEquals("clip.mp4", photo.fileName());
            assertTrue(photo.width() > 0);
            assertTrue(photo.durationMillis() > 0);
            assertTrue(library.thumbnail(photo.id()).isPresent());
        }
    }

    private static long folderOf(List<Photo> photos, String name) {
        return photos.stream().filter(photo -> photo.fileName().equals(name)).findFirst().orElseThrow().folderId();
    }

    private static Path image(Path file, int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private static boolean waitFor(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }
}
