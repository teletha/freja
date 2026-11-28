/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain.component;

import java.awt.Component;
import java.io.File;

import com.formdev.flatlaf.util.SystemFileChooser;

import laevatain.ChooserLocations;

/**
 * A FlatLaf system chooser which opens where its owner last left it and stores the new location when
 * it closes. Each dialog passes its own key so the positions do not mix.
 */
public final class ARXFileChooser extends SystemFileChooser {

    private final String key;

    public ARXFileChooser(String key) {
        this(key, null);
    }

    /**
     * @param key an identifier unique to the dialog using this chooser
     * @param fallback where to open when the dialog has never been used
     */
    public ARXFileChooser(String key, File fallback) {
        super();
        this.key = key;
        File location = ChooserLocations.getOrNull(key);
        if (location == null) {
            location = fallback;
        }
        if (location != null && location.isDirectory()) {
            setCurrentDirectory(location);
        }
    }

    @Override
    public int showOpenDialog(Component parent) {
        return remember(super.showOpenDialog(parent));
    }

    @Override
    public int showSaveDialog(Component parent) {
        return remember(super.showSaveDialog(parent));
    }

    @Override
    public int showDialog(Component parent, String approveButtonText) {
        return remember(super.showDialog(parent, approveButtonText));
    }

    private int remember(int result) {
        File[] selected = result == APPROVE_OPTION ? getSelectedFiles() : null;
        File location = selected != null && selected.length > 0 ? selected[0] : getCurrentDirectory();
        ChooserLocations.remember(key, location);
        return result;
    }
}
