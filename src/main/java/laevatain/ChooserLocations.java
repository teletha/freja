/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain;

import java.io.File;
import java.util.prefs.Preferences;

/** Remembers the last directory used by each file chooser, keyed by the dialog that owns it. */
public final class ChooserLocations {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ChooserLocations.class);

    private ChooserLocations() {
    }

    /**
     * Returns the remembered directory for a chooser.
     *
     * @param key an identifier unique to one dialog
     * @return an existing directory, or the user's home directory
     */
    public static File get(String key) {
        File remembered = getOrNull(key);
        return remembered == null ? new File(System.getProperty("user.home")) : remembered;
    }

    /** Returns the remembered directory, or null when this chooser has never been used. */
    public static File getOrNull(String key) {
        String remembered = PREFERENCES.get(key, "");
        if (!remembered.isBlank()) {
            File directory = new File(remembered);
            while (directory != null && !directory.isDirectory()) {
                directory = directory.getParentFile();
            }
            if (directory != null) {
                return directory;
            }
        }
        return null;
    }

    /** Stores the directory a chooser was last left in. */
    public static void remember(String key, File location) {
        if (location == null) {
            return;
        }
        File directory = location.isDirectory() ? location : location.getParentFile();
        if (directory == null) {
            return;
        }
        PREFERENCES.put(key, directory.getAbsolutePath());
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }
}
