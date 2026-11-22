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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.PhotoFilter.SortOrder;

/**
 * The navigation sidebar.
 */
public final class Sidebar extends JPanel {

    /** The selected library view. */
    public enum Mode {
        ALL, FAVORITES, RECENT, FOLDER
    }

    /** Callbacks for navigation actions. */
    public interface Listener {

        void onSelect(Mode mode, long folderId);

        void onAddFolder();

        void onRescanFolder(long folderId);

        void onSortFolder(long folderId, SortOrder sort);

        void onRemoveFolder(long folderId);

        void onRevealFolder(long folderId);
    }

    private final JPanel rows = new JPanel();

    private final JPanel folderRows = new JPanel();

    private final NavRow all = new NavRow("すべての写真", Icons.grid(17, Theme.MUTED));

    private final NavRow favorites = new NavRow("お気に入り", Icons.star(17, Theme.MUTED, true));

    private final NavRow recent = new NavRow("最近の写真", Icons.clock(17, Theme.MUTED));

    private final Map<Long, NavRow> folderComponents = new HashMap<>();

    private final JLabel footer = new JLabel();

    private Listener listener = new Listener() {
        @Override
        public void onSelect(Mode mode, long folderId) {
        }

        @Override
        public void onAddFolder() {
        }

        @Override
        public void onRescanFolder(long folderId) {
        }

        @Override
        public void onSortFolder(long folderId, SortOrder sort) {
        }

        @Override
        public void onRemoveFolder(long folderId) {
        }

        @Override
        public void onRevealFolder(long folderId) {
        }
    };

    private Mode mode = Mode.ALL;

    private long folderId = -1;

    public Sidebar() {
        setLayout(new BorderLayout());
        setBackground(Theme.SIDEBAR);
        setPreferredSize(new Dimension(246, 600));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(18, 16, 8, 16));
        top.add(brand());
        top.add(Box.createVerticalStrut(18));
        top.add(sectionLabel("ライブラリ"));
        top.add(Box.createVerticalStrut(6));
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(all);
        rows.add(Box.createVerticalStrut(2));
        rows.add(favorites);
        rows.add(Box.createVerticalStrut(2));
        rows.add(recent);
        top.add(rows);
        top.add(Box.createVerticalStrut(18));
        top.add(folderHeader());
        add(top, BorderLayout.NORTH);

        folderRows.setOpaque(false);
        folderRows.setLayout(new BoxLayout(folderRows, BoxLayout.Y_AXIS));
        folderRows.setBorder(BorderFactory.createEmptyBorder(6, 8, 12, 8));
        JPanel folderHolder = new JPanel(new BorderLayout());
        folderHolder.setOpaque(false);
        folderHolder.add(folderRows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(folderHolder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);

        footer.setOpaque(false);
        footer.setForeground(Theme.MUTED);
        footer.setFont(Theme.font(Font.PLAIN, 11));
        footer.setBorder(BorderFactory.createEmptyBorder(8, 18, 16, 18));
        add(footer, BorderLayout.SOUTH);

        all.addMouseListener(rowListener(() -> select(Mode.ALL, -1)));
        favorites.addMouseListener(rowListener(() -> select(Mode.FAVORITES, -1)));
        recent.addMouseListener(rowListener(() -> select(Mode.RECENT, -1)));
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    private JComponent brand() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);
        JComponent mark = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Theme.ACCENT);
                    g.fill(new RoundRectangle2D.Float(0, 2, 34, 34, 10, 10));
                    g.setColor(Color.WHITE);
                    g.setFont(Theme.font(Font.BOLD, 19));
                    g.drawString("F", 12, 27);
                } finally {
                    g.dispose();
                }
            }
        };
        mark.setPreferredSize(new Dimension(38, 40));
        panel.add(mark, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("FREJA");
        name.setFont(Theme.font(Font.BOLD, 19));
        name.setForeground(Theme.TEXT);
        JLabel caption = new JLabel("PHOTO LIBRARY");
        caption.setFont(Theme.font(Font.PLAIN, 9));
        caption.setForeground(Theme.MUTED);
        text.add(name);
        text.add(Box.createVerticalStrut(1));
        text.add(caption);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private JComponent folderHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(sectionLabel("フォルダ"), BorderLayout.CENTER);
        JButton add = new JButton(Icons.plus(14, Theme.MUTED));
        add.setFocusable(false);
        add.setOpaque(false);
        add.setContentAreaFilled(false);
        add.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        add.setToolTipText("フォルダを追加");
        add.addActionListener(event -> listener.onAddFolder());
        panel.add(add, BorderLayout.EAST);
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 11));
        label.setForeground(Theme.MUTED);
        return label;
    }

    private MouseAdapter rowListener(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    action.run();
                }
            }
        };
    }

    private void select(Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        updateSelection();
        listener.onSelect(mode, folderId);
    }

    /**
     * Replaces the navigation data.
     *
     * @param stats library counters
     * @param folders registered folders
     * @param mode the active mode
     * @param folderId the active folder, or -1
     */
    public void setData(LibraryStats stats, List<Folder> folders, Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        all.setCount(stats.photos());
        favorites.setCount(stats.favorites());
        recent.setCount(stats.recent());
        footer.setText(stats.photos() + " 枚  ·  " + freja.model.Photo.formatSize(stats.bytes()));

        folderRows.removeAll();
        folderComponents.clear();
        for (Folder folder : folders) {
            NavRow row = new NavRow(folder.name(), Icons.folder(17, Theme.MUTED));
            row.setToolTipText(folder.path().toString() + (folder.explicit() ? "" : "  (自動検出)"));
            row.setCount(folder.photoCount());
            row.addMouseListener(rowListener(() -> select(Mode.FOLDER, folder.id())));
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON3) {
                        showFolderMenu(row, folder, event);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) {
                        showFolderMenu(row, folder, event);
                    }
                }
            });
            row.setAlignmentX(LEFT_ALIGNMENT);
            folderRows.add(row);
            folderRows.add(Box.createVerticalStrut(2));
            folderComponents.put(folder.id(), row);
        }
        if (folders.isEmpty()) {
            JLabel empty = new JLabel("登録されたフォルダはありません");
            empty.setForeground(Theme.FAINT);
            empty.setFont(Theme.font(Font.PLAIN, 11));
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            folderRows.add(empty);
        }
        updateSelection();
        folderRows.revalidate();
        folderRows.repaint();
    }

    private void showFolderMenu(NavRow row, Folder folder, MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(sortMenu(folder));
        menu.addSeparator();
        menu.add(item("再スキャン", () -> listener.onRescanFolder(folder.id())));
        menu.add(item("エクスプローラーで開く", () -> listener.onRevealFolder(folder.id())));
        if (folder.explicit()) {
            menu.addSeparator();
            menu.add(item("ライブラリから削除", () -> listener.onRemoveFolder(folder.id())));
        }
        menu.show(row, event.getX(), event.getY());
    }

    private JMenu sortMenu(Folder folder) {
        JMenu menu = new JMenu("表示順");
        ButtonGroup group = new ButtonGroup();
        for (SortOrder sort : SortOrder.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(sort.label(), sort == folder.sort());
            item.addActionListener(event -> listener.onSortFolder(folder.id(), sort));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    private javax.swing.JMenuItem item(String text, Runnable action) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void updateSelection() {
        all.setSelected(mode == Mode.ALL);
        favorites.setSelected(mode == Mode.FAVORITES);
        recent.setSelected(mode == Mode.RECENT);
        for (var entry : folderComponents.entrySet()) {
            entry.getValue().setSelected(mode == Mode.FOLDER && entry.getKey() == folderId);
        }
    }

    /**
     * A clickable navigation row.
     */
    private static final class NavRow extends JPanel {

        private final JLabel title;

        private final JLabel count = new JLabel();

        private boolean selected;

        private boolean hovered;

        NavRow(String text, javax.swing.Icon icon) {
            setOpaque(false);
            setLayout(new BorderLayout(9, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setAlignmentX(LEFT_ALIGNMENT);
            JLabel graphic = new JLabel(icon);
            add(graphic, BorderLayout.WEST);
            title = new JLabel(text);
            title.setFont(Theme.font(Font.PLAIN, 13));
            title.setForeground(Theme.TEXT);
            add(title, BorderLayout.CENTER);
            count.setFont(Theme.font(Font.PLAIN, 11));
            count.setForeground(Theme.MUTED);
            count.setHorizontalAlignment(SwingConstants.RIGHT);
            add(count, BorderLayout.EAST);
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

        void setCount(int value) {
            count.setText(value < 0 ? "" : String.format(Locale.ROOT, "%d", value));
        }

        void setSelected(boolean value) {
            if (selected != value) {
                selected = value;
                title.setFont(Theme.font(value ? Font.BOLD : Font.PLAIN, 13));
                title.setForeground(value ? Theme.ACCENT_DARK : Theme.TEXT);
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (selected || hovered) {
                    g.setColor(selected ? Theme.ACCENT_SOFT : Theme.HOVER);
                    g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 9, 9));
                }
                if (selected) {
                    g.setColor(Theme.ACCENT);
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(2, 9, 2, getHeight() - 9);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
