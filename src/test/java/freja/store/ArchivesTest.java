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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import freja.model.Folder;
import freja.model.PhotoFilter;
import freja.scan.LibraryScanner;
import freja.util.ArchiveSettings;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchivesTest {

    @Test
    void readsImagesFromZipAnd7z(@TempDir Path directory) throws Exception {
        byte[] jpeg = jpeg();
        Path zip = directory.resolve("album.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("notes.txt"));
            output.write("not an image".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/photo.jpg"));
            output.write(jpeg);
            output.closeEntry();
        }
        Path seven = directory.resolve("album.7z");
        try (SevenZOutputFile output = new SevenZOutputFile(seven.toFile())) {
            SevenZArchiveEntry entry = output.createArchiveEntry(directory.resolve("album.zip"), "photos/photo.jpg");
            output.putArchiveEntry(entry);
            output.write(jpeg);
            output.closeArchiveEntry();
        }

        assertTrue(Archives.isArchive(zip));
        assertTrue(Archives.isArchive(seven));
        assertEquals(Archives.ZIP, Archives.type(zip));
        assertEquals(Archives.SEVEN_Z, Archives.type(seven));

        assertEquals(1, Archives.entries(zip).size());
        assertEquals("nested/photo.jpg", Archives.entries(zip).getFirst().name());
        assertEquals(1, Archives.entries(seven).size());
        assertEquals("photos/photo.jpg", Archives.entries(seven).getFirst().name());

        assertEquals(jpeg.length, Archives.read(zip, Archives.ZIP, "nested/photo.jpg").length);
        assertEquals(jpeg.length, Archives.read(seven, Archives.SEVEN_Z, "photos/photo.jpg").length);
    }

    @Test
    void entryPathsStayInsideTheArchive(@TempDir Path directory) {
        Path archive = directory.resolve("album.zip");
        Path entry = Archives.entryPath(archive, "../../photos/photo.jpg");
        assertTrue(entry.startsWith(archive));
        assertFalse(entry.toString().contains(".."));
    }

    @Test
    void displayNameDropsOnlyTheArchiveExtension() {
        assertEquals("album", Archives.displayName(Path.of("photos", "album.zip")));
        assertEquals("album", Archives.displayName(Path.of("photos", "album.7z")));
        assertEquals("album.v2", Archives.displayName(Path.of("photos", "album.v2.ZIP")));
        assertEquals("album.jpg", Archives.displayName(Path.of("photos", "album.jpg")));
        assertEquals("photos", Archives.displayName(Path.of("photos")));
    }

    @Test
    void eachArchiveBecomesItsOwnFolder(@TempDir Path directory) throws Exception {
        Path first = directory.resolve("first.zip");
        Path second = directory.resolve("second.zip");
        writeZip(first, "a.jpg");
        writeZip(second, "b.jpg");
        boolean original = ArchiveSettings.scanArchives();
        try {
            ArchiveSettings.scanArchives(true);
            try (Library library = new Library(directory.resolve("library.db"));
                    LibraryScanner scanner = new LibraryScanner(library)) {
                scanner.addFolder(directory);
                assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)));

                List<Folder> folders = library.folders();
                assertEquals(3, folders.size(), "the root and one folder per archive");
                assertEquals(2, folders.stream().filter(folder -> !folder.explicit()).count());
                assertTrue(folders.stream().anyMatch(folder -> folder.name().equals("first")));
                assertTrue(folders.stream().anyMatch(folder -> folder.name().equals("second")));
                assertTrue(folders.stream().noneMatch(folder -> folder.name().endsWith(".zip")));
                assertEquals(1, library.photos(PhotoFilter.all()).stream()
                        .filter(photo -> photo.folderPath().endsWith("first.zip")).count());
                assertEquals(1, library.photos(PhotoFilter.all()).stream()
                        .filter(photo -> photo.folderPath().endsWith("second.zip")).count());

                // A repeated scan must not prune the folders which are not real directories.
                scanner.scanAll();
                assertTrue(scanner.awaitIdle(Duration.ofSeconds(30)));
                assertEquals(3, library.folders().size());
            }
        } finally {
            ArchiveSettings.scanArchives(original);
        }
    }

    private static void writeZip(Path file, String entry) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry(entry));
            output.write(jpeg());
            output.closeEntry();
        }
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color(120, 60 + x * 3, 40 + y * 6).getRGB());
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return bytes.toByteArray();
    }
}
