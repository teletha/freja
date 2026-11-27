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
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javax.swing.ToolTipManager;
import javax.swing.UIDefaults;
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

    /** The identifier of the active theme: one of the built-ins or an IntelliJ theme class. */
    private static final String THEME_KEY = "theme";

    /** The hand tuned light palette, offered as a quick pick. */
    public static final String THEME_LIGHT = "light";

    /** The hand tuned dark palette, offered as a quick pick. */
    public static final String THEME_DARK = "dark";

    private static final String FONT_FAMILY_KEY = "font.family";

    private static final String FONT_SIZE_KEY = "font.size";

    private static final String FILE_LIST_FONT_SIZE_KEY = "font.fileList.size";

    private static final String FILE_LIST_ROW_HEIGHT_KEY = "fileList.rowHeight";

    /** The size the individual font calls in this application were designed around. */
    private static final int BASE_FONT_SIZE = 13;

    /** Default size of the file manager's list, independent of the general font size. */
    public static final int BASE_FILE_LIST_FONT_SIZE = 12;

    /** Default height of one file manager list row, in points at the base font size. */
    public static final int BASE_FILE_LIST_ROW_HEIGHT = 30;

    private record Palette(Color background, Color surface, Color sidebar, Color border, Color text, Color muted, Color faint, Color accent, Color accentSoft, Color accentDark, Color gold, Color danger, Color hover, Color viewer, Color viewerPanel) {
    }

    private static final Palette LIGHT = new Palette(new Color(0xF7F8FA), Color.WHITE, new Color(0xF1F3F6), new Color(0xE2E5EA), new Color(0x25282D), new Color(0x7B818B), new Color(0xA8ADB5), new Color(0x2E6BE6), new Color(0xE8F0FE), new Color(0x1F52B8), new Color(0xE9A800), new Color(0xC43D3D), new Color(0xE7EAEF), new Color(0x0D0E10), new Color(0x1A1C20));

    private static final Palette DARK = new Palette(new Color(0x15171B), new Color(0x1E2127), new Color(0x191C21), new Color(0x30343C), new Color(0xE7E9ED), new Color(0x9AA1AA), new Color(0x666D77), new Color(0x6C9BFF), new Color(0x25344D), new Color(0xB8CDFF), new Color(0xF1B64A), new Color(0xFF7676), new Color(0x282C33), new Color(0x0D0E10), new Color(0x1A1C20));

    private static volatile Palette palette = LIGHT;

    private static volatile boolean dark;

    private static volatile String themeId = THEME_LIGHT;

    /** The IntelliJ themes found on the class path; populated on first use, empty without them. */
    private static List<ThemeInfo> intellijThemes;

    /**
     * A theme the user can pick: an identifier, a display name and whether it is dark. Built-ins
     * carry a short id, IntelliJ themes their look and feel class name.
     */
    public record ThemeInfo(String id, String name, boolean dark, boolean builtin) {
    }

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

    /** Repeats a palette entry with a fixed alpha, used for selection washes. */
    private static Color tint(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * The accent tint which paints a selected navigation row. A stronger tint than
     * {@link #ACCENT_SOFT} so the active row is obvious at a glance.
     */
    public static final Color SELECTION = dynamic(p -> tint(p.accent, 80));

    /** The accent used for a tinted, secondary-emphasis button. */
    public static final Color TINT_HOVER = dynamic(p -> mix(p.accentSoft, p.accent, 0.18));

    /** The full screen viewer background. */
    public static final Color VIEWER = dynamic(p -> p.viewer);

    /** The full screen viewer toolbar and footer. */
    public static final Color VIEWER_PANEL = dynamic(p -> p.viewerPanel);

    private static String fontFamily;

    private static int fontSize = BASE_FONT_SIZE;

    private static int fileListFontSize = BASE_FILE_LIST_FONT_SIZE;

    private static int fileListRowHeight = BASE_FILE_LIST_ROW_HEIGHT;

    private Theme() {
    }

    /**
     * Installs the persisted look and feel.
     */
    public static void install() {
        dark = PREFERENCES.getBoolean(DARK_KEY, false);
        themeId = PREFERENCES.get(THEME_KEY, dark ? THEME_DARK : THEME_LIGHT);
        dark = isDarkTheme(themeId);
        palette = dark ? DARK : LIGHT;
        fontFamily = PREFERENCES.get(FONT_FAMILY_KEY, detectJapaneseFont());
        fontSize = Math.max(8, Math.min(28, PREFERENCES.getInt(FONT_SIZE_KEY, BASE_FONT_SIZE)));
        fileListFontSize = Math.max(8, Math.min(28, PREFERENCES.getInt(FILE_LIST_FONT_SIZE_KEY, BASE_FILE_LIST_FONT_SIZE)));
        fileListRowHeight = Math.max(20, Math.min(80, PREFERENCES.getInt(FILE_LIST_ROW_HEIGHT_KEY, BASE_FILE_LIST_ROW_HEIGHT)));
        applyLookAndFeel();
    }

    private static String detectJapaneseFont() {
        Set<String> available = new HashSet<>(Arrays
                .asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
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
        setTheme(value ? THEME_DARK : THEME_LIGHT);
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

    /** The identifier of the active theme: a built-in id or an IntelliJ theme class name. */
    public static String themeId() {
        return themeId;
    }

    /**
     * Switches to the theme with the given id and persists the choice. The look and feel goes in
     * first, so a custom theme's own defaults can supply the shades the application paints with.
     *
     * @param id an id from {@link #availableThemes()}
     */
    public static void setTheme(String id) {
        if (id == null || id.equals(themeId)) {
            return;
        }
        themeId = id;
        dark = isDarkTheme(id);
        PREFERENCES.put(THEME_KEY, id);
        // Keep the legacy flag in sync: earlier releases stored only this, and it doubles as the
        // fallback when a theme id turns out to be unavailable.
        PREFERENCES.putBoolean(DARK_KEY, dark);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
        applyLookAndFeel();
    }

    /** The themes on offer: the two hand tuned palettes plus every IntelliJ theme available. */
    public static List<ThemeInfo> availableThemes() {
        List<ThemeInfo> themes = new ArrayList<>();
        themes.add(new ThemeInfo(THEME_LIGHT, "Light", false, true));
        themes.add(new ThemeInfo(THEME_DARK, "Dark", true, true));
        themes.addAll(intellijThemes());
        return themes;
    }

    /**
     * The IntelliJ themes found on the class path. Looked up reflectively, so the optional
     * flatlaf-intellij-themes artifact stays optional at build and at run time.
     */
    public static List<ThemeInfo> intellijThemes() {
        List<ThemeInfo> cached = intellijThemes;
        if (cached != null) {
            return cached;
        }
        List<ThemeInfo> found = new ArrayList<>();
        try {
            Object[] infos = (Object[]) Class.forName("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes").getField("INFOS").get(null);
            for (Object info : infos) {
                if (info instanceof UIManager.LookAndFeelInfo entry) {
                    boolean isDark = (Boolean) entry.getClass().getMethod("isDark").invoke(entry);
                    found.add(new ThemeInfo(entry.getClassName(), entry.getName(), isDark, false));
                }
            }
            found.sort(java.util.Comparator.comparing(ThemeInfo::name));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Without the extra artifact the two built-in palettes are all there is.
        }
        intellijThemes = found;
        return found;
    }

    /** Whether the theme behind an id is dark, which picks the fallback palette and icons. */
    public static boolean isDarkTheme(String id) {
        if (THEME_DARK.equals(id)) {
            return true;
        }
        if (THEME_LIGHT.equals(id)) {
            return false;
        }
        for (ThemeInfo theme : intellijThemes()) {
            if (theme.id().equals(id)) {
                return theme.dark();
            }
        }
        return false;
    }

    /** The handful of shades that stand for a theme in the appearance picker. */
    public record Preview(Color background, Color surface, Color sidebar, Color text, Color accent) {
    }

    private static final Map<String, Preview> PREVIEWS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The shades a theme would paint with, resolved without installing it: a look and feel hands
     * over its defaults as soon as it is constructed, so the picker can preview every theme at
     * once.
     */
    public static Preview preview(ThemeInfo theme) {
        return PREVIEWS.computeIfAbsent(theme.id(), id -> readPreview(theme, id));
    }

    private static Preview readPreview(ThemeInfo theme, String id) {
        Palette base = theme.dark() ? DARK : LIGHT;
        if (theme.builtin()) {
            return new Preview(base.background(), base.surface(), base.sidebar(), base.text(), base.accent());
        }
        try {
            if (Class.forName(id).getDeclaredConstructor().newInstance() instanceof javax.swing.LookAndFeel laf) {
                UIDefaults defaults = laf.getDefaults();
                Color background = color(defaults, "Panel.background", base.background());
                Color text = color(defaults, "Label.foreground", base.text());
                return new Preview(background, color(defaults, "List.background", base.surface()), mix(background, text, theme.dark() ? 0.05
                        : 0.035), text, color(defaults, "Component.accentColor", base.accent()));
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return new Preview(base.background(), base.surface(), base.sidebar(), base.text(), base.accent());
    }

    private static Color color(UIDefaults defaults, String key, Color fallback) {
        Color value = defaults == null ? null : defaults.getColor(key);
        // Copy out of the UIResource: the instance belongs to a look and feel that is not
        // installed.
        return value == null ? fallback : new Color(value.getRGB(), true);
    }

    /** Instantiates the look and feel behind an id, or null when the theme is unavailable. */
    private static javax.swing.LookAndFeel createLookAndFeel(String id) {
        if (THEME_LIGHT.equals(id)) {
            return new FlatLightLaf();
        }
        if (THEME_DARK.equals(id)) {
            return new FlatDarkLaf();
        }
        try {
            if (Class.forName(id).getDeclaredConstructor().newInstance() instanceof javax.swing.LookAndFeel laf) {
                return laf;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    /**
     * Reads the application's shades from the freshly installed look and feel, so rails, cards and
     * hairlines follow the chosen theme instead of the hand tuned palette. Colours are copied out
     * of
     * the look and feel defaults: UIResource instances belong to the previous theme.
     */
    private static Palette readPalette(Palette base, boolean isDark) {
        Color background = uiColor("Panel.background", base.background);
        Color text = uiColor("Label.foreground", base.text);
        Color muted = uiColor("Label.disabledForeground", base.muted);
        Color accent = uiColor("Component.accentColor", base.accent);
        return new Palette(background, uiColor("List.background", base.surface), mix(background, text, isDark ? 0.05
                : 0.035), uiColor("Component.borderColor", base.border), text, muted, mix(muted, background, 0.45), accent, mix(accent, background, isDark
                        ? 0.78
                        : 0.86), isDark ? mix(accent, Color.WHITE, 0.25)
                                : mix(accent, Color.BLACK, 0.18), base.gold, base.danger, mix(background, text, isDark ? 0.14
                                        : 0.09), base.viewer, base.viewerPanel);
    }

    private static Color uiColor(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : new Color(value.getRGB(), true);
    }

    private static void applyLookAndFeel() {
        javax.swing.LookAndFeel theme = createLookAndFeel(themeId);
        if (theme == null) {
            themeId = dark ? THEME_DARK : THEME_LIGHT;
            theme = createLookAndFeel(themeId);
        }
        dark = isDarkTheme(themeId);
        FlatLaf.setup(theme);
        // Tooltips should appear quickly enough to feel responsive during scanning and browsing,
        // and stay long enough to read paths and explanations without the pointer moving.
        ToolTipManager.sharedInstance().setInitialDelay(500);
        ToolTipManager.sharedInstance().setReshowDelay(1_000);
        ToolTipManager.sharedInstance().setDismissDelay(10_000);
        Palette base = dark ? DARK : LIGHT;
        boolean builtin = THEME_LIGHT.equals(themeId) || THEME_DARK.equals(themeId);
        palette = builtin ? base : readPalette(base, dark);
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ToolBar.background", palette.sidebar);
        // FlatLaf paints toolbar buttons with its own toolbar hover colour. Point both that key and
        // the ARX blended hover at the same shade derived from the sidebar background. These are
        // dynamic colours, so components which captured them while being built still follow a
        // later light/dark switch instead of freezing the palette of their birth.
        UIManager.put("ToolBar.background", SIDEBAR);
        Color hover = hoverShade(palette.sidebar);
        UIManager.put("Button.toolbar.hoverBackground", hover);
        UIManager.put("Button.toolbar.hoverForeground", TEXT);
        UIManager.put("ARXButton.hoverBackground", dynamic(p -> hoverShade(p.sidebar)));
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Button.defaultBackground", ACCENT);
        UIManager.put("Button.defaultForeground", Color.WHITE);
        UIManager.put("Button.defaultHoverBackground", ACCENT_DARK);
        UIManager.put("Button.defaultHoverForeground", Color.WHITE);
        // A softer accent role for emphasized-but-not-primary actions (see ARXButton.TINTED).
        UIManager.put("ARXButton.tintedBackground", ACCENT_SOFT);
        UIManager.put("ARXButton.tintedHoverBackground", TINT_HOVER);
        UIManager.put("ARXButton.tintedForeground", ACCENT_DARK);
        UIManager.put("ARXButton.tintedHoverForeground", ACCENT);
        // Toggle buttons reuse the navigation list's selection wash: enough colour to read as
        // active, but light enough that the normal text colour keeps strong contrast.
        UIManager.put("ARXButton.selectedBackground", SELECTION);
        UIManager.put("ARXButton.selectedHoverBackground", SELECTION);
        UIManager.put("ARXButton.selectedForeground", TEXT);
        UIManager.put("Component.selectionBackground", ACCENT_SOFT);
        UIManager.put("Component.selectionForeground", TEXT);
        UIManager.put("defaultFont", font(Font.PLAIN, 13));
        // Give context-menu rows a little more breathing room. These must be real Insets
        // instances: a plain String would make UIManager.getInsets() return null and drop the
        // margin entirely. Vertical 3->4 adds 2px per row; horizontal spacing is untouched.
        Insets menuMargin = new Insets(6, 6, 6, 6);
        UIManager.put("MenuItem.margin", menuMargin);
        UIManager.put("CheckBoxMenuItem.margin", menuMargin);
        UIManager.put("RadioButtonMenuItem.margin", menuMargin);
        UIManager.put("Menu.margin", menuMargin);
        // FlatLaf rolls menu items over with the strong accent selection colour, which swallows the
        // small tinted icons. Use the neutral hover wash from the palette instead and keep the
        // normal foreground so labels and icons stay equally readable in light and dark themes.
        UIManager.put("MenuItem.selectionBackground", HOVER);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("Menu.selectionBackground", HOVER);
        UIManager.put("Menu.selectionForeground", TEXT);
        UIManager.put("CheckBoxMenuItem.selectionBackground", HOVER);
        UIManager.put("CheckBoxMenuItem.selectionForeground", TEXT);
        UIManager.put("RadioButtonMenuItem.selectionBackground", HOVER);
        UIManager.put("RadioButtonMenuItem.selectionForeground", TEXT);
        // The ARX components read their palette and type scale from these keys instead of
        // importing this class, so laevatain stays reusable outside Freja while still following
        // every light/dark switch and font change.
        UIManager.put("ARX.background", BACKGROUND);
        UIManager.put("ARX.surface", SURFACE);
        UIManager.put("ARX.navBackground", SIDEBAR);
        UIManager.put("ARX.hairline", BORDER);
        UIManager.put("ARX.text", TEXT);
        UIManager.put("ARX.textMuted", MUTED);
        UIManager.put("ARX.selection", SELECTION);
        UIManager.put("ARX.hover", HOVER);
        UIManager.put("ARX.font.title", font(Font.BOLD, 19));
        UIManager.put("ARX.font.group", font(Font.BOLD, 13));
        UIManager.put("ARX.font.subtitle", font(Font.PLAIN, 12));
        UIManager.put("ARX.font.label", font(Font.PLAIN, 13));
        UIManager.put("ARX.font.hint", font(Font.PLAIN, 11));
        UIManager.put("ARX.font.value", font(Font.PLAIN, 12));
        UIManager.put("ARX.font.row", font(Font.PLAIN, 13));
        UIManager.put("ARX.font.annotation", font(Font.PLAIN, 11));
        // One logical size for every navigation row icon, shared by ARXNavList and the folder list.
        // Icons apply the display scale themselves, so the value here stays unscaled.
        UIManager.put("ARX.navIconSize", 15);
        UIManager.put("ARX.font.section", font(Font.BOLD, 11));
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

    /** Returns the configured file list font size. */
    public static int fileListFontSize() {
        return fileListFontSize;
    }

    /** Returns the file list font, scaled with the application's base size. */
    public static Font fileListFont() {
        return font(Font.PLAIN, fileListFontSize);
    }

    /** Changes the file list font size and persists the choice. */
    public static void setFileListFontSize(int size) {
        fileListFontSize = Math.max(8, Math.min(28, size));
        PREFERENCES.putInt(FILE_LIST_FONT_SIZE_KEY, fileListFontSize);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }

    /** Returns the configured file list row height. */
    public static int fileListRowHeight() {
        return fileListRowHeight;
    }

    /** Returns the row height scaled with the application's base font size. */
    public static int scaledFileListRowHeight() {
        return Math.max(16, Math.round(fileListRowHeight * (float) fontSize / BASE_FONT_SIZE));
    }

    /** Changes the file list row height and persists the choice. */
    public static void setFileListRowHeight(int height) {
        fileListRowHeight = Math.max(20, Math.min(80, height));
        PREFERENCES.putInt(FILE_LIST_ROW_HEIGHT_KEY, fileListRowHeight);
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
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
        return new Color((int) Math.round(first.getRed() * (1 - value) + second.getRed() * value), (int) Math
                .round(first.getGreen() * (1 - value) + second.getGreen() * value), (int) Math
                        .round(first.getBlue() * (1 - value) + second.getBlue() * value));
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
