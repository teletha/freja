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
 * The user's preference for indexing images inside ZIP and 7z containers.
 * <p>
 * Scanning archives is on by default, so a library which contains photo archives shows their
 * contents on the first run. Turning it off stops new entries from being indexed; the next full
 * scan also removes rows which were indexed from archives previously.
 * </p>
 */
public final class ArchiveSettings {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ArchiveSettings.class);

    private static final String SCAN_KEY = "scanArchives";

    private ArchiveSettings() {
    }

    /**
     * Whether the scanner should look inside compressed files.
     *
     * @return true unless the user turned the option off
     */
    public static boolean scanArchives() {
        return PREFERENCES.getBoolean(SCAN_KEY, true);
    }

    /**
     * Remembers the choice for this and future sessions.
     *
     * @param enabled whether archives should be scanned
     */
    public static void scanArchives(boolean enabled) {
        PREFERENCES.putBoolean(SCAN_KEY, enabled);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }
}
