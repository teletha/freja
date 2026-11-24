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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.SpinnerNumberModel;

import com.formdev.flatlaf.ui.FlatLineBorder;

import laevatain.ARXButton;
import laevatain.ARXToggle;

/**
 * A Windows 11-style settings pane: a pill-shaped navigation rail, theme preview tiles and cards
 * with hairline dividers and a live font preview.
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

    private static final int RAIL_WIDTH = 240;

    /** The fixed palettes the theme tiles draw, independent of the active theme. */
    private static final Map<Boolean, Color[]> PALETTES = Map.of(Boolean.FALSE, new Color[] {
            new Color(0xF7F8FA), Color.WHITE, new Color(0xE2E5EA), new Color(0x25282D), new Color(0x7B818B),
            new Color(0x2E6BE6) }, Boolean.TRUE, new Color[] {
            new Color(0x15171B), new Color(0x1E2127), new Color(0x30343C), new Color(0xE7E9ED), new Color(0x9AA1AA),
            new Color(0x6C9BFF) });

    private final Listener listener;

    private final List<NavItem> items = new ArrayList<>();

    private final JPanel pages = new JPanel(new CardLayout());

    private final JLabel heading = new JLabel(APPEARANCE);

    private final JLabel subtitle = new JLabel();

    private ThemeTile lightTile;

    private ThemeTile darkTile;

    private JLabel preview;

    private boolean updating;

    public SettingsPanel(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BACKGROUND);

        JPanel rail = new JPanel();
        rail.setOpaque(true);
        rail.setBackground(Theme.SIDEBAR);
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBorder(BorderFactory.createEmptyBorder(20, 0, 18, 0));
        rail.setMinimumSize(new Dimension(RAIL_WIDTH, 0));
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, 0));
        rail.setMaximumSize(new Dimension(RAIL_WIDTH, Integer.MAX_VALUE));

        JLabel railTitle = new JLabel("設定");
        railTitle.setFont(Theme.font(Font.BOLD, 15));
        railTitle.setForeground(Theme.MUTED);
        railTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        railTitle.setBorder(BorderFactory.createEmptyBorder(2, 22, 12, 22));
        rail.add(railTitle);
        addItem(rail, APPEARANCE, color -> Icons.appearance(18, color));
        addItem(rail, FONTS, color -> Icons.font(18, color));
        addItem(rail, SYSTEM, color -> Icons.settings(18, color));
        rail.add(Box.createVerticalGlue());

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 18, 30));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        heading.setFont(Theme.font(Font.BOLD, 26));
        heading.setForeground(Theme.TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(heading);
        subtitle.setFont(Theme.font(Font.PLAIN, 12));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        header.add(subtitle);
        content.add(header, BorderLayout.NORTH);

        pages.setOpaque(false);
        pages.add(appearancePage(), APPEARANCE);
        pages.add(fontPage(), FONTS);
        pages.add(systemPage(), SYSTEM);
        pages.setMinimumSize(new Dimension(360, 0));
        pages.setMaximumSize(new Dimension(720, Integer.MAX_VALUE));
        pages.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel bounded = new JPanel();
        bounded.setOpaque(false);
        bounded.setLayout(new BoxLayout(bounded, BoxLayout.X_AXIS));
        bounded.add(pages);
        bounded.add(Box.createHorizontalGlue());
        JScrollPane scroll = new JScrollPane(bounded);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);

        add(rail, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        select(APPEARANCE);
    }

    private void addItem(JPanel rail, String name, Function<Color, javax.swing.Icon> icon) {
        NavItem item = new NavItem(name, icon, () -> select(name));
        items.add(item);
        rail.add(item);
    }

    private void select(String name) {
        heading.setText(name);
        subtitle.setText(describe(name));
        for (NavItem item : items) {
            item.setSelected(name.equals(item.name));
        }
        ((CardLayout) pages.getLayout()).show(pages, name);
        for (Component component : pages.getComponents()) {
            if (name.equals(component.getName())) {
                component.revalidate();
                component.repaint();
            }
        }
    }

    private static String describe(String page) {
        if (APPEARANCE.equals(page)) {
            return "アプリの配色を選択します。";
        }
        if (FONTS.equals(page)) {
            return "文字の書体と大きさを調整します。";
        }
        return "アップデート確認とアプリの情報、再起動を管理します。";
    }

    /** Theme preview tiles: click a tile to switch between light and dark mode. */
    private JPanel appearancePage() {
        JPanel page = page(APPEARANCE);
        lightTile = new ThemeTile(false);
        darkTile = new ThemeTile(true);
        lightTile.addActionListener(event -> selectTheme(false));
        darkTile.addActionListener(event -> selectTheme(true));
        JPanel tiles = new JPanel(new GridLayout(1, 2, 14, 0));
        tiles.setOpaque(false);
        tiles.add(lightTile);
        tiles.add(darkTile);
        syncThemeTiles();
        addCard(page, tiles);
        return page;
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

    /** A card with the font family and size, followed by a live preview card. */
    private JPanel fontPage() {
        JPanel page = page(FONTS);
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

        JPanel settings = new JPanel(new BorderLayout(0, 0));
        settings.setOpaque(false);
        settings.add(row("フォント ファミリー", "使用する書体", family), BorderLayout.NORTH);
        settings.add(divider(), BorderLayout.CENTER);
        settings.add(row("フォント サイズ", "文字の基準サイズ（ポイント）", size), BorderLayout.SOUTH);
        addCard(page, card(settings));

        preview = new JLabel("Aa Bb あいうえお カタカナ 0123456789 — Freja");
        preview.setForeground(Theme.TEXT);
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel sample = new JPanel(new BorderLayout());
        sample.setOpaque(false);
        sample.add(row("プレビュー", "現在の設定の確認結果", Box.createHorizontalGlue()), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(10, 16, 18, 16));
        body.add(preview, BorderLayout.CENTER);
        sample.add(body, BorderLayout.SOUTH);
        addCard(page, card(sample));
        refreshPreview();
        return page;
    }

    /** Re-renders the sample text with the configured family and size. */
    private void refreshPreview() {
        if (preview != null) {
            preview.setFont(Theme.font(Font.PLAIN, 15));
            preview.repaint();
        }
    }

    /** System page with update controls, app information and a restart action. */
    private JPanel systemPage() {
        JPanel page = page(SYSTEM);

        JPanel updates = new JPanel(new BorderLayout());
        updates.setOpaque(false);

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

        ARXButton restart = new ARXButton("再起動する", Icons.refresh(14, Theme.MUTED),
                ARXButton.Variant.PRIMARY, freja.Freja::restart);
        restart.setFont(Theme.font(Font.PLAIN, 12));

        JPanel restartPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        restartPanel.setOpaque(false);
        restartPanel.add(restart);

        JPanel rows = new JPanel(new BorderLayout());
        rows.setOpaque(false);
        rows.add(row("起動時にアップデートを確認する", "起動時に新しいバージョンを確認します", togglePanel), BorderLayout.NORTH);
        rows.add(divider(), BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(row("アップデートの確認", "現在のバージョンを確認します", check), BorderLayout.NORTH);
        middle.add(divider(), BorderLayout.SOUTH);
        rows.add(middle, BorderLayout.CENTER);
        rows.add(row("アプリケーションを再起動", "設定を反映してアプリを再起動します", restartPanel), BorderLayout.SOUTH);
        addCard(page, card(rows));

        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);
        info.add(row("アプリケーション", "", value("Freja " + freja.Freja.VERSION + " (2026-11-24)")), BorderLayout.NORTH);
        info.add(divider(), BorderLayout.CENTER);
        JPanel osRow = row("オペレーティングシステム", "", value(System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + " " + System.getProperty("os.version")));
        JPanel javaRow = row("Javaランタイム", "", value(System.getProperty("java.version")));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(osRow, BorderLayout.NORTH);
        bottom.add(divider(), BorderLayout.CENTER);
        bottom.add(javaRow, BorderLayout.SOUTH);
        info.add(bottom, BorderLayout.SOUTH);
        addCard(page, card(info));
        return page;
    }

    /** A muted, right-aligned value label used in information rows. */
    private JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.MUTED);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    /** A plain stacked page which fills the bounded column. */
    private JPanel page(String id) {
        JPanel page = new JPanel();
        page.setName(id);
        page.setOpaque(false);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        return page;
    }

    /** Adds a card with a gap above it. */
    private void addCard(JPanel page, JPanel card) {
        page.add(Box.createVerticalStrut(12));
        page.add(card);
    }

    /** Wraps rows in a rounded, outlined card. */
    private JPanel card(JPanel rows) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(new FlatLineBorder(new Insets(1, 1, 1, 1), Theme.BORDER, 1, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(rows, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows.getPreferredSize().height + 2));
        return card;
    }

    /** One settings row: label and description on the left, control on the right. */
    private JPanel row(String title, String description, Component control) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(Theme.font(Font.PLAIN, 13));
        name.setForeground(Theme.TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = new JLabel(description);
        hint.setFont(Theme.font(Font.PLAIN, 11));
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        if (!description.isEmpty()) {
            text.add(hint);
        }
        row.add(text, BorderLayout.CENTER);
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(control, BorderLayout.CENTER);
        row.add(east, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** A hairline between rows, inset from the card edges. */
    private JPanel divider() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(Theme.BORDER);
        line.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
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
                String label = dark ? "ダーク" : "ライト";
                g.setFont(Theme.font(selectedState ? Font.BOLD : Font.PLAIN, 12));
                g.setColor(palette[3]);
                g.drawString(label, 21, 122);
                if (selectedState) {
                    g.setColor(palette[5]);
                    g.fillOval(w - 40, h - 40, 26, 26);
                    javax.swing.Icon tick = Icons.check(16, Color.WHITE);
                    tick.paintIcon(this, g, w - 35, h - 35);
                    g.setColor(palette[5]);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                } else if (hovered) {
                    g.setColor(palette[2]);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                } else {
                    g.setColor(Theme.BORDER);
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 16, 16));
                }
            } finally {
                g.dispose();
            }
        }
    }

    /** One entry in the left navigation rail, painted as a rounded pill. */
    private final class NavItem extends JPanel {

        private final String name;

        private final JLabel label;

        private final Function<Color, javax.swing.Icon> iconFactory;

        private final JLabel graphic;

        private boolean selected;

        private boolean hovered;

        NavItem(String name, Function<Color, javax.swing.Icon> iconFactory, Runnable action) {
            this.name = name;
            this.iconFactory = iconFactory;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(name);
            graphic = new JLabel(iconFactory.apply(Theme.MUTED));
            add(graphic, BorderLayout.WEST);
            label = new JLabel(name);
            label.setFont(Theme.font(Font.PLAIN, 13));
            label.setForeground(Theme.MUTED);
            add(label, BorderLayout.CENTER);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    action.run();
                }

                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    paintState();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    paintState();
                }
            });
        }

        void setSelected(boolean value) {
            selected = value;
            paintState();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int height = 34;
                int top = (getHeight() - height) / 2;
                int left = 12;
                int width = getWidth() - 24;
                if (selected) {
                    g.setColor(Theme.ACCENT_SOFT);
                    g.fillRoundRect(left, top, width, height, 14, 14);
                    g.setColor(Theme.ACCENT);
                    g.fillRoundRect(6, top + height / 2 - 8, 4, 16, 4, 4);
                } else if (hovered) {
                    g.setColor(Theme.HOVER);
                    g.fillRoundRect(left, top, width, height, 14, 14);
                }
            } finally {
                g.dispose();
            }
        }

        private void paintState() {
            label.setForeground(selected ? Theme.TEXT : Theme.MUTED);
            label.setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 13));
            graphic.setIcon(iconFactory.apply(selected ? Theme.TEXT : Theme.MUTED));
            repaint();
        }
    }
}
