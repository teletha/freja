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

/** Window layout values shared by the library pane and the file manager. */
public final class WindowSettings {

    private static final Preferences PREFERENCES = Preferences.userRoot().node("/freja/MainWindow");

    private static final String SIDEBAR_DIVIDER = "view.sidebarDivider";

    private static final int DEFAULT_SIDEBAR_DIVIDER = 246;

    private WindowSettings() {
    }

    /** Width of the library sidebar, reused as the file manager's folder list width. */
    public static int sidebarDivider() {
        return PREFERENCES.getInt(SIDEBAR_DIVIDER, DEFAULT_SIDEBAR_DIVIDER);
    }

    public static void sidebarDivider(int width) {
        PREFERENCES.putInt(SIDEBAR_DIVIDER, width);
    }

    private static final String ACTIVITY = "view.activity";

    /** Which activity bar pane was last open. */
    public static String activity() {
        String value = PREFERENCES.get(ACTIVITY, "viewer");
        return switch (value) {
            case "files", "settings" -> value;
            default -> "viewer";
        };
    }

    public static void activity(String id) {
        PREFERENCES.put(ACTIVITY, id);
    }
}
