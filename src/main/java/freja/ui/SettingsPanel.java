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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

import freja.util.ArchiveSettings;
import kiss.I;
import laevatain.ARXButton;
import laevatain.ARXComboBox;
import laevatain.ARXLabel;
import laevatain.ARXSettingsPane;
import laevatain.ARXTextBinding;
import laevatain.ARXToggle;
import laevatain.ChooserLocations;
import laevatain.util.Language;

/**
 * Freja's settings page: the reusable {@link ARXSettingsPane} scaffold - searchable rail, anchored
 * sections, glide scrolling and cards - filled with the application's appearance, font, language
 * and system settings. Captions are written in English and translated by
 * {@link I#translate(String, Object...)},
 * so both the page and its search follow the language picked on the language section.
 */
public final class SettingsPanel extends JPanel {

    /** Notifications sent back to the owner after a setting changed. */
    public interface Listener {

        void onFontChanged(float ratio);

        /** Called when the file manager's list font size changes on its own. */
        default void onFileListFontChanged() {
        }

        /** Called when the file manager's row height changes on its own. */
        default void onFileListRowHeightChanged() {
        }

        void onThemeChanged();

        /** Called when the selected window icon changes; windows can refresh their icon here. */
        default void onAppIconChanged() {
        }

        void onManageFolders();
    }

    private static final String APPEARANCE = "appearance";

    private static final String FONTS = "fonts";

    private static final String LANGUAGE = "language";

    private static final String DATA_SCAN = "dataScan";

    private static final String SYSTEM = "system";

    /** The number of theme tiles across the appearance picker. */
    private static final int THEME_COLUMNS = 4;

    /**
     * The themes shown before the list is opened up: a spread of well known light and dark
     * palettes, chosen so the twelve tiles already cover most tastes.
     */
    private static final Set<String> RECOMMENDED = Set
            .of("Arc", "GitHub (Material)", "Solarized Light", "Light Owl (Material)", "Gray", "Dracula", "Nord", "Monokai Pro", "One Dark", "Cobalt 2");

    /** The scope of the tile grid: the picks, or everything. */
    private static final String[] THEMES = {"All themes", "Light themes", "Dark themes"};

    private final Listener listener;

    private final ARXSettingsPane pane = new ARXSettingsPane(I.translate("Settings"));

    /** The preview tiles by theme id, so choosing a theme can refresh every badge. */
    private final Map<String, ThemeTile> themeTiles = new LinkedHashMap<>();

    private JPanel themeGrid;

    /** Current number of tile rows, used to pin the grid's height against stale parent sizes. */
    private int themeRows = 1;

    private ARXButton moreButton;

    private JLabel themeCount;

    private ARXComboBox themeScope;

    private boolean expanded;

    private ARXComboBox languages;

    private JLabel localeValue;

    private JLabel preview;

    public SettingsPanel(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        add(pane, BorderLayout.CENTER);

        // The search box narrows the page to the rows which answer to it, in any language.
        pane.search(ARXSettingsPane.Text.of("Search settings"), ARXSettingsPane.Text.of("No settings match your search"));
        backupActions();

        appearanceSection();
        fontSection();
        languageSection();
        dataScanSection();
        systemSection();
        pane.show(APPEARANCE);
    }

    /** Two compact buttons beside the search field: write the whole profile out, or restore one. */
    private void backupActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(iconButton(Icons.exportSettings(15, Theme.MUTED), "Export settings", this::exportSettings));
        actions.add(iconButton(Icons.importSettings(15, Theme.MUTED), "Import settings", this::importSettings));
        pane.searchAccessory(actions);
    }

    private ARXButton iconButton(Icon icon, String sentence, Runnable action) {
        ARXButton button = new ARXButton((String) null, icon, ARXButton.Variant.SECONDARY, action);
        // Translations arrive asynchronously (and may change with the language), so bind the live
        // variable instead of freezing the English text with a single get().
        button.setToolTipText(I.translate(sentence));
        button.setPreferredSize(new Dimension(30, 30));
        return button;
    }

    /** Saves every application preference to a file chosen by the user. */
    private void exportSettings() {
        JFileChooser chooser = new JFileChooser(ChooserLocations.getOrNull("settingsBackup"));
        chooser.setDialogTitle(I.translate("Export settings").get());
        chooser.setSelectedFile(new File("freja-settings.properties"));
        chooser.setFileFilter(new FileNameExtensionFilter(I.translate("Settings backup").get(), "properties"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        ChooserLocations.remember("settingsBackup", chosen.getParentFile());
        Path file = chosen.toPath();
        if (!file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".properties")) {
            file = Path.of(file + ".properties");
        }
        try {
            SettingsBackup.exportTo(file);
            JOptionPane.showMessageDialog(this, I.translate("Settings exported to %1$s", file).get(), I
                    .translate("Export settings").get(), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            I.error("freja", e);
            JOptionPane.showMessageDialog(this, I.translate("Could not export the settings.").get(), I
                    .translate("Export settings").get(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Replaces the current preferences with a previously exported file. */
    private void importSettings() {
        JFileChooser chooser = new JFileChooser(ChooserLocations.getOrNull("settingsBackup"));
        chooser.setDialogTitle(I.translate("Import settings").get());
        chooser.setFileFilter(new FileNameExtensionFilter(I.translate("Settings backup").get(), "properties"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        ChooserLocations.remember("settingsBackup", chosen.getParentFile());
        int confirm = JOptionPane.showConfirmDialog(this, I
                .translate("Replace all current settings with this backup?").get(), I.translate("Import settings").get(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            SettingsBackup.importFrom(chosen.toPath());
            // Theme and language are read from Preferences here, so they change immediately; the
            // window layout is read at startup and needs the restart offered below.
            Theme.install();
            Language.install();
            refreshTheme();
            listener.onThemeChanged();
            int restart = JOptionPane.showConfirmDialog(this, I
                    .translate("Settings imported. Restart Freja to apply every setting?").get(), I.translate("Import settings").get(), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (restart == JOptionPane.YES_OPTION) {
                freja.Freja.restart();
            }
        } catch (IOException e) {
            I.error("freja", e);
            JOptionPane.showMessageDialog(this, I.translate("Could not import the settings.").get(), I
                    .translate("Import settings").get(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /** The appearance section: a dozen preview tiles, opening up to every theme on request. */
    private void appearanceSection() {
        for (Theme.ThemeInfo theme : Theme.availableThemes()) {
            ThemeTile tile = new ThemeTile(theme);
            tile.addActionListener(event -> selectTheme(theme));
            themeTiles.put(theme.id(), tile);
        }

        ARXSettingsPane.Section section = pane.addSection(APPEARANCE, ARXSettingsPane.Text.of("Appearance"), ARXSettingsPane.Text
                .of("Choose how the application is coloured."), Icons.nav("contrast-circle", Theme.MUTED));

        // Scope first, then brightness, then the tiles: each control narrows what appears below it.
        moreButton = new ARXButton("Show all themes", Icons.chevron(14, Theme.MUTED, false), ARXButton.Variant.SECONDARY, () -> {
            expanded = !expanded;
            showThemes();
        });
        moreButton.setFont(Theme.font(Font.PLAIN, 12));
        themeCount = ARXSettingsPane.value("");
        JPanel morePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        morePanel.setOpaque(false);
        morePanel.add(themeCount);
        morePanel.add(moreButton);
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("All themes"), ARXSettingsPane.Text.of("Show the representative palettes only"), morePanel)
                .keywords("theme", "themes", "palette", "palettes"));

        themeScope = new ARXComboBox();
        themeScope.setSources(THEMES);
        themeScope.setFont(Theme.font(Font.PLAIN, 13));
        themeScope.setPreferredSize(new Dimension(160, 30));
        themeScope.addActionListener(event -> showThemes());
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Filter"), ARXSettingsPane.Text.of("Narrow the candidates by brightness"), themeScope)
                .keywords("filter", "brightness", "light", "dark"));

        themeGrid = new JPanel(new GridLayout(1, THEME_COLUMNS, 12, 12)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.height = gridHeight();
                return size;
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, gridHeight());
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, gridHeight());
            }
        };
        themeGrid.setOpaque(false);
        section.addContent(themeGrid, "theme", "themes", "colour", "color", "palette");
        showThemes();

        // The window icon is selected here and applied at once; the preview makes each design
        // recognisable even before it is chosen.
        JComboBox<AppIcon> appIcons = new JComboBox<>(AppIcon.values());
        appIcons.setSelectedItem(AppIcon.current());
        appIcons.setFont(Theme.font(Font.PLAIN, 13));
        appIcons.setPreferredSize(new Dimension(160, 30));
        appIcons.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof AppIcon icon) {
                    setText(I.translate(icon.label()).get());
                    setIcon(icon.thumbnail(20));
                }
                return this;
            }
        });
        JLabel iconPreview = new JLabel(AppIcon.current().thumbnail(40));
        appIcons.addActionListener(event -> {
            if (appIcons.getSelectedItem() instanceof AppIcon chosen) {
                AppIcon.select(chosen);
                iconPreview.setIcon(chosen.thumbnail(40));
                listener.onAppIconChanged();
            }
        });
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        iconPanel.setOpaque(false);
        iconPanel.add(iconPreview);
        iconPanel.add(appIcons);
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("App icon"), ARXSettingsPane.Text
                        .of("Choose the icon shown in the window and taskbar."), iconPanel)
                .keywords("app", "icon", "logo", "taskbar", "window"));
    }

    /** Rebuilds the tile grid from the current scope (picks or all) and the brightness filter. */
    private void showThemes() {
        String active = Theme.themeId();
        // The scope labels are translated, so the choice is read by index, not by text.
        int scope = themeScope == null ? 0 : Math.max(0, themeScope.getSelectedIndex());
        List<ThemeTile> visible = new java.util.ArrayList<>();
        for (ThemeTile tile : themeTiles.values()) {
            Theme.ThemeInfo theme = tile.theme();
            // The two hand tuned palettes are always in reach, as is the live theme even when it is
            // not one of the picks.
            boolean inScope = expanded || theme.builtin() || theme.id().equals(active) || RECOMMENDED.contains(theme.name());
            boolean matches = scope == 0 || theme.dark() == (scope == 2);
            if (inScope && matches) {
                visible.add(tile);
            }
        }
        int rows = Math.max(1, (visible.size() + THEME_COLUMNS - 1) / THEME_COLUMNS);
        themeRows = rows;
        themeGrid.setLayout(new GridLayout(rows, THEME_COLUMNS, 12, 12));
        themeGrid.removeAll();
        for (ThemeTile tile : visible) {
            themeGrid.add(tile);
        }
        moreButton.setText(I.translate(expanded ? "Show suggested themes" : "Show all themes"));
        moreButton.setIcon(Icons.chevron(14, Theme.MUTED, expanded));
        themeCount.setText(String.format(Language.locale(), "%1$d / %2$d", visible.size(), themeTiles.size()));
        themeGrid.revalidate();
        themeGrid.repaint();
        // The grid's height changed, so every ancestor's preferred size changed with it.
        for (java.awt.Component parent = themeGrid.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof javax.swing.JComponent component) {
                component.revalidate();
                component.repaint();
            }
        }
    }

    /** Height the tile grid insists on, independent of whatever size the parent hands it. */
    private int gridHeight() {
        return themeRows * 112 + Math.max(0, themeRows - 1) * 12;
    }

    private void selectTheme(Theme.ThemeInfo theme) {
        if (theme.id().equals(Theme.themeId())) {
            return;
        }
        Theme.setTheme(theme.id());
        refreshTheme();
        listener.onThemeChanged();
    }

    /**
     * Re-reads the active theme after it changed elsewhere - the toolbar's dark mode button or the
     * menu - so the badges, the visible picks and the system page all agree again.
     */
    public void refreshTheme() {
        syncThemeTiles();
        showThemes();
    }

    /** Repaints the tiles so the badge follows the now active theme. */
    private void syncThemeTiles() {
        for (ThemeTile tile : themeTiles.values()) {
            tile.repaint();
        }
    }

    /** The data scan section: how the library finds and indexes images. */
    private void dataScanSection() {
        // A newly registered archive is indexed on the next scan; the choice is shared with the
        // scanner through Preferences so it takes effect without restarting the application.
        ARXToggle archives = new ARXToggle(ArchiveSettings.scanArchives());
        archives.addActionListener(event -> ArchiveSettings.scanArchives(archives.isSelected()));

        ARXButton manage = new ARXButton(I.translate("Manage watched folders"), Icons
                .folder(15, Theme.MUTED), ARXButton.Variant.SECONDARY, listener::onManageFolders);
        manage.setFont(Theme.font(Font.PLAIN, 12));

        ARXSettingsPane.Section section = pane.addSection(DATA_SCAN, ARXSettingsPane.Text.of("Data scan"), ARXSettingsPane.Text
                .of("Choose how the library scans and indexes images."), Icons.nav("refresh", Theme.MUTED));
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Scan images inside compressed files"), ARXSettingsPane.Text
                        .of("Index photos stored in ZIP and 7z archives."), archives)
                .keywords("zip", "7z", "archive", "compressed", "scan", "photo", "image"), ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Watched folders"), ARXSettingsPane.Text
                        .of("Add or remove folders and rescan them."), manage)
                .keywords("folder", "folders", "watch", "watched", "manage", "rescan"));
    }

    /** The font section: family and size controls plus a live preview card. */
    private void fontSection() {
        JComboBox<String> family = new JComboBox<>(Theme.availableFontFamilies());
        family.setSelectedItem(Theme.fontFamily());
        family.setFont(Theme.font(Font.PLAIN, 13));
        family.setMinimumSize(new Dimension(240, 30));
        family.setPreferredSize(new Dimension(240, 30));
        JSpinner size = new JSpinner(new SpinnerNumberModel(Theme.fontSize(), 8, 28, 1));
        size.setFont(Theme.font(Font.PLAIN, 13));
        size.setPreferredSize(new Dimension(110, 30));
        JSpinner fileListSize = new JSpinner(new SpinnerNumberModel(Theme.fileListFontSize(), 8, 28, 1));
        fileListSize.setFont(Theme.font(Font.PLAIN, 13));
        fileListSize.setPreferredSize(new Dimension(110, 30));
        JSpinner rowHeight = new JSpinner(new SpinnerNumberModel(Theme.fileListRowHeight(), 20, 80, 2));
        rowHeight.setFont(Theme.font(Font.PLAIN, 13));
        rowHeight.setPreferredSize(new Dimension(110, 30));

        family.addActionListener(event -> {
            if (family.getSelectedItem() instanceof String chosen) {
                Theme.setFont(chosen, Theme.fontSize());
                refreshPreview();
                listener.onFontChanged(1f);
            }
        });
        size.addChangeListener(event -> {
            int value = (Integer) size.getValue();
            float ratio = (float) value / Theme.fontSize();
            Object chosen = family.getSelectedItem();
            Theme.setFont(chosen instanceof String string ? string : null, value);
            refreshPreview();
            listener.onFontChanged(ratio);
        });
        fileListSize.addChangeListener(event -> {
            Theme.setFileListFontSize((Integer) fileListSize.getValue());
            listener.onFileListFontChanged();
        });
        rowHeight.addChangeListener(event -> {
            Theme.setFileListRowHeight((Integer) rowHeight.getValue());
            listener.onFileListRowHeightChanged();
        });

        preview = new JLabel("Aa Bb あいうえお カタカナ 0123456789 — Freja");
        preview.setForeground(Theme.TEXT);
        preview.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel sample = new JPanel(new BorderLayout());
        sample.setOpaque(false);
        sample.setBorder(BorderFactory.createEmptyBorder(10, 16, 18, 16));
        sample.add(preview, BorderLayout.CENTER);
        // The specimen is part of the preview row, so it comes and goes with it.
        ARXSettingsPane.Row previewRow = ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Preview"), ARXSettingsPane.Text.of("How the current settings look"), sample)
                .keywords("preview", "sample", "specimen");

        ARXSettingsPane.Section section = pane.addSection(FONTS, ARXSettingsPane.Text.of("Fonts"), ARXSettingsPane.Text
                .of("Adjust the typeface and its base size."), Icons.navLetter("A", Theme.MUTED));
        section.addCard(ARXSettingsPane.row(ARXSettingsPane.Text.of("Font family"), ARXSettingsPane.Text.of("Typeface to use"), family)
                .keywords("font", "typeface", "family"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("Font size"), ARXSettingsPane.Text.of("Base size in points"), size)
                        .keywords("size", "points", "scale"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("File list font size"), ARXSettingsPane.Text.of("Size of the file manager list"), fileListSize)
                        .keywords("file", "list", "table", "size", "points"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("File list row height"), ARXSettingsPane.Text.of("Height of one file manager row"), rowHeight)
                        .keywords("file", "list", "row", "height", "density"));
        section.addCard(previewRow);
        refreshPreview();
    }

    /** Re-renders the sample text with the configured family and size. */
    private void refreshPreview() {
        if (preview != null) {
            preview.setFont(Theme.font(Font.PLAIN, 15));
            preview.repaint();
        }
    }

    /**
     * The language section. Freja writes its interface text in English and {@link Language}
     * translates it into the language chosen here; the first request per sentence goes online, the
     * result is cached, and bound captions - including this page's own - update as they arrive.
     */
    private void languageSection() {
        languages = new ARXComboBox();
        for (Language.Supported language : Language.SUPPORTS) {
            languages.addItem(language.name());
        }
        languages.setSelectedItem(Language.name());
        languages.setFont(Theme.font(Font.PLAIN, 13));
        languages.setPreferredSize(new Dimension(200, 30));
        languages.addActionListener(event -> {
            String display = (String) languages.getSelectedItem();
            for (Language.Supported language : Language.SUPPORTS) {
                if (language.name().equals(display)) {
                    Language.install(language.code());
                    break;
                }
            }
        });

        localeValue = ARXSettingsPane.value(Language.displayName());

        ARXSettingsPane.Section section = pane.addSection(LANGUAGE, ARXSettingsPane.Text.of("Language"), ARXSettingsPane.Text
                .of("Pick the language of the interface."), Icons.navLetter("あ", Theme.MUTED));
        section.addCard(ARXSettingsPane.row(ARXSettingsPane.Text.of("Display language"), ARXSettingsPane.Text
                .of("Menus, buttons and messages are shown in the selected language."), languages)
                .keywords("language", "locale", "translation", "translate"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("Current locale"), ARXSettingsPane.Text
                                .of("Dates and numbers follow the selected region."), localeValue)
                        .keywords("locale", "region", "date", "numbers", "sorting"));
    }

    /** The system section: update controls, app information and restart. */
    private void systemSection() {
        ARXToggle startup = new ARXToggle(false);
        ARXLabel state = new ARXLabel(I.translate("Off"));
        state.setFont(Theme.font(Font.PLAIN, 12));
        state.setForeground(Theme.MUTED);
        startup.addActionListener(event -> {
            state.setText(I.translate(startup.isSelected() ? "On" : "Off"));
            state.repaint();
        });
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        togglePanel.setOpaque(false);
        togglePanel.add(state);
        togglePanel.add(startup);

        ARXButton check = new ARXButton(I.translate("Check now"), Icons
                .search(14, Theme.MUTED), ARXButton.Variant.SECONDARY, () -> JOptionPane
                        .showMessageDialog(this, I.translate("Update checking is currently unavailable.").get(), I.translate("Update")
                                .get(), JOptionPane.INFORMATION_MESSAGE));
        check.setFont(Theme.font(Font.PLAIN, 12));

        ARXButton restart = new ARXButton(I.translate("Restart"), Icons
                .refresh(14, Theme.ACCENT_DARK), ARXButton.Variant.TINTED, freja.Freja::restart);
        restart.setFont(Theme.font(Font.PLAIN, 12));

        ARXSettingsPane.Section section = pane.addSection(SYSTEM, ARXSettingsPane.Text.of("System"), ARXSettingsPane.Text
                .of("Check for updates, view application details or restart Freja."), Icons.nav("cog", Theme.MUTED));
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Check for updates at startup"), ARXSettingsPane.Text
                        .of("Check for a newer version each time Freja starts."), togglePanel)
                .keywords("update", "updates", "startup", "boot"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("Check for updates"), ARXSettingsPane.Text
                                .of("Compare against the latest release"), check)
                        .keywords("update", "updates", "version", "release"), ARXSettingsPane
                                .row(ARXSettingsPane.Text.of("Restart the application"), ARXSettingsPane.Text
                                        .of("Save your changes and restart Freja."), restart)
                                .keywords("restart", "reboot", "reload"));
        section.addCard(ARXSettingsPane
                .row(ARXSettingsPane.Text.of("Application"), "", ARXSettingsPane.value("Freja " + freja.Freja.VERSION + " (2026-11-24)"))
                .keywords("version", "build", "about"), ARXSettingsPane
                        .row(ARXSettingsPane.Text.of("Operating system"), "", ARXSettingsPane.value(System
                                .getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version")))
                        .keywords("os", "windows", "system"), ARXSettingsPane
                                .row(ARXSettingsPane.Text.of("Java runtime"), "", ARXSettingsPane.value(System.getProperty("java.version")))
                                .keywords("java", "jvm", "runtime"));
    }

    /** A theme preview: a miniature window painted in the shades of the theme it offers. */
    private static final class ThemeTile extends JButton {

        private final Theme.ThemeInfo theme;

        private final Theme.Preview colors;

        private final ARXTextBinding textBinding = new ARXTextBinding();

        private final ARXTextBinding tipBinding = new ARXTextBinding();

        private boolean hovered;

        ThemeTile(Theme.ThemeInfo theme) {
            super(theme.name());
            this.theme = theme;
            colors = Theme.preview(theme);
            // Built-in palettes carry a translatable name; third-party themes are proper nouns.
            if (theme.builtin()) {
                textBinding.bind(I.translate(theme.name()), this::setText);
            }
            tipBinding.bind(I.translate(theme.dark() ? "Dark" : "Light"), this::refreshTip);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(160, 112));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }
            });
        }

        /** The theme this tile offers. */
        Theme.ThemeInfo theme() {
            return theme;
        }

        /** The tooltip repeats the caption with the brightness, both in the current language. */
        private void refreshTip(String brightness) {
            super.setToolTipText(getText() + "（" + brightness + "）");
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean selected = theme.id().equals(Theme.themeId());
                int w = getWidth();
                int h = getHeight();
                // The tile follows the live palette; the miniature window inside shows the theme on
                // offer, so the choice is legible before it is applied.
                g.setColor(Theme.SURFACE);
                g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 12, 12));
                paintMockWindow(g, w, h);

                g.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 12));
                g.setColor(Theme.TEXT);
                g.drawString(shrink(g, getText(), w - 24), 12, h - 12);

                if (selected) {
                    g.setColor(Theme.ACCENT);
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 12, 12));
                    g.fillOval(w - 30, 8, 20, 20);
                    Icons.check(12, Color.WHITE).paintIcon(this, g, w - 26, 12);
                } else {
                    g.setColor(hovered ? Theme.ACCENT_SOFT : Theme.BORDER);
                    g.setStroke(new java.awt.BasicStroke(hovered ? 2f : 1f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 12, 12));
                }
            } finally {
                g.dispose();
            }
        }

        /** A tiny window: navigation rail, toolbar strip and a content block. */
        private void paintMockWindow(Graphics2D g, int w, int h) {
            int x = 12;
            int y = 12;
            int width = w - 24;
            int height = h - 24 - 20;
            int rail = Math.max(9, width * 18 / 100);
            int bar = Math.max(12, height * 30 / 100);
            g.setColor(colors.background());
            g.fillRoundRect(x, y, width, height, 8, 8);
            g.setColor(colors.sidebar());
            g.fillRoundRect(x + 3, y + 3, rail, height - 6, 4, 4);
            int cx = x + rail + 6;
            int cw = width - rail - 12;
            g.setColor(colors.surface());
            g.fillRoundRect(cx, y + 3, cw, bar, 4, 4);
            g.fillRoundRect(cx, y + bar + 8, cw, height - bar - 11, 4, 4);
            g.setColor(colors.accent());
            g.fillOval(cx + 6, y + (bar - 8) / 2, 8, 8);
            g.setColor(colors.text());
            g.fillRoundRect(cx + 20, y + bar / 2 - 2, Math.max(10, cw / 3), 4, 2, 2);
            g.fillRoundRect(cx + 8, y + bar + 16, Math.max(10, cw / 4), 4, 2, 2);
        }

        /** Shortens the theme name with an ellipsis when the tile is too narrow for it. */
        private String shrink(Graphics2D g, String text, int available) {
            java.awt.FontMetrics metrics = g.getFontMetrics();
            if (text == null || metrics.stringWidth(text) <= available) {
                return text;
            }
            String shown = text;
            while (shown.length() > 1 && metrics.stringWidth(shown + "…") > available) {
                shown = shown.substring(0, shown.length() - 1);
            }
            return shown + "…";
        }
    }
}
