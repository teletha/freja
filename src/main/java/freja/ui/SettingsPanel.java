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
import java.util.Map;

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

    /** The fixed palettes the theme tiles draw, independent of the active theme. */
    private static final Map<Boolean, Color[]> PALETTES = Map.of(Boolean.FALSE, new Color[] {
            new Color(0xF7F8FA), Color.WHITE, new Color(0xE2E5EA), new Color(0x25282D), new Color(0x7B818B),
            new Color(0x2E6BE6) }, Boolean.TRUE, new Color[] {
            new Color(0x15171B), new Color(0x1E2127), new Color(0x30343C), new Color(0xE7E9ED), new Color(0x9AA1AA),
            new Color(0x6C9BFF) });

    private final Listener listener;

    private final ARXSettingsPane pane = new ARXSettingsPane("設定");

    private ThemeTile lightTile;

    private ThemeTile darkTile;

    private JLabel preview;

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

    /** The appearance section: a pair of clickable theme preview tiles. */
    private void appearanceSection() {
        lightTile = new ThemeTile(false);
        darkTile = new ThemeTile(true);
        lightTile.addActionListener(event -> selectTheme(false));
        darkTile.addActionListener(event -> selectTheme(true));
        JPanel tiles = new JPanel(new GridLayout(1, 2, 14, 0));
        tiles.setOpaque(false);
        tiles.add(lightTile);
        tiles.add(darkTile);
        syncThemeTiles();

        ARXSettingsPane.Section section = pane.addSection(APPEARANCE, "外観", "アプリの配色を選択します。",
                Icons.contrast(17, Theme.MUTED));
        section.addContent(tiles);
    }

    private void selectTheme(boolean dark) {
        Theme.setDark(dark);
        syncThemeTiles();
        listener.onThemeChanged();
    }

    private void syncThemeTiles() {
        boolean dark = Theme.isDark();
        lightTile.setSelectedState(!dark);
        darkTile.setSelectedState(dark);
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

        ARXSettingsPane.Section section = pane.addSection(SYSTEM, "システム",
                "アップデート確認とアプリの情報、再起動を管理します。", Icons.settings(17, Theme.MUTED));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("起動時にアップデートを確認する", "起動時に新しいバージョンを確認します", togglePanel),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("アップデートの確認", "現在のバージョンを確認します", check),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("アプリケーションを再起動", "設定を反映してアプリを再起動します", restart)));
        section.addCard(ARXSettingsPane.rows(
                ARXSettingsPane.row("アプリケーション", "", value("Freja " + freja.Freja.VERSION
                        + " (2026-11-24)")),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("オペレーティングシステム", "", value(System.getProperty("os.name") + " "
                        + System.getProperty("os.arch") + " " + System.getProperty("os.version"))),
                ARXSettingsPane.divider(),
                ARXSettingsPane.row("Javaランタイム", "", value(System.getProperty("java.version")))));
    }

    /** A muted, right-aligned value label, resolved against the Freja palette. */
    private JLabel value(String text) {
        JLabel label = ARXSettingsPane.value(text);
        label.setForeground(Theme.MUTED);
        return label;
    }

    /** A rounded, clickable theme preview which paints a miniature window in its palette. */
    private final class ThemeTile extends JButton {

        private final Color[] palette;

        private final boolean dark;

        private boolean selectedState;

        private boolean hovered;

        ThemeTile(boolean dark) {
            super(dark ? "ダーク" : "ライト");
            this.dark = dark;
            palette = PALETTES.get(dark);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(230, 140));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
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

        void setSelectedState(boolean value) {
            selectedState = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g.setColor(palette[1]);
                g.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                g.setColor(palette[0]);
                g.fill(new RoundRectangle2D.Float(11, 10, w - 22, 44, 10, 10));
                g.setColor(palette[1]);
                g.fill(new RoundRectangle2D.Float(15, 14, w - 30, 36, 8, 8));
                g.setColor(palette[5]);
                g.fillOval(22, 26, 12, 12);
                g.setColor(palette[4]);
                g.fillRoundRect(44, 27, 66, 5, 4, 4);
                g.setColor(palette[3]);
                g.fillRoundRect(44, 36, 40, 5, 4, 4);
                g.setColor(palette[2]);
                g.fillRoundRect(15, 62, w - 30, 44, 8, 8);
                g.setColor(palette[4]);
                g.fillRoundRect(21, 68, 44, 5, 4, 4);
                g.setColor(palette[3]);
                g.fillRoundRect(21, 78, 30, 5, 4, 4);
                g.setFont(Theme.font(selectedState ? Font.BOLD : Font.PLAIN, 13));
                g.setColor(palette[3]);
                g.drawString(dark ? "ダーク" : "ライト", 21, 122);
                if (selectedState) {
                    g.setColor(palette[5]);
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                    g.fillOval(w - 40, h - 40, 26, 26);
                    Icons.check(16, Color.WHITE).paintIcon(this, g, w - 35, h - 35);
                } else if (hovered) {
                    g.setColor(palette[2]);
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                } else {
                    g.setColor(Theme.BORDER);
                    g.setStroke(new java.awt.BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                }
            } finally {
                g.dispose();
            }
        }
    }
}
