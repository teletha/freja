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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import freja.model.Photo;
import freja.util.PathUtil;

/**
 * Reads images inside ZIP and 7z archives without extracting them.
 * <p>
 * An archive is a container, not a photo. Its entries are indexed as virtual photos whose
 * {@link Photo#path()} is a synthetic path below the archive, so all reads of the original bytes
 * have to go through {@link #read(Photo)}. The identity of an entry is its cleaned name, which
 * keeps the catalog stable even when the archive was created on a machine with a different path
 * separator.
 * </p>
 */
public final class Archives {

    /** The type marker stored for ZIP entries. */
    public static final String ZIP = "ZIP";

    /** The type marker stored for 7z entries. */
    public static final String SEVEN_Z = "7Z";

    /** No single entry may be read into memory beyond this size. */
    private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;

    private Archives() {
    }

    /** One image entry of an archive. */
    public record Entry(String name, long size, long crc, long modifiedAt) {
    }

    /**
     * Returns the marker for an archive file, or null for any other path.
     *
     * @param path a file
     * @return {@link #ZIP}, {@link #SEVEN_Z}, or null
     */
    public static String type(Path path) {
        if (path == null) {
            return null;
        }
        String extension = PathUtil.extension(path);
        if (extension.equals("zip")) {
            return ZIP;
        }
        if (extension.equals("7z")) {
            return SEVEN_Z;
        }
        return null;
    }

    /**
     * Tests whether a path is an archive this application can read.
     *
     * @param path a file
     * @return true for ZIP and 7z
     */
    public static boolean isArchive(Path path) {
        return type(path) != null;
    }

    /**
     * Returns the name under which a folder path should be shown. An archive is registered as a
     * folder of its own, but the container suffix is a format detail rather than part of the name.
     *
     * @param path a directory or archive file
     * @return the last path element, without the archive extension
     */
    public static String displayName(Path path) {
        String name = PathUtil.folderName(path);
        if (!isArchive(path)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Lists the image entries of an archive. Directories, hidden metadata entries and entries whose
     * name does not carry a supported image extension are skipped.
     *
     * @param archive the archive file
     * @return the image entries, in archive order
     * @throws IOException when the archive cannot be opened
     */
    public static List<Entry> entries(Path archive) throws IOException {
        String type = type(archive);
        List<Entry> entries = new ArrayList<>();
        if (ZIP.equals(type)) {
            try (ZipFile zip = new ZipFile(archive)) {
                var all = zip.getEntries();
                while (all.hasMoreElements()) {
                    ZipArchiveEntry entry = all.nextElement();
                    String name = clean(entry.getName());
                    if (!entry.isDirectory() && !name.isBlank() && PathUtil.isImage(Path.of(name))) {
                        entries.add(new Entry(name, entry.getSize(), entry.getCrc(), time(entry.getTime())));
                    }
                }
            }
        } else if (SEVEN_Z.equals(type)) {
            try (SevenZFile seven = new SevenZFile(archive.toFile())) {
                for (SevenZArchiveEntry entry : seven.getEntries()) {
                    String name = clean(entry.getName());
                    if (!entry.isDirectory() && entry.hasStream() && !name.isBlank() && PathUtil.isImage(Path.of(name))) {
                        entries.add(new Entry(name, entry.getSize(), entry.getCrcValue(), time(entry.getLastModifiedTime())));
                    }
                }
            }
        }
        return entries;
    }

    /**
     * Reads one entry as bytes.
     *
     * @param archive the archive file
     * @param type the marker returned by {@link #type(Path)}
     * @param entryName the cleaned entry name
     * @return the uncompressed bytes
     * @throws IOException when the entry is missing, too large or unreadable
     */
    public static byte[] read(Path archive, String type, String entryName) throws IOException {
        if (ZIP.equals(type)) {
            try (ZipFile zip = new ZipFile(archive)) {
                ZipArchiveEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    // Some writers prefix names with "./"; match the cleaned form as a fallback.
                    var all = zip.getEntries();
                    while (all.hasMoreElements()) {
                        ZipArchiveEntry candidate = all.nextElement();
                        if (clean(candidate.getName()).equals(entryName)) {
                            entry = candidate;
                            break;
                        }
                    }
                }
                if (entry == null) {
                    throw new IOException("アーカイブにエントリがありません: " + entryName);
                }
                return read(entry.getSize(), zip.getInputStream(entry));
            }
        }
        if (SEVEN_Z.equals(type)) {
            try (SevenZFile seven = new SevenZFile(archive.toFile())) {
                for (SevenZArchiveEntry entry : seven.getEntries()) {
                    if (clean(entry.getName()).equals(entryName)) {
                        return read(entry.getSize(), seven.getInputStream(entry));
                    }
                }
                throw new IOException("アーカイブにエントリがありません: " + entryName);
            }
        }
        throw new IOException("対応していないアーカイブです: " + archive.getFileName());
    }

    /**
     * Reads the entry a virtual photo was indexed from.
     *
     * @param photo a photo, possibly an archive entry
     * @return the original bytes
     * @throws IOException when the source cannot be read
     */
    public static byte[] read(Photo photo) throws IOException {
        if (photo == null) {
            throw new IOException("写真がありません");
        }
        if (photo.archived()) {
            return read(Path.of(photo.archivePath()), photo.archiveType(), photo.entryName());
        }
        return java.nio.file.Files.readAllBytes(photo.path());
    }

    /**
     * Builds the synthetic path stored for an entry. The path is never opened on the file system;
     * its only jobs are to give the row a stable unique key and a readable name.
     *
     * @param archive the archive file
     * @param entryName the cleaned entry name
     * @return the synthetic path
     */
    public static Path entryPath(Path archive, String entryName) {
        return archive.resolve(clean(entryName).replace('/', java.io.File.separatorChar));
    }

    private static byte[] read(long size, InputStream input) throws IOException {
        if (size > MAX_ENTRY_BYTES) {
            throw new IOException("アーカイブ内の画像が大きすぎます");
        }
        try (InputStream stream = input) {
            byte[] bytes = stream.readAllBytes();
            if (bytes.length > MAX_ENTRY_BYTES) {
                throw new IOException("アーカイブ内の画像が大きすぎます");
            }
            return bytes;
        }
    }

    /** Turns an archive entry name into a portable, path-traversal-free relative name. */
    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : name.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                continue;
            }
            parts.add(part);
        }
        return String.join("/", parts);
    }

    private static long time(long millis) {
        return millis;
    }

    private static long time(java.nio.file.attribute.FileTime time) {
        return time == null ? 0 : time.toMillis();
    }

    /** Lower-cases the marker so a hand-edited catalog row still resolves. */
    static String normalizedType(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
