/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.mpv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import kiss.I;

/**
 * Downloads and loads the libmpv native library.
 * <p>
 * Freja does not ship the binary. On first use it is downloaded from the official mpv release into
 * the application data directory and verified against a pinned SHA-256. A failed download is not
 * fatal: video support stays unavailable for the rest of the process and is retried on next start.
 * Use {@code -Dfreja.mpv.path=<directory>} to point at an already extracted {@code libmpv-2.dll}.
 * </p>
 */
public final class MpvNative {

    /** The pinned mpv release. */
    public static final String VERSION = "0.42.0";

    /** The native library file name inside the release archive. */
    public static final String LIBRARY = "libmpv-2.dll";

    private static final String ASSET = "libmpv-v0.42.0-x86_64-w64-mingw32-lgpl.zip";

    private static final String URL = "https://github.com/mpv-player/mpv/releases/download/v0.42.0/" + ASSET;

    private static final String ARCHIVE_SHA256 = "8e5f77430efc53ef175804ba6618527e3af0448a0dcabf04d3cc72659d1a6fce";

    private static final String LIBRARY_SHA256 = "94fb87247732c59915cd59808bc582e843a71c22214b7b6c1e5563aa8149cef4";

    private static final long LIBRARY_SIZE = 125_790_720L;

    private static final Object LOCK = new Object();

    private static volatile Path library;

    private static volatile Throwable failure;

    private static volatile MpvBindings bindings;

    private MpvNative() {
    }

    /**
     * Returns the native bindings, downloading and linking the library on first use.
     *
     * @return the bindings
     * @throws UnavailableException when the library cannot be obtained or linked
     */
    public static MpvBindings bindings() {
        MpvBindings current = bindings;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (bindings != null) {
                return bindings;
            }
            // A failed download is latched for this process. Retrying it for every video in a scan
            // would turn a dead network into a long sequence of full download attempts.
            if (failure != null) {
                throw new UnavailableException("libmpv " + VERSION + " is not available", failure);
            }
            try {
                bindings = new MpvBindings(library());
                return bindings;
            } catch (IOException | RuntimeException | LinkageError e) {
                failure = e;
                I.warn("freja", new IOException("libmpv " + VERSION + " is not available", e));
                throw new UnavailableException("libmpv " + VERSION + " is not available", e);
            }
        }
    }

    /**
     * Tests whether the native library can be used.
     *
     * @return true when libmpv is linked and ready
     */
    public static boolean isAvailable() {
        try {
            bindings();
            return true;
        } catch (UnavailableException e) {
            return false;
        }
    }

    /**
     * Returns the last loading failure, if any.
     *
     * @return the failure or null
     */
    public static Throwable failure() {
        return failure;
    }

    /**
     * Returns the path of the native library, downloading it if needed.
     *
     * @return the absolute path of {@code libmpv-2.dll}
     * @throws IOException when the download, verification or extraction fails
     */
    static Path library() throws IOException {
        if (library != null) {
            return library;
        }
        synchronized (LOCK) {
            if (library != null) {
                return library;
            }

            String override = System.getProperty("freja.mpv.path");
            if (override != null && !override.isBlank()) {
                Path given = Path.of(override).toAbsolutePath().normalize();
                Path dll = LIBRARY.equals(given.getFileName().toString()) ? given : given.resolve(LIBRARY);
                if (!Files.isRegularFile(dll)) {
                    throw new IOException("libmpv not found at " + dll);
                }
                return library = dll;
            }

            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
            if (!os.contains("windows") || !(arch.contains("amd64") || arch.contains("x86_64"))) {
                throw new IOException("Automatic libmpv download supports only Windows x86_64; set -Dfreja.mpv.path");
            }

            Path directory = cacheDirectory();
            Files.createDirectories(directory);
            Path dll = directory.resolve(LIBRARY);
            if (isVerifiedLibrary(dll)) {
                return library = dll;
            }

            Path archive = Files.createTempFile(directory, "mpv-", ".zip");
            Path unpacked = Files.createTempFile(directory, "mpv-", ".dll");
            try {
                download(archive);
                extract(archive, unpacked);
                if (!isVerifiedLibrary(unpacked)) {
                    throw new IOException("The extracted " + LIBRARY + " failed verification");
                }
                move(unpacked, dll);
                return library = dll;
            } finally {
                Files.deleteIfExists(archive);
                Files.deleteIfExists(unpacked);
            }
        }
    }

    /**
     * Returns the directory holding the downloaded binary.
     *
     * @return the cache directory
     */
    static Path cacheDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        Path root = local != null && !local.isBlank() ? Path.of(local, "Freja")
                : Path.of(System.getProperty("user.home"), ".freja");
        return root.resolve("native").resolve("mpv").resolve(VERSION);
    }

    private static void download(Path archive) throws IOException {
        URLConnection connection = URI.create(URL).toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        try (InputStream input = connection.getInputStream();
                DigestInputStream digest = new DigestInputStream(input, sha256());
                OutputStream output = Files.newOutputStream(archive, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = digest.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            String actual = HexFormat.of().formatHex(digest.getMessageDigest().digest());
            if (!ARCHIVE_SHA256.equalsIgnoreCase(actual)) {
                throw new IOException("SHA-256 mismatch for " + ASSET + ": " + actual);
            }
        }
    }

    private static void extract(Path archive, Path target) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (LIBRARY.equals(entry.getName())) {
                    try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING)) {
                        input.transferTo(output);
                    }
                    return;
                }
            }
        }
        throw new IOException(LIBRARY + " is missing from " + ASSET);
    }

    private static boolean isVerifiedLibrary(Path dll) throws IOException {
        return Files.isRegularFile(dll) && Files.size(dll) == LIBRARY_SIZE
                && LIBRARY_SHA256.equalsIgnoreCase(sha256(dll));
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Computes the SHA-256 digest of a file.
     *
     * @param file a file
     * @return the lower-case hex digest
     * @throws IOException when the file cannot be read
     */
    public static String sha256(Path file) throws IOException {
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), sha256())) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        }
    }

    /**
     * Reports that the native library could not be prepared.
     */
    public static final class UnavailableException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
