/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Bulk export and import of the application's user preferences.
 * <p>
 * Freja keeps its settings in a handful of Java {@link Preferences} nodes - the theme, font,
 * language and window layout under {@code /freja}, and the reusable components' language and
 * chooser folders under {@code /laevatain}. Collecting those nodes into one properties file makes
 * a profile portable; restoring it replaces their contents so stale values from the current
 * machine cannot survive the import. Keys outside those two roots are never touched.
 * </p>
 */
public final class SettingsBackup {

    /** The format marker written to every export. */
    private static final String VERSION = "1";

    private static final String VERSION_KEY = "freja.backup.version";

    /** Separates the preference node path from the key inside a properties name. */
    private static final String SEPARATOR = "|";

    /** The preference roots which hold settings written by Freja and its UI components. */
    private static final String[] ROOTS = {"/freja", "/laevatain"};

    private SettingsBackup() {
    }

    /**
     * Writes every application preference to a file the user can keep or move to another machine.
     *
     * @param file the destination
     * @throws IOException when the preferences or the file cannot be accessed
     */
    public static void exportTo(Path file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(VERSION_KEY, VERSION);
        try {
            for (String root : ROOTS) {
                collect(Preferences.userRoot().node(root), properties);
            }
        } catch (BackingStoreException e) {
            throw new IOException("Cannot read the user preferences", e);
        }
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Freja settings: theme, fonts, language, window layout, chooser folders");
        }
    }

    /**
     * Replaces the application preferences with the contents of a backup file.
     *
     * @param file the file written by {@link #exportTo(Path)}
     * @throws IOException when the file is not a valid backup or the preferences cannot be written
     */
    public static void importFrom(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        if (!VERSION.equals(properties.getProperty(VERSION_KEY))) {
            throw new IOException("This file is not a Freja settings backup");
        }
        try {
            for (String root : ROOTS) {
                clear(Preferences.userRoot().node(root));
            }
            for (String name : properties.stringPropertyNames()) {
                if (VERSION_KEY.equals(name)) {
                    continue;
                }
                int separator = name.indexOf(SEPARATOR);
                if (separator < 0) {
                    continue;
                }
                String path = name.substring(0, separator);
                if (!owned(path)) {
                    continue;
                }
                Preferences.userRoot().node(path).put(name.substring(separator + SEPARATOR.length()), properties.getProperty(name));
            }
            // Flush the roots: Preferences.flush also persists every descendant.
            for (String root : ROOTS) {
                Preferences.userRoot().node(root).flush();
            }
        } catch (BackingStoreException e) {
            throw new IOException("Cannot restore the user preferences", e);
        }
    }

    private static void collect(Preferences node, Properties properties) throws BackingStoreException {
        for (String key : node.keys()) {
            properties.setProperty(node.absolutePath() + SEPARATOR + key, node.get(key, ""));
        }
        for (String child : node.childrenNames()) {
            collect(node.node(child), properties);
        }
    }

    private static void clear(Preferences node) throws BackingStoreException {
        // Keep the node itself: components hold on to their Preferences object, and removing it
        // would make every later read throw "Node has been removed".
        node.clear();
        for (String child : node.childrenNames()) {
            clear(node.node(child));
        }
    }

    /** True only for the nodes this application owns, so a hand-edited file cannot reach others. */
    private static boolean owned(String path) {
        for (String root : ROOTS) {
            if (path.equals(root) || path.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }
}
