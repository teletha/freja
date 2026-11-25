/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.SwingUtilities;

import kiss.I;

/**
 * The settings store of the Web edition, persisted as {@code settings.json} in the data directory.
 * <p>
 * The renderer keeps the live map and sends it back in full on every change; the Java side only
 * persists it and applies the keys which need framework attention: the display language switches
 * the translation layer, and the rest are carried into the bootstrap so the next startup resumes
 * where the user left off.
 * </p>
 */
public final class WebSettings {

    private static final Map<String, String> DEFAULTS = defaults();

    private final Path file;

    private final Map<String, String> values = new LinkedHashMap<>();

    /**
     * Opens the settings store.
     *
     * @param directory the data directory, typically {@code .freja-web}
     */
    public WebSettings(Path directory) {
        this.file = directory.resolve("settings.json");
        values.putAll(DEFAULTS);
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                Map<String, String> read = I.json(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8), Map.class);
                if (read != null) {
                    values.putAll(read);
                }
            } catch (IOException | RuntimeException e) {
                I.error("freja", new IOException("Cannot read the settings " + file, e));
            }
        }
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("theme", "dark");
        map.put("thumbSize", "170");
        map.put("sort", "NAME");
        map.put("inspectorVisible", "true");
        map.put("scanOnStartup", "false");
        map.put("sweep", "false");
        map.put("language", "");
        map.put("selectedFolder", "");
        return map;
    }

    /**
     * Replaces the full map sent by the renderer and persists it.
     *
     * @param received the map, or null for none
     */
    public synchronized void replace(Map<String, String> received) {
        if (received == null) {
            return;
        }
        for (Map.Entry<String, String> entry : received.entrySet()) {
            if (entry.getValue() != null) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        store();
    }

    /**
     * Writes the current map to the settings file.
     */
    public synchronized void store() {
        try (OutputStream output = Files.newOutputStream(file)) {
            I.write(values, new java.io.OutputStreamWriter(output, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            I.error("freja", new IOException("Cannot write the settings " + file, e));
        }
    }

    /**
     * Returns a string value with a fallback.
     *
     * @param key the setting name
     * @param fallback the default when absent or blank
     * @return the string value
     */
    public synchronized String string(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Returns a boolean value.
     *
     * @param key the setting name
     * @return the boolean value, or false for absent
     */
    public synchronized boolean bool(String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    /**
     * Returns a number value.
     *
     * @param key the setting name
     * @param fallback the default when absent or unparsable
     * @return the number
     */
    public synchronized int number(String key, int fallback) {
        try {
            return Integer.parseInt(values.get(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Returns an immutable copy of the settings for the bootstrap.
     *
     * @return the live map
     */
    public synchronized Map<String, String> map() {
        return Map.copyOf(values);
    }

    /**
     * Returns the data directory where the settings file lives.
     *
     * @return the directory
     */
    public Path directory() {
        return file.getParent();
    }

    /**
     * Applies a changed language to the translation layer, then refreshes the browser on the
     * swing thread.
     *
     * @param language the new language code, or empty for the system language
     * @param onApplied a callback run once the change is visible, or null
     */
    public void applyLanguage(String language, Runnable onApplied) {
        laevatain.util.Language.install(language.isEmpty() ? laevatain.util.Language.language() : language);
        if (onApplied != null) {
            SwingUtilities.invokeLater(onApplied);
        }
    }
}
