/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * The window icons Freja can switch between in the settings page. Each design is an SVG in
 * {@code icons/app}, rendered by {@link FlatSVGIcon} at the exact size the caller asks for. This
 * keeps the same vector pipeline as the rest of the interface and lets FlatLaf produce crisp
 * variants for small sizes and HiDPI screens instead of shrinking a single 128px bitmap.
 * <p>
 * The selected design lives in {@link Preferences} under {@code /freja/ui}, so the settings backup
 * exports and imports it along with the rest of the profile.
 * </p>
 */
public enum AppIcon {

    /** The original four-curved-blade pinwheel with a small house, inspired by Picasa. */
    PINWHEEL("pinwheel", "Pinwheel"),

    /** A circle divided into six equal coloured sectors, like a camera shutter. */
    APERTURE("aperture", "Aperture"),

    /** Four rounded colour tiles on a white rounded square. */
    TILES("tiles", "Tiles"),

    /** A strip of film with sprocket holes and four coloured frames. */
    FILM("film", "Film"),

    /** A small four-blade pinwheel held inside a rounded square, shown as "Flower". */
    SQUARE("square", "Flower");

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(AppIcon.class);

    private static final String PREFERENCE_KEY = "appIcon";

    private static final String ICON_PATH = "icons/app/";

    private static final int SIZE = 128;

    /** Native variants supplied to the window manager, smallest first. */
    private static final int[] SIZES = {16, 20, 32, 48, 128};

    private final String id;

    /** The English caption; {@link kiss.I#translate(String)} resolves it for the current locale. */
    private final String label;

    private final Map<Integer, FlatSVGIcon> icons = new ConcurrentHashMap<>();

    private Image image;

    AppIcon(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** The design remembered for this and future sessions, defaulting to the flower tile. */
    public static AppIcon current() {
        String stored = PREFERENCES.get(PREFERENCE_KEY, SQUARE.id);
        for (AppIcon icon : values()) {
            if (icon.id.equals(stored)) {
                return icon;
            }
        }
        return SQUARE;
    }

    /** Remembers the selected design immediately, as settings changes are not tied to a restart. */
    public static void select(AppIcon icon) {
        PREFERENCES.put(PREFERENCE_KEY, icon.id);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }

    /** Persisted identifier of this design. */
    public String id() {
        return id;
    }

    /** Translation source for the name shown in the settings list. */
    public String label() {
        return label;
    }

    /** The vector icon rasterised at the requested pixel size. */
    public Icon thumbnail(int size) {
        return svg(size);
    }

    /** The full-size image used when only one window icon can be supplied. */
    public Image image() {
        Image result = image;
        if (result == null) {
            result = svg(SIZE).getImage();
            image = result;
        }
        return result;
    }

    /** The rasterised variants, so the platform can choose a native-sized icon. */
    public List<Image> images() {
        List<Image> icons = new ArrayList<>();
        for (int size : SIZES) {
            icons.add(svg(size).getImage());
        }
        return icons;
    }

    private FlatSVGIcon svg(int size) {
        return icons.computeIfAbsent(size, value -> new FlatSVGIcon(ICON_PATH + id + ".svg", value, value));
    }
}
