/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.util.Locale;

import freja.model.PhotoFilter;
import viewtify.preference.Preferences;

/**
 * Persisted settings of the JavaFX edition. Viewtify stores them as JSON under its preference
 * directory, separately from the Swing edition's {@code java.util.prefs} node.
 */
public class FrejaSetting extends Preferences {

    /** Whether the catalog is scanned once when the application starts. */
    public final Preference<Boolean> scanOnStartup = initialize(false);

    /** Whether the periodic sweep repair runs while watching. */
    public final Preference<Boolean> sweep = initialize(false);

    /** The thumbnail edge length used by the grid. */
    public final IntPreference thumbSize = initialize(170);

    /** The order used by the whole-library views. */
    public final Preference<String> sort = initialize(PhotoFilter.SortOrder.NAME.name());

    /** Whether the inspector is docked on the right. */
    public final Preference<Boolean> inspectorVisible = initialize(true);

    /** The path of the folder selected before the last shutdown. */
    public final Preference<String> selectedFolder = initialize("");

    /** The active order as an enum, or {@code NAME} when the stored name is unknown. */
    public PhotoFilter.SortOrder order() {
        try {
            return PhotoFilter.SortOrder.valueOf(sort.get().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PhotoFilter.SortOrder.NAME;
        }
    }
}
