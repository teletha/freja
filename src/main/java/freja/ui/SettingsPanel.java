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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

import com.formdev.flatlaf.ui.FlatLineBorder;

import laevatain.ARXButton;
import laevatain.ARXToggle;

/** A Windows 11-style settings pane with a navigation rail and a card-based detail area. */
public final class SettingsPanel extends JPanel {

    /** Notifications sent back to the owner after a setting changed. */
    public interface Listener {

        void onFontChanged(float ratio);

        void onThemeChanged();
    }

    private static final String APPEARANCE = "外観";

    private static final String FONTS = "フォント";

    private static final String SYSTEM = "システム";

    private final Listener listener;

    private final List<NavItem> items = new ArrayList<>();

    private final JPanel pages = new JPanel(new java.awt.CardLayout());

    private final JLabel heading = new JLabel(APPEARANCE);

    private final JLabel breadcrumb = new JLabel("設定  ›  " + APPEARANCE);

    private boolean updating;

    public SettingsPanel(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BACKGROUND);

        JPanel rail = new JPanel();
        rail.setBackground(Theme.SIDEBAR);
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setPreferredSize(new Dimension(224, 0));
        JLabel railTitle = new JLabel("設定");
        railTitle.setFont(Theme.font(Font.BOLD, 18));
        railTitle.setForeground(Theme.TEXT);
        railTitle.setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 22));
        rail.add(railTitle);
        addItem(rail, APPEARANCE, Icons.moon(16, Theme.MUTED));
        addItem(rail, FONTS, Icons.image(16, Theme.MUTED));
        addItem(rail, SYSTEM, Icons.refresh(16, Theme.MUTED));
        rail.add(Box.createVerticalGlue());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        breadcrumb.setFont(Theme.font(Font.PLAIN, 12));
        breadcrumb.setForeground(Theme.MUTED);
        breadcrumb.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        header.add(breadcrumb);
        heading.setFont(Theme.font(Font.BOLD, 22));
        heading.setForeground(Theme.TEXT);
        heading.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        header.add(heading);
        content.add(header, BorderLayout.NORTH);
        pages.setOpaque(false);
        pages.add(appearancePage(), APPEARANCE);
        pages.add(fontPage(), FONTS);
        pages.add(systemPage(), SYSTEM);
        pages.setMinimumSize(new Dimension(360, 0));
        pages.setMaximumSize(new Dimension(720, Integer.MAX_VALUE));
        pages.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
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

    private void addItem(JPanel rail, String name, javax.swing.Icon icon) {
        NavItem item = new NavItem(name, icon, () -> select(name));
        items.add(item);
        rail.add(item);
    }

    private void select(String name) {
        heading.setText(name);
        breadcrumb.setText("設定  ›  " + name);
        for (NavItem item : items) {
            item.setSelected(name.equals(item.name));
        }
        ((java.awt.CardLayout) pages.getLayout()).show(pages, name);
        for (java.awt.Component component : pages.getComponents()) {
            if (name.equals(component.getName())) {
                component.revalidate();
                component.repaint();
            }
        }
    }

    /** Card containing the light/dark selection. */
    private JPanel appearancePage() {
        JPanel page = section("テーマ", "アプリの配色を選択します。");
        JToggleButton light = new JToggleButton("ライト", Icons.sun(15, Theme.MUTED), !Theme.isDark());
        JToggleButton dark = new JToggleButton("ダーク", Icons.moon(15, Theme.MUTED), Theme.isDark());
        ButtonGroup group = new ButtonGroup();
        group.add(light);
        group.add(dark);
        for (JToggleButton button : new JToggleButton[] {light, dark}) {
            button.setFont(Theme.font(Font.PLAIN, 12));
            button.setFocusPainted(false);
        }
        ActionListener apply = event -> {
            if (updating) {
                return;
            }
            boolean value = dark.isSelected();
            Theme.setDark(value);
            listener.onThemeChanged();
        };
        light.addActionListener(apply);
        dark.addActionListener(apply);

        JPanel segmented = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        segmented.setOpaque(false);
        segmented.setBorder(new FlatLineBorder(new Insets(1, 1, 1, 1), Theme.BORDER, 1, 8));
        segmented.add(light);
        segmented.add(dark);
        page.add(card(row("配色", "ウィンドウと各画面の配色", segmented)));
        return page;
    }

    /** Card containing the font family and size controls. */
    private JPanel fontPage() {
        JPanel page = section("フォント", "文字の書体と大きさを調整します。");
        JComboBox<String> family = new JComboBox<>(Theme.availableFontFamilies());
        family.setSelectedItem(Theme.fontFamily());
        family.setFont(Theme.font(Font.PLAIN, 13));
        JSpinner size = new JSpinner(new SpinnerNumberModel(Theme.fontSize(), 8, 28, 1));
        size.setFont(Theme.font(Font.PLAIN, 13));
        ChangeListener changed = event -> {
            if (updating) {
                return;
            }
            String chosen = (String) family.getSelectedItem();
            int value = (Integer) size.getValue();
            float ratio = (float) value / Theme.fontSize();
            Theme.setFont(chosen, value);
            listener.onFontChanged(ratio);
        };
        family.addActionListener(event -> {
            if (!updating) {
                float ratio = (float) Theme.fontSize() / Theme.fontSize();
                String chosen = (String) family.getSelectedItem();
                int value = Theme.fontSize();
                Theme.setFont(chosen, value);
                listener.onFontChanged(ratio);
            }
        });
        size.addChangeListener(changed);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);
        rows.add(row("フォント ファミリー", "使用する書体", family));
        rows.add(row("フォント サイズ", "文字の基準サイズ（ポイント）", size));
        page.add(card(rows));
        return page;
    }

    /** Update information and application restart, laid out like Windows Update. */
    private JPanel systemPage() {
        JPanel page = section("更新", "アプリの情報と再起動を管理します。");
        page.add(banner("最新の状態です", "アップデートの自動確認はまだ利用できません。"));
        group(page, "その他のオプション");

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 0));
        rows.setOpaque(false);

        ARXToggle startup = new ARXToggle(false);
        JLabel state = new JLabel("オフ");
        state.setFont(Theme.font(Font.PLAIN, 12));
        state.setForeground(Theme.MUTED);
        startup.addActionListener(event -> state.setText(startup.isSelected() ? "オン" : "オフ"));
        JPanel togglePanel = new JPanel(new FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        togglePanel.setOpaque(false);
        togglePanel.add(state);
        togglePanel.add(startup);
        rows.add(row("起動時にアップデートを確認する", "起動時に新しいバージョンを確認します", togglePanel));

        ARXButton check = new ARXButton("最新バージョンです", null, ARXButton.Variant.SECONDARY, () -> {
        });
        check.setFont(Theme.font(Font.PLAIN, 12));
        rows.add(row("アップデートの確認", "現在のバージョンを確認します", check));

        ARXButton restart = new ARXButton("再起動する", Icons.refresh(14, Theme.MUTED),
                ARXButton.Variant.SECONDARY, freja.Freja::restart);
        restart.setFont(Theme.font(Font.PLAIN, 12));
        rows.add(row("アプリケーションを再起動", "設定を反映してアプリを再起動します", restart));
        page.add(card(rows));

        group(page, "アプリの情報");
        JPanel info = new JPanel(new GridLayout(0, 1, 0, 0));
        info.setOpaque(false);
        info.add(row("アプリケーション", "", value("Freja " + freja.Freja.VERSION + " (2026-11-24)")));
        info.add(row("オペレーティングシステム", "", value(System.getProperty("os.name") + " "
                + System.getProperty("os.arch") + " " + System.getProperty("os.version"))));
        info.add(row("Javaランタイム", "", value(System.getProperty("java.version"))));
        page.add(card(info));

        group(page, "関連するサポート");
        JPanel support = new JPanel(new GridLayout(0, 1, 0, 0));
        support.setOpaque(false);
        support.add(row("アップデートについて", "このアプリの更新方法", value("詳細情報")));
        page.add(card(support));
        return page;
    }

    /** A small informational banner at the top of a page. */
    private JPanel banner(String title, String detail) {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(Theme.ACCENT_SOFT);
        banner.setBorder(new FlatLineBorder(new Insets(12, 14, 12, 14), Theme.ACCENT, 1, 8));
        banner.add(new JLabel(Icons.check(16, Theme.ACCENT)), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(Theme.font(Font.BOLD, 12));
        name.setForeground(Theme.TEXT);
        JLabel hint = new JLabel(detail);
        hint.setFont(Theme.font(Font.PLAIN, 11));
        hint.setForeground(Theme.MUTED);
        text.add(name);
        text.add(hint);
        banner.add(text, BorderLayout.CENTER);
        banner.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(720, 56));
        return banner;
    }

    /** A section heading between cards. */
    private void group(JPanel page, String title) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Font.BOLD, 13));
        label.setForeground(Theme.TEXT);
        label.setBorder(BorderFactory.createEmptyBorder(16, 0, 8, 0));
        holder.add(label, BorderLayout.CENTER);
        page.add(holder);
    }

    /** A muted, right-aligned value label used in information rows. */
    private JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.MUTED);
        label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        return label;
    }

    /** A page with a title, subtitle and vertically stacked cards. */
    private JPanel section(String title, String subtitle) {
        JPanel page = new JPanel();
        page.setName(title);
        page.setOpaque(false);
        page.setLayout(new javax.swing.BoxLayout(page, javax.swing.BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Font.BOLD, 17));
        heading.setForeground(Theme.TEXT);
        heading.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        page.add(heading);
        JLabel hint = new JLabel(subtitle);
        hint.setFont(Theme.font(Font.PLAIN, 12));
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 18, 0));
        page.add(hint);
        return page;
    }

    /** Wraps rows in a rounded, outlined card. */
    private JPanel card(JPanel rows) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(new FlatLineBorder(new Insets(14, 16, 14, 16), Theme.BORDER, 1, 10));
        card.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        card.add(rows, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    /** One settings row: label/description on the left, control on the right. */
    private JPanel row(String title, String description, java.awt.Component control) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(Theme.font(Font.PLAIN, 13));
        name.setForeground(Theme.TEXT);
        JLabel hint = new JLabel(description);
        hint.setFont(Theme.font(Font.PLAIN, 11));
        hint.setForeground(Theme.MUTED);
        text.add(name);
        text.add(hint);
        row.add(text, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        return row;
    }

    /** One entry in the left navigation rail. */
    private final class NavItem extends JPanel {

        private final String name;

        private boolean selected;

        private boolean hovered;

        NavItem(String name, javax.swing.Icon icon, Runnable action) {
            this.name = name;
            setOpaque(true);
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            JLabel graphic = new JLabel(icon);
            add(graphic, BorderLayout.WEST);
            JLabel label = new JLabel(name);
            label.setFont(Theme.font(Font.PLAIN, 13));
            label.setForeground(Theme.TEXT);
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

        private void paintState() {
            setBackground(selected ? Theme.ACCENT_SOFT : hovered ? Theme.HOVER : Theme.SIDEBAR);
            repaint();
        }
    }
}
