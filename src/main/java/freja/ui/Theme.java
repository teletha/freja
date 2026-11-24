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

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * The visual language of the application.
 * <p>
 * The palette is expressed with dynamic colours, so every component which received a
 * {@link Theme} colour is repainted with the new palette when the dark mode changes. FlatLaf
 * updates the standard Swing components at the same time.
 * </p>
 */
public final class Theme {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(Theme.class);

    private static final String DARK_KEY = "dark";

    private static final String FONT_FAMILY_KEY = "font.family";

    private static final String FONT_SIZE_KEY = "font.size";

    /** The size the individual font calls in this application were designed around. */
    private static final int BASE_FONT_SIZE = 13;

    private record Palette(Color background, Color surface, Color sidebar, Color border, Color text, Color muted,
            Color faint, Color accent, Color accentSoft, Color accentDark, Color gold, Color danger, Color hover,
            Color viewer, Color viewerPanel) {
    }

    private static final Palette LIGHT = new Palette(new Color(0xF7F8FA), Color.WHITE, new Color(0xF1F3F6), new Color(0xE2E5EA), new Color(0x25282D), new Color(0x7B818B), new Color(0xA8ADB5), new Color(0x2E6BE6), new Color(0xE8F0FE), new Color(0x1F52B8), new Color(0xE9A800), new Color(0xC43D3D), new Color(0xE7EAEF), new Color(0x0D0E10), new Color(0x1A1C20));

    private static final Palette DARK = new Palette(new Color(0x15171B), new Color(0x1E2127), new Color(0x191C21), new Color(0x30343C), new Color(0xE7E9ED), new Color(0x9AA1AA), new Color(0x666D77), new Color(0x6C9BFF), new Color(0x25344D), new Color(0xB8CDFF), new Color(0xF1B64A), new Color(0xFF7676), new Color(0x282C33), new Color(0x0D0E10), new Color(0x1A1C20));

    private static volatile Palette palette = LIGHT;

    private static volatile boolean dark;

    /** The application background. */
    public static final Color BACKGROUND = dynamic(p -> p.background);

    /** The background of cards, panels and the toolbar. */
    public static final Color SURFACE = dynamic(p -> p.surface);

    /** The navigation sidebar background. */
    public static final Color SIDEBAR = dynamic(p -> p.sidebar);

    /** Hairline borders. */
    public static final Color BORDER = dynamic(p -> p.border);

    /** Primary text. */
    public static final Color TEXT = dynamic(p -> p.text);

    /** Secondary text. */
    public static final Color MUTED = dynamic(p -> p.muted);

    /** Faint decorative text and icons. */
    public static final Color FAINT = dynamic(p -> p.faint);

    /** The interactive accent. */
    public static final Color ACCENT = dynamic(p -> p.accent);

    /** The soft accent background used by selected rows. */
    public static final Color ACCENT_SOFT = dynamic(p -> p.accentSoft);

    /** The accent used for selected text. */
    public static final Color ACCENT_DARK = dynamic(p -> p.accentDark);

    /** Stars and favorites. */
    public static final Color GOLD = dynamic(p -> p.gold);

    /** Errors and destructive actions. */
    public static final Color DANGER = dynamic(p -> p.danger);

    /** Hover background of neutral rows. */
    public static final Color HOVER = dynamic(p -> p.hover);

    /** The full screen viewer background. */
    public static final Color VIEWER = dynamic(p -> p.viewer);

    /** The full screen viewer toolbar and footer. */
    public static final Color VIEWER_PANEL = dynamic(p -> p.viewerPanel);

    private static String fontFamily;

    private static int fontSize = BASE_FONT_SIZE;

    private Theme() {
    }

    /**
     * Installs the persisted look and feel.
     */
    public static void install() {
        dark = PREFERENCES.getBoolean(DARK_KEY, false);
        palette = dark ? DARK : LIGHT;
        fontFamily = PREFERENCES.get(FONT_FAMILY_KEY, detectJapaneseFont());
        fontSize = Math.max(8, Math.min(28, PREFERENCES.getInt(FONT_SIZE_KEY, BASE_FONT_SIZE)));
        applyLookAndFeel();
    }

    private static String detectJapaneseFont() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : new String[] {"Yu Gothic UI", "Meiryo UI", "Hiragino Sans", "Noto Sans JP", "Segoe UI"}) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }

    /**
     * Tests whether dark mode is active.
     *
     * @return true when dark mode is active
     */
    public static boolean isDark() {
        return dark;
    }

    /**
     * Changes the application theme.
     *
     * @param value true for dark mode, false for light mode
     */
    public static void setDark(boolean value) {
        dark = value;
        palette = value ? DARK : LIGHT;
        PREFERENCES.putBoolean(DARK_KEY, value);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
        applyLookAndFeel();
    }

    /**
     * Switches between light and dark mode.
     *
     * @return true when dark mode is now active
     */
    public static boolean toggle() {
        setDark(!dark);
        return dark;
    }

    private static void applyLookAndFeel() {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ToolBar.background", palette.sidebar);
        // FlatLaf paints toolbar buttons with its own toolbar hover colour. Point both that key and
        // the ARX blended hover at the same shade derived from the sidebar background.
        Color hover = hoverShade(palette.sidebar);
        UIManager.put("Button.toolbar.hoverBackground", hover);
        UIManager.put("Button.toolbar.hoverForeground", palette.text);
        UIManager.put("ARXButton.hoverBackground", hover);
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Component.focusColor", palette.accent);
        UIManager.put("Button.defaultBackground", palette.accent);
        UIManager.put("Button.defaultForeground", Color.WHITE);
        UIManager.put("Button.defaultHoverBackground", palette.accentDark);
        UIManager.put("Button.defaultHoverForeground", Color.WHITE);
        UIManager.put("Component.selectionBackground", palette.accentSoft);
        UIManager.put("Component.selectionForeground", palette.text);
        UIManager.put("defaultFont", font(Font.PLAIN, 13));
        FlatLaf.updateUI();
    }

    /**
     * Returns the application font.
     *
     * @param style the font style
     * @param size the point size
     * @return a font which can render Japanese
     */
    public static Font font(int style, int size) {
        int scaled = Math.max(8, Math.round(size * (float) fontSize / BASE_FONT_SIZE));
        return new Font(fontFamily, style, scaled);
    }

    /** Returns the configured font family. */
    public static String fontFamily() {
        return fontFamily;
    }

    /** Returns the configured base font size. */
    public static int fontSize() {
        return fontSize;
    }

    /** Lists font families available on this system. */
    public static String[] availableFontFamilies() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    }

    /** Changes the font family and/or base size and persists the choice. */
    public static void setFont(String family, int size) {
        String chosen = family == null || family.isBlank() ? fontFamily : family;
        int chosenSize = Math.max(8, Math.min(28, size));
        fontFamily = chosen;
        fontSize = chosenSize;
        PREFERENCES.put(FONT_FAMILY_KEY, chosen);
        PREFERENCES.putInt(FONT_SIZE_KEY, chosenSize);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
        applyLookAndFeel();
    }

    /** Rebuilds component fonts after a family/size change, preserving each component's scale. */
    public static void scaleFonts(java.awt.Component component, float ratio) {
        if (component == null) {
            return;
        }
        Font font = component.getFont();
        if (font != null) {
            int size = Math.max(8, Math.round(font.getSize2D() * ratio));
            component.setFont(new Font(fontFamily, font.getStyle(), size));
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                scaleFonts(child, ratio);
            }
        }
        if (component instanceof javax.swing.JComponent jcomponent) {
            jcomponent.revalidate();
            jcomponent.repaint();
        }
    }

    /**
     * Blends two colours at call time.
     *
     * @param first the first colour
     * @param second the second colour
     * @param ratio 0 for the first colour, 1 for the second
     * @return the mixed colour
     */
    public static Color mix(Color first, Color second, double ratio) {
        double value = Math.max(0, Math.min(1, ratio));
        return new Color((int) Math.round(first.getRed() * (1 - value) + second.getRed() * value), (int) Math.round(first.getGreen() * (1 - value) + second.getGreen() * value), (int) Math.round(first.getBlue() * (1 - value) + second.getBlue() * value));
    }

    /** Darkens a light base slightly and lightens a dark one, keeping its hue and saturation. */
    public static Color hoverShade(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float brightness = (base.getRed() * 0.299f + base.getGreen() * 0.587f + base.getBlue() * 0.114f) / 255f;
        float delta = brightness > 0.5f ? -0.06f : 0.12f;
        float value = Math.max(0f, Math.min(1f, hsb[2] + delta));
        Color derived = new Color(Color.HSBtoRGB(hsb[0], hsb[1], value));
        return new Color(derived.getRed(), derived.getGreen(), derived.getBlue(), base.getAlpha());
    }

    private static Color dynamic(Function<Palette, Color> selector) {
        return new DynamicColor(selector);
    }

    /**
     * A colour which resolves against the active palette every time it is painted.
     */
    private static final class DynamicColor extends Color {

        private final Function<Palette, Color> selector;

        private DynamicColor(Function<Palette, Color> selector) {
            super(0);
            this.selector = selector;
        }

        private Color resolve() {
            return selector.apply(palette);
        }

        @Override
        public int getRGB() {
            return resolve().getRGB();
        }

        @Override
        public int getRed() {
            return resolve().getRed();
        }

        @Override
        public int getGreen() {
            return resolve().getGreen();
        }

        @Override
        public int getBlue() {
            return resolve().getBlue();
        }

        @Override
        public int getAlpha() {
            return resolve().getAlpha();
        }

        @Override
        public int getTransparency() {
            return resolve().getTransparency();
        }

        @Override
        public Color brighter() {
            return resolve().brighter();
        }

        @Override
        public Color darker() {
            return resolve().darker();
        }

        @Override
        public String toString() {
            return resolve().toString();
        }
    }
}
