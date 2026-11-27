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

import java.util.prefs.Preferences;

/**
 * The user's preference for the position from which a video's catalog thumbnail is taken.
 * <p>
 * The value is a number of seconds from the start of the clip. Zero keeps the very first frame;
 * any other value is clamped to the middle of shorter clips so the thumbnail never lands in the
 * closing credits. The preference lives under {@code /freja}, so
 * {@code freja.setting.SettingsBackup} includes it in exported profiles.
 * </p>
 */
public final class VideoThumbnailSettings {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(VideoThumbnailSettings.class);

    private static final String SECONDS_KEY = "videoThumbnailSeconds";

    /** The thumbnail position used when the user has not chosen one: three minutes in. */
    public static final int DEFAULT_SECONDS = 180;

    /** The earliest position; zero asks for the first frame without seeking. */
    public static final int MIN_SECONDS = 0;

    /** The latest position the settings control offers. */
    public static final int MAX_SECONDS = 3600;

    private VideoThumbnailSettings() {
    }

    /**
     * Returns the configured position, clamped to the supported range.
     *
     * @return seconds from the start of a video
     */
    public static int thumbnailSeconds() {
        return clamp(PREFERENCES.getInt(SECONDS_KEY, DEFAULT_SECONDS));
    }

    /**
     * Remembers the configured position for this and future sessions.
     *
     * @param seconds seconds from the start of a video
     */
    public static void thumbnailSeconds(int seconds) {
        PREFERENCES.putInt(SECONDS_KEY, clamp(seconds));
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }

    private static int clamp(int seconds) {
        return Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
    }
}
