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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Semaphore;

import freja.mpv.Mpv;
import freja.mpv.MpvNative;
import freja.util.VideoThumbnailSettings;

/**
 * Produces the catalog entry of a video: a still frame for the thumbnail plus the dimensions and
 * duration reported by libmpv.
 */
public final class VideoImporter {

    /**
     * A dozen simultaneous mpv clients made frame extraction flaky, but two leave most cores idle
     * on a large machine. Scale the limit with the machine, capped at six decoders.
     */
    private static final Semaphore DECODERS = new Semaphore(
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 4)));

    private VideoImporter() {
    }

    /**
     * Analyzes a video file with libmpv.
     *
     * @param file a video file
     * @param modifiedAt the file's modification time, or -1 to read it
     * @return the analysis with the video duration in milliseconds
     * @throws IOException when libmpv is unavailable or the file cannot be decoded
     */
    public static PhotoImporter.AnalyzedPhoto analyze(Path file, long modifiedAt) throws IOException {
        if (!MpvNative.isAvailable()) {
            throw new IOException("libmpv is not available: " + MpvNative.failure(), MpvNative.failure());
        }

        try {
            DECODERS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted before decoding the video " + file, e);
        }
        Path directory = null;
        try {
            // Give mpv a path which does not exist yet, so a screenshot cannot be mistaken for the
            // empty placeholder file and a slow write cannot be read half-finished.
            directory = Files.createTempDirectory("freja-video-");
            Path frame = directory.resolve("frame.png");
            Path baseline = directory.resolve("baseline.png");
            int width;
            int height;
            long durationMillis;
            try (Mpv mpv = Mpv.open(Map.of("vo", "null", "audio", "no", "pause", "yes", "config", "no"))) {
                mpv.load(file);
                // Track metadata arrives shortly after FILE_LOADED; read it again for a brief window
                // instead of accepting the first (often empty) property sample.
                String widthValue = mpv.property("width");
                String heightValue = mpv.property("height");
                String durationValue = mpv.property("duration");
                long metaDeadline = System.nanoTime() + 1_000_000_000L;
                while (System.nanoTime() < metaDeadline && (integer(widthValue, 0) <= 0 || integer(heightValue, 0) <= 0)) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    widthValue = mpv.property("width");
                    heightValue = mpv.property("height");
                    durationValue = mpv.property("duration");
                }
                width = integer(widthValue, 0);
                height = integer(heightValue, 0);
                double duration = decimal(durationValue, 0);
                durationMillis = Math.round(duration * 1000);
                double target = seekTarget(duration, VideoThumbnailSettings.thumbnailSeconds());
                if (target > 0) {
                    // time-pos reaches the target before the decoder draws the new frame, so a
                    // screenshot taken right after the seek still shows the first frame. Keep
                    // sampling until the image differs from the pre-seek one, then keep that frame.
                    byte[] before = screenshot(mpv, baseline);
                    mpv.command("seek", Double.toString(target), "absolute+exact");
                    long deadline = System.nanoTime() + 3_000_000_000L;
                    while (System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (!Arrays.equals(screenshot(mpv, frame), before)) {
                            break;
                        }
                    }
                } else {
                    screenshot(mpv, frame);
                }
            } catch (RuntimeException e) {
                throw new IOException("Cannot decode the video " + file.getFileName(), e);
            }

            String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
            PhotoImporter.AnalyzedPhoto base = PhotoImporter.analyze(Files.readAllBytes(frame), name, modifiedAt);
            return new PhotoImporter.AnalyzedPhoto(width > 0 ? width : base.width(),
                    height > 0 ? height : base.height(), base.capturedAt(), base.exif(), base.thumbnail(),
                    base.thumbnailWidth(), base.thumbnailHeight(), durationMillis);
        } finally {
            if (directory != null) {
                try {
                    Files.deleteIfExists(directory.resolve("frame.png"));
                    Files.deleteIfExists(directory.resolve("baseline.png"));
                    Files.deleteIfExists(directory);
                } catch (IOException ignored) {
                }
            }
            DECODERS.release();
        }
    }

    /**
     * Position of the thumbnail frame, in seconds from the start of the clip.
     *
     * @param duration the clip length in seconds, or 0 when unknown
     * @param seconds the configured position, or 0 to keep the very first frame
     * @return the seek target, or 0 when no seek is needed
     */
    static double seekTarget(double duration, int seconds) {
        if (duration <= 1 || seconds <= 0) {
            return 0;
        }
        // The configured position must not land in the closing credits of a short clip. Half the
        // clip is as far as the thumbnail is ever taken, with a small floor for very short seeks.
        return Math.min(Math.max(0.5, seconds), duration / 2);
    }

    /**
     * Captures one frame, retrying until mpv has decoded one.
     * <p>
     * {@code screenshot-to-file} fails while the first frame is still being decoded, which is
     * common right after loading a large video. Retrying turns that transient failure into a
     * usable frame instead of a placeholder catalog row.
     * </p>
     *
     * @param mpv the player holding the video
     * @param file where to write the PNG
     * @return the PNG bytes
     * @throws IOException when no frame appears within the deadline
     */
    private static byte[] screenshot(Mpv mpv, Path file) throws IOException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (true) {
            try {
                // Start from a missing file so mpv's stability check cannot accept an old image.
                Files.deleteIfExists(file);
                mpv.screenshot(file);
                return Files.readAllBytes(file);
            } catch (Mpv.MpvException e) {
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

    /**
     * Builds the catalog entry of a video whose analysis failed. A negative duration marks the row
     * for re-import on the next scan, so a transient libmpv failure does not become permanent.
     *
     * @param file the unreadable video
     * @param modifiedAt the file's modification time, or -1 to read it
     * @return the retry analysis
     */
    public static PhotoImporter.AnalyzedPhoto failed(Path file, long modifiedAt) {
        PhotoImporter.AnalyzedPhoto placeholder = PhotoImporter.placeholder(file, modifiedAt);
        return new PhotoImporter.AnalyzedPhoto(0, 0, placeholder.capturedAt(), placeholder.exif(),
                placeholder.thumbnail(), placeholder.thumbnailWidth(), placeholder.thumbnailHeight(), -1);
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double decimal(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
