/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.setting;

import java.util.prefs.Preferences;

/** File manager preferences shared by the settings page and the browser. */
public final class FileSettings {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FileSettings.class);

    private static final String HIDE_HIDDEN = "hideHidden";

    private FileSettings() {
    }

    /** Whether hidden files and folders are left out of the file list. */
    public static boolean hideHidden() {
        return PREFERENCES.getBoolean(HIDE_HIDDEN, true);
    }

    public static void hideHidden(boolean enabled) {
        PREFERENCES.putBoolean(HIDE_HIDDEN, enabled);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }
}
