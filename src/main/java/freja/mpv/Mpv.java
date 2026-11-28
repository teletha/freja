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

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kiss.I;

/**
 * A small, synchronous wrapper around one libmpv instance.
 * <p>
 * The wrapper is UI independent on purpose: it can produce a still frame for the catalog as well as
 * drive a player surface later on. Only one thread may poll events for an instance, which is why
 * {@link #load(Path)} and {@link #screenshot(Path)} are synchronous.
 * </p>
 */
public final class Mpv implements AutoCloseable {

    /** mpv event: the file was opened and decoding started. */
    public static final int FILE_LOADED = 8;

    /** mpv event: playback of the current file ended. */
    public static final int END_FILE = 7;

    /** One chapter of the loaded file: its starting second and its optional title. */
    public record Chapter(double time, String title) {
    }

    private final MpvBindings bindings;
    private final MemorySegment handle;

    private volatile boolean closed;

    private Mpv(MpvBindings bindings, MemorySegment handle) {
        this.bindings = bindings;
        this.handle = handle;
    }

    /**
     * Creates and initializes an instance with the given libmpv options.
     *
     * @param options option name to value pairs, e.g. {@code vo=null}
     * @return the ready instance
     */
    public static Mpv open(Map<String, String> options) {
        MpvBindings bindings = MpvNative.bindings();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = bindings.create();
            if (handle.equals(MemorySegment.NULL)) {
                throw new MpvException("mpv_create failed");
            }
            Mpv mpv = new Mpv(bindings, handle);
            try {
                for (Map.Entry<String, String> option : options.entrySet()) {
                    mpv.setOption(option.getKey(), option.getValue(), arena);
                }
                int result = bindings.initialize(handle);
                if (result != 0) {
                    throw new MpvException("mpv_initialize failed: " + bindings.error(result));
                }
                return mpv;
            } catch (Throwable e) {
                mpv.close();
                throw failure("Cannot initialize libmpv", e);
            }
        } catch (MpvException e) {
            throw e;
        } catch (Throwable e) {
            throw failure("Cannot initialize libmpv", e);
        }
    }

    /**
     * Sets a string property, e.g. {@code pause} or {@code volume}.
     *
     * @param name the property name
     * @param value the value
     */
    public void set(String name, String value) {
        try (Arena arena = Arena.ofConfined()) {
            check(bindings.setString(handle, name, value, arena), "set " + name);
        } catch (MpvException e) {
            throw e;
        } catch (Throwable e) {
            throw failure("Cannot set the mpv property " + name, e);
        }
    }

    /**
     * Reads a string property.
     *
     * @param name the property name
     * @return the value, or null when unavailable
     */
    public String property(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return bindings.getString(handle, name, arena);
        } catch (Throwable e) {
            throw failure("Cannot read the mpv property " + name, e);
        }
    }

    /**
     * Reads the chapters of the loaded file in their chronological order.
     *
     * @return the chapters, or an empty list when the file has none
     */
    public List<Chapter> chapters() {
        int count = (int) number(property("chapter-list/count"), 0);
        List<Chapter> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String title = property("chapter-list/" + i + "/title");
            result.add(new Chapter(number(property("chapter-list/" + i + "/time"), 0),
                    title == null ? "" : title));
        }
        return result;
    }

    /**
     * Returns the index of the chapter containing the current position, or -1 outside any chapter.
     *
     * @return the zero-based chapter index
     */
    public int chapter() {
        return (int) number(property("chapter"), -1);
    }

    /**
     * Seeks to the start of the given chapter.
     *
     * @param index the zero-based chapter index
     */
    public void chapter(int index) {
        set("chapter", Integer.toString(index));
    }

    private static double number(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Sets an integer property, e.g. {@code wid} for embedding.
     *
     * @param name the property name
     * @param value the value
     */
    public void setLong(String name, long value) {
        try (Arena arena = Arena.ofConfined()) {
            check(bindings.setLong(handle, name, value, arena), "set " + name);
        } catch (Throwable e) {
            throw failure("Cannot set the mpv property " + name, e);
        }
    }

    /**
     * Opens a media file, replacing the current playlist, and waits until its headers are loaded.
     *
     * @param file a local media file
     */
    public void load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Not a file: " + file);
        }
        command("loadfile", file.toAbsolutePath().normalize().toString(), "replace");
        try {
            awaitFileLoaded(10_000);
        } catch (Throwable e) {
            throw failure("Cannot load " + file, e);
        }
    }

    /**
     * Writes the current video frame as a PNG image.
     *
     * @param file the destination
     * @throws IOException when no image was written
     */
    public void screenshot(Path file) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        command("screenshot-to-file", target.toString(), "video");
        // mpv queues the command and writes the PNG on its own thread. A single existence check
        // against a pre-created empty file produced missing or half-written thumbnails.
        long deadline = System.nanoTime() + 5_000_000_000L;
        long previous = -1;
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(target)) {
                long size;
                try {
                    size = Files.size(target);
                } catch (IOException e) {
                    size = -1;
                }
                if (size > 1024 && size == previous && isPng(target)) {
                    return;
                }
                previous = size;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the mpv screenshot", e);
            }
        }
        throw new IOException("mpv did not write a complete screenshot to " + target);
    }

    /**
     * Captures the current frame as PNG bytes, retrying while mpv has not decoded one yet.
     *
     * @param file a scratch path for the PNG
     * @return the PNG bytes
     * @throws IOException when no frame can be captured
     */
    public byte[] capture(Path file) throws IOException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (true) {
            try {
                // A missing path keeps mpv's stability check from accepting an old screenshot.
                Files.deleteIfExists(file);
                screenshot(file);
                return Files.readAllBytes(file);
            } catch (MpvException e) {
                if (System.nanoTime() >= deadline) {
                    throw new IOException("mpv could not capture a frame", e);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for a video frame", interrupted);
                }
            }
        }
    }

    /** Checks the PNG signature so a partial or foreign file is never fed to the image decoder. */
    private static boolean isPng(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] magic = in.readNBytes(8);
            return magic.length == 8 && magic[0] == (byte) 0x89 && magic[1] == 'P' && magic[2] == 'N'
                    && magic[3] == 'G' && magic[4] == 13 && magic[5] == 10 && magic[6] == 26 && magic[7] == 10;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Runs a mpv command and translates its error code.
     *
     * @param args the command and its arguments
     */
    public void command(String... args) {
        try (Arena arena = Arena.ofConfined()) {
            check(bindings.command(handle, arena, args), "command " + args[0]);
        } catch (Throwable e) {
            throw failure("mpv command " + args[0] + " failed", e);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            bindings.terminate(handle);
        } catch (Throwable e) {
            I.warn("freja", new IOException("Cannot close libmpv", e));
        }
    }

    private void setString(String name, String value, Arena arena) throws Throwable {
        check(bindings.setString(handle, name, value, arena), "set " + name);
    }

    private void setOption(String name, String value, Arena arena) throws Throwable {
        check(bindings.setOption(handle, name, value, arena), "set option " + name);
    }

    private void awaitFileLoaded(long timeoutMillis) throws Throwable {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            MemorySegment pending = bindings.waitEvent(handle, 0);
            if (pending == null || pending.equals(MemorySegment.NULL)) {
                Thread.sleep(10);
                continue;
            }
            MemorySegment event = pending.reinterpret(MpvBindings.EVENT_LAYOUT.byteSize());
            int id = event.get(JAVA_INT, MpvBindings.EVENT_ID);
            int error = event.get(JAVA_INT, MpvBindings.EVENT_ERROR);
            if (id == FILE_LOADED) {
                if (error != 0) {
                    throw new MpvException("mpv could not load the file: " + bindings.error(error));
                }
                return;
            }
            if (id == END_FILE) {
                throw new MpvException("mpv could not load the file: " + bindings.error(error));
            }
            Thread.sleep(10);
        }
        throw new MpvException("Timed out waiting for mpv to load the file");
    }

    private void check(int result, String action) throws Throwable {
        if (result != 0) {
            throw new MpvException("mpv " + action + " failed: " + bindings.error(result));
        }
    }

    private static MpvException failure(String message, Throwable cause) {
        return new MpvException(message, cause);
    }

    /**
     * Reports a libmpv API or playback failure.
     */
    public static final class MpvException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public MpvException(String message) {
            super(message);
        }

        MpvException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
