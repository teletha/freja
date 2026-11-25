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

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import viewtify.Viewtify;

/**
 * Bridges the Swing edition's {@link freja.ui.Theme} palette to JavaFX.
 * <p>
 * Viewtify's bundled theme is a neutral grey which looks nothing like the blue-tinted FlatLaf
 * palette of the Swing edition. This class writes the active Swing palette as a JavaFX stylesheet
 * and inserts it <em>before</em> Viewtify's generated {@code application.css}. The order matters:
 * JavaFX resolves looked-up colors while parsing each stylesheet, so a palette attached later can
 * no longer satisfy the rules Stylist generated for the views.
 * </p>
 */
public final class FxPalette {

    /** The stylesheet which reshapes the stock JavaFX controls. */
    private static final String CONTROLS = FxPalette.class.getResource("/freja/fx/freja.css").toExternalForm();

    /** A version suffix forces JavaFX to reload the generated palette after a theme switch. */
    private static int generation;

    /** The URL of the palette stylesheet currently in use. */
    private static String palette;

    private FxPalette() {
    }

    /** Writes the palette stylesheet for the current Swing theme. */
    private static synchronized void write() {
        generation++;
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "freja-fx-palette-" + generation + ".css");
        try {
            Files.writeString(file, stylesheet());
            palette = file.toUri().toString();
        } catch (IOException e) {
            // Without the palette the stock Viewtify theme remains, which is still a working UI.
            e.printStackTrace();
        }
    }

    /**
     * Installs the palette before the main scene is shown. Called from Viewtify's
     * {@code onInitialize} hook, which runs after its own stylesheets are collected but before the
     * first CSS pass. All later windows inherit the palette from Viewtify's global stylesheet list.
     */
    public static synchronized void installGlobal(Scene scene) {
        write();
        if (palette == null) {
            return;
        }
        try {
            Field field = Viewtify.class.getDeclaredField("stylesheets");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> stylesheets = (List<String>) field.get(null);
            insert(stylesheets);
        } catch (ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace();
        }
        if (scene != null) {
            insert(scene.getStylesheets());
            Node root = scene.getRoot();
            if (root instanceof Parent parent && !parent.getStyleClass().contains("freja-root")) {
                parent.getStyleClass().add("freja-root");
            }
        }
    }

    /** Rewrites the palette and swaps it into every open window, e.g. after a light/dark switch. */
    public static synchronized void refresh() {
        write();
        if (palette == null) {
            return;
        }
        try {
            Field field = Viewtify.class.getDeclaredField("stylesheets");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> stylesheets = (List<String>) field.get(null);
            insert(stylesheets);
        } catch (ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace();
        }
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene != null) {
                insert(scene.getStylesheets());
            }
        }
    }

    private static void insert(List<String> stylesheets) {
        stylesheets.removeIf(sheet -> sheet.contains("freja-fx-palette-") || sheet.equals(CONTROLS));
        stylesheets.add(0, CONTROLS);
        stylesheets.add(0, palette);
    }

    /** Builds the stylesheet defining the looked-up colors from the Swing palette. */
    private static String stylesheet() {
        StringBuilder css = new StringBuilder(".root, .freja-root {\n");
        lookup(css, "freja-background", freja.ui.Theme.BACKGROUND);
        lookup(css, "freja-surface", freja.ui.Theme.SURFACE);
        lookup(css, "freja-sidebar", freja.ui.Theme.SIDEBAR);
        lookup(css, "freja-border", freja.ui.Theme.BORDER);
        lookup(css, "freja-text", freja.ui.Theme.TEXT);
        lookup(css, "freja-muted", freja.ui.Theme.MUTED);
        lookup(css, "freja-faint", freja.ui.Theme.FAINT);
        lookup(css, "freja-accent", freja.ui.Theme.ACCENT);
        lookup(css, "freja-accent-soft", freja.ui.Theme.ACCENT_SOFT);
        lookup(css, "freja-accent-dark", freja.ui.Theme.ACCENT_DARK);
        lookup(css, "freja-gold", freja.ui.Theme.GOLD);
        lookup(css, "freja-danger", freja.ui.Theme.DANGER);
        lookup(css, "freja-hover", freja.ui.Theme.HOVER);
        lookup(css, "freja-viewer", freja.ui.Theme.VIEWER);
        lookup(css, "freja-viewer-panel", freja.ui.Theme.VIEWER_PANEL);
        lookup(css, "freja-selection", freja.ui.Theme.SELECTION);

        // Map the standard lookups onto the same palette. These must be literal colors: JavaFX
        // resolves chained lookups only one level deep while parsing a stylesheet, so defining
        // -fx-outer-border as -freja-border would leave a string where a Paint is expected.
        lookup(css, "fx-base", freja.ui.Theme.BACKGROUND);
        lookup(css, "fx-background", freja.ui.Theme.BACKGROUND);
        lookup(css, "fx-control-inner-background", freja.ui.Theme.SURFACE);
        lookup(css, "fx-accent", freja.ui.Theme.ACCENT);
        lookup(css, "fx-light-text-color", freja.ui.Theme.TEXT);
        lookup(css, "fx-mid-text-color", freja.ui.Theme.MUTED);
        lookup(css, "fx-dark-text-color", freja.ui.Theme.TEXT);
        lookup(css, "fx-text-base-color", freja.ui.Theme.TEXT);
        lookup(css, "fx-text-fill", freja.ui.Theme.TEXT);
        lookup(css, "fx-focus-color", freja.ui.Theme.ACCENT);
        lookup(css, "fx-faint-focus-color", freja.ui.Theme.ACCENT_SOFT);
        lookup(css, "fx-selection-bar", freja.ui.Theme.ACCENT);
        lookup(css, "fx-selection-bar-text", freja.ui.Theme.TEXT);
        lookup(css, "fx-box-border", freja.ui.Theme.BORDER);
        lookup(css, "fx-outer-border", freja.ui.Theme.BORDER);
        lookup(css, "fx-inner-border", freja.ui.Theme.BORDER);
        css.append("    -fx-font-family: \"").append(freja.ui.Theme.fontFamily()).append("\";\n");
        css.append("    -fx-font-size: ").append(freja.ui.Theme.fontSize()).append("px;\n");
        return css.append("}\n").toString();
    }

    private static void lookup(StringBuilder css, String name, Color color) {
        String prefix = "    -" + name + ": ";
        int alpha = color.getAlpha();
        if (alpha == 255) {
            css.append(prefix).append('#').append(hex(color)).append(";\n");
        } else {
            css.append(prefix)
                    .append("rgba(")
                    .append(color.getRed())
                    .append(',')
                    .append(color.getGreen())
                    .append(',')
                    .append(color.getBlue())
                    .append(',')
                    .append(String.format("%.3f", alpha / 255d))
                    .append(");\n");
        }
    }

    private static String hex(Color color) {
        return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
