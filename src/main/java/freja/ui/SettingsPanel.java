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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.SpinnerNumberModel;

import laevatain.ARXButton;
import laevatain.ARXSettingsPane;
import laevatain.ARXToggle;

/**
 * Freja's settings page: the reusable {@link ARXSettingsPane} scaffold - rail, anchored sections,
 * glide scrolling and cards - filled with the application's own appearance, font and system
 * settings.
 */
public final class SettingsPanel extends JPanel {

    /** Notifications sent back to the owner after a setting changed. */
    public interface Listener {

        void onFontChanged(float ratio);

        void onThemeChanged();
    }

    private static final String APPEARANCE = "外観";

    private static final String FONTS = "フォント";

    private static final String SYSTEM = "システム";

    /** The number of theme tiles across the appearance picker. */
    private static final int THEME_COLUMNS = 4;

    /**
     * The themes shown before the list is opened up: a spread of well known light and dark
     * palettes, chosen so the twelve tiles already cover most tastes.
     */
    private static final Set<String> RECOMMENDED = Set.of("Arc", "GitHub (Material)", "Solarized Light",
            "Light Owl (Material)", "Gray", "Dracula", "Nord", "Monokai Pro", "One Dark", "Cobalt 2");

    private final Listener listener;

    private final ARXSettingsPane pane = new ARXSettingsPane("設定");

    /** The preview tiles by theme id, so choosing a theme can refresh every badge. */
    private final Map<String, ThemeTile> themeTiles = new LinkedHashMap<>();

    private JPanel themeGrid;

    /** Narrows whatever is in scope by brightness. */
    private JComboBox<String> themeFilter;

    private ARXButton moreButton;

    private JLabel themeCount;

    private boolean expanded;

    private JLabel preview;

    /** The system page mirrors the active theme name, which the tiles can change. */
    private JLabel currentThemeValue;

    private boolean updating;

    public SettingsPanel(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        add(pane, BorderLayout.CENTER);

        appearanceSection();
        fontSection();
        systemSection();
        pane.show(APPEARANCE);
    }

    /** The appearance section: a dozen preview tiles, opening up to every theme on request. */
    private void appearanceSection() {
        for (Theme.ThemeInfo theme : Theme.availableThemes()) {
            ThemeTile tile = new ThemeTile(theme);
            tile.addActionListener(event -> selectTheme(theme));
            themeTiles.put(theme.id(), tile);
        }

        ARXSettingsPane.Section section = pane.addSection(APPEARANCE, "外観", "アプリの配色を選択します。",
                Icons.contrast(17, Theme.MUTED));

        // Scope first, then brightness, then the tiles: each control narrows what appears below it.
        moreButton = new ARXButton("さらに見る", Icons.chevron(14, Theme.MUTED, false),
                ARXButton.Variant.SECONDARY, () -> {
                    expanded = !expanded;
                    showThemes();
                });
        moreButton.setFont(Theme.font(Font.PLAIN, 12));
        themeCount = value("");
        JPanel morePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        morePanel.setOpaque(false);
        morePanel.add(themeCount);
        morePanel.add(moreButton);
        section.addCard(ARXSettingsPane.rows(ARXSettingsPane.row("すべてのテーマ",
                "代表的な配色に絞って表示します", morePanel)));

        // The filter narrows whatever is currently in scope: the picks, or everything.
        themeFilter = new JComboBox<>(new String[] {"すべて", "ライト系", "ダーク系"});
        themeFilter.setFont(Theme.font(Font.PLAIN, 13));
        themeFilter.setPreferredSize(new Dimension(160, 30));
        themeFilter.addActionListener(event -> showThemes());
        section.addCard(ARXSettingsPane.rows(ARXSettingsPane.row("テーマの絞り込み",
                "明るさに応じて候補を絞り込みます", themeFilter)));

        themeGrid = new JPanel(new GridLayout(1, THEME_COLUMNS, 12, 12));
        themeGrid.setOpaque(false);
        section.addContent(themeGrid);
        showThemes();
    }

    /** Rebuilds the tile grid from the current scope (picks or all) and the brightness filter. */
    private void showThemes() {
        String active = Theme.themeId();
        String filter = themeFilter == null ? "すべて" : (String) themeFilter.getSelectedItem();
        List<ThemeTile> visible = new ArrayList<>();
        for (ThemeTile tile : themeTiles.values()) {
            Theme.ThemeInfo theme = tile.theme();
            // The two hand tuned palettes are always in reach, as is the live theme even when it is
            // not one of the picks.
            boolean inScope = expanded || theme.builtin() || theme.id().equals(active)
                    || RECOMMENDED.contains(theme.name());
            boolean matches = "すべて".equals(filter) || theme.dark() == "ダーク系".equals(filter);
            if (inScope && matches) {
                visible.add(tile);
            }
        }
        int rows = Math.max(1, (visible.size() + THEME_COLUMNS - 1) / THEME_COLUMNS);
        themeGrid.setLayout(new GridLayout(rows, THEME_COLUMNS, 12, 12));
        themeGrid.removeAll();
        for (ThemeTile tile : visible) {
            themeGrid.add(tile);
        }
        moreButton.setText(expanded ? "代表のみ表示" : "さらに見る");
        moreButton.setIcon(Icons.chevron(14, Theme.MUTED, expanded));
        themeCount.setText(visible.size() + " / " + themeTiles.size() + " 件");
        themeGrid.revalidate();
        themeGrid.repaint();
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
        if (currentThemeValue != null) {
            currentThemeValue.setText(themeName());
            currentThemeValue.repaint();
        }
    }

    /** Repaints the tiles so the badge follows the now active theme. */
    private void syncThemeTiles() {
        for (ThemeTile tile : themeTiles.values()) {
            tile.repaint();
        }
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

        family.addActionListener(event -> {
            if (updating) {
                return;
            }
            if (family.getSelectedItem() instanceof String chosen) {
                Theme.setFont(chosen, Theme.fontSize());
                refreshPreview();
                listener.onFontChanged(1f);
            }
        });
        size.addChangeListener(event -> {
            if (updating) {
                return;
            }
            int value = (Integer) size.getValue();
            float ratio = (float) value / Theme.fontSize();
            Object chosen = family.getSelectedItem();
            Theme.setFont(chosen instanceof String string ? string : null, value);
            refreshPreview();
            listener.onFontChanged(ratio);
        });

        preview = new JLabel("Aa Bb あいうえお カタカナ 0123456789 — Freja");
        preview.setForeground(Theme.TEXT);
        preview.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel sample = new JPanel(new BorderLayout());
        sample.setOpaque(false);
        sample.setBorder(BorderFactory.createEmptyBorder(10, 16, 18, 16));
        sample.add(preview, BorderLayout.CENTER);

        ARXSettingsPane.Section section = pane.addSection(FONTS, "フォント", "文字の書体と大きさを調整します。",
                Icons.letter("A", 17, Theme.MUTED));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("フォント ファミリー", "使用する書体", family),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("フォント サイズ", "文字の基準サイズ（ポイント）", size)));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("プレビュー", "現在の設定の確認結果", Box.createHorizontalGlue()),
                sample));
        refreshPreview();
    }

    /** Re-renders the sample text with the configured family and size. */
    private void refreshPreview() {
        if (preview != null) {
            preview.setFont(Theme.font(Font.PLAIN, 15));
            preview.repaint();
        }
    }

    /** The system section: update controls, app information and restart. */
    private void systemSection() {
        ARXToggle startup = new ARXToggle(false);
        JLabel state = new JLabel("オフ");
        state.setFont(Theme.font(Font.PLAIN, 12));
        state.setForeground(Theme.MUTED);
        startup.addActionListener(event -> state.setText(startup.isSelected() ? "オン" : "オフ"));
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        togglePanel.setOpaque(false);
        togglePanel.add(state);
        togglePanel.add(startup);

        ARXButton check = new ARXButton("アップデートを確認", Icons.search(14, Theme.MUTED),
                ARXButton.Variant.SECONDARY, () -> JOptionPane.showMessageDialog(this,
                        "アップデート確認機能は現在準備中です。", "アップデート", JOptionPane.INFORMATION_MESSAGE));
        check.setFont(Theme.font(Font.PLAIN, 12));

        ARXButton restart = new ARXButton("再起動する", Icons.refresh(14, Theme.ACCENT_DARK),
                ARXButton.Variant.TINTED, freja.Freja::restart);
        restart.setFont(Theme.font(Font.PLAIN, 12));

        currentThemeValue = value(themeName());
        ARXSettingsPane.Section section = pane.addSection(SYSTEM, "システム",
                "アップデート確認とアプリの情報、再起動を管理します。", Icons.settings(17, Theme.MUTED));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("起動時にアップデートを確認する", "起動時に新しいバージョンを確認します", togglePanel),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("アップデートの確認", "現在のバージョンを確認します", check),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("アプリケーションを再起動", "設定を反映してアプリを再起動します", restart)));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("現在のテーマ", "", currentThemeValue),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("アプリケーション", "", value("Freja " + freja.Freja.VERSION
                        + " (2026-11-24)")),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("オペレーティングシステム", "", value(System.getProperty("os.name") + " "
                        + System.getProperty("os.arch") + " " + System.getProperty("os.version"))),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("Javaランタイム", "", value(System.getProperty("java.version")))));
    }

    /** The display name of the active theme, suffixed with its light or dark character. */
    private static String themeName() {
        return Theme.availableThemes().stream()
                .filter(theme -> theme.id().equals(Theme.themeId()))
                .findFirst()
                .map(theme -> theme.name() + "（" + (theme.dark() ? "ダーク" : "ライト") + "）")
                .orElse("ライト");
    }

    /** A muted, right-aligned value label, resolved against the Freja palette. */
    private JLabel value(String text) {
        JLabel label = ARXSettingsPane.value(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    /** A theme preview: a miniature window painted in the shades of the theme it offers. */
    private static final class ThemeTile extends JButton {

        private final Theme.ThemeInfo theme;

        private final Theme.Preview colors;

        private boolean hovered;

        /** The theme this tile offers. */
        Theme.ThemeInfo theme() {
            return theme;
        }

        ThemeTile(Theme.ThemeInfo theme) {
            super(theme.name());
            this.theme = theme;
            colors = Theme.preview(theme);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(160, 112));
            setToolTipText(theme.name() + "（" + (theme.dark() ? "ダーク" : "ライト") + "）");
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

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
                g.drawString(shrink(g, theme.name(), w - 24), 12, h - 12);

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
            if (metrics.stringWidth(text) <= available) {
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
