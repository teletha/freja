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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
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
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import freja.model.Folder;
import laevatain.ARXButton;
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

        void onRenameFolder(long folderId);

        void onRemoveFolder(long folderId);

        void onRevealFolder(long folderId);

        void onEditFolderTags(long folderId);

        void onManageTags();
    }

    private final JPanel rows = new JPanel();

    private final JPanel folderRows = new JPanel();

    private final NavRow all = new NavRow("すべての写真", Icons.grid(17, Theme.MUTED));

    private final NavRow favorites = new NavRow("お気に入り", Icons.star(17, Theme.MUTED, true));

    private final NavRow recent = new NavRow("最近の写真", Icons.clock(17, Theme.MUTED));

    private final Map<Long, NavRow> folderComponents = new HashMap<>();

    /** Folder identifiers in display order, for arrow-key navigation. */
    private final List<Long> folderOrder = new ArrayList<>();

    private JScrollPane folderScroll;

    private final JLabel folderCount = new JLabel();

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
        public void onRenameFolder(long folderId) {
        }

        @Override
        public void onRemoveFolder(long folderId) {
        }

        @Override
        public void onRevealFolder(long folderId) {
        }

        @Override
        public void onEditFolderTags(long folderId) {
        }

        @Override
        public void onManageTags() {
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
        // BoxLayout keeps centring a bare JLabel / nested panel even with LEFT_ALIGNMENT. Wrapping
        // them in a stretching BorderLayout panel pins both to the sidebar's left edge.
        JPanel libraryHeader = new JPanel(new BorderLayout());
        libraryHeader.setOpaque(false);
        libraryHeader.add(sectionLabel("ライブラリ"), BorderLayout.CENTER);
        top.add(libraryHeader);
        top.add(Box.createVerticalStrut(6));
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(all);
        rows.add(favorites);
        rows.add(recent);
        JPanel rowsHolder = new JPanel(new BorderLayout());
        rowsHolder.setOpaque(false);
        rowsHolder.add(rows, BorderLayout.CENTER);
        top.add(rowsHolder);
        top.add(Box.createVerticalStrut(18));
        top.add(folderHeader());
        add(top, BorderLayout.NORTH);

        folderRows.setOpaque(true);
        folderRows.setBackground(Theme.SIDEBAR);
        folderRows.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        folderRows.setLayout(new BoxLayout(folderRows, BoxLayout.Y_AXIS));
        // Match the library rows' horizontal inset so both groups line up at the same x.
        folderRows.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));
        JPanel folderHolder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                // Let the viewport define the width. Otherwise a long folder name makes the view
                // wider than the sidebar and the count at the right edge gets clipped away.
                size.width = 0;
                return size;
            }
        };
        folderHolder.setOpaque(true);
        folderHolder.setBackground(Theme.SIDEBAR);
        folderHolder.add(folderRows, BorderLayout.NORTH);
        folderScroll = new JScrollPane(folderHolder);
        JScrollPane scroll = folderScroll;
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.setBackground(Theme.SIDEBAR);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(Theme.SIDEBAR);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        add(scroll, BorderLayout.CENTER);

        all.addMouseListener(rowListener(() -> select(Mode.ALL, -1)));
        favorites.addMouseListener(rowListener(() -> select(Mode.FAVORITES, -1)));
        recent.addMouseListener(rowListener(() -> select(Mode.RECENT, -1)));
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    private JComponent folderHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        heading.setOpaque(false);
        heading.add(sectionLabel("フォルダ"));
        folderCount.setFont(Theme.font(Font.PLAIN, 11));
        folderCount.setForeground(Theme.MUTED);
        heading.add(folderCount);
        panel.add(heading, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        ARXButton manage = headerButton("タグ", Icons.tag(13, Theme.MUTED), "タグを管理", () -> listener.onManageTags());
        ARXButton add = headerButton(null, Icons.plus(14, Theme.MUTED), "監視フォルダを管理", () -> listener.onAddFolder());
        buttons.add(manage);
        buttons.add(add);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 11));
        label.setForeground(Theme.MUTED);
        // A bare JLabel in a BoxLayout keeps the default CENTER alignment. Match the folder header,
        // which stretches full width and therefore reads as left aligned.
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
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

    /** Scrolls the sidebar so the given folder row is visible. */
    private void revealFolder(long id) {
        javax.swing.JComponent row = folderComponents.get(id);
        if (row != null) {
            // The rectangle must be in the scroll container's coordinate system.
            folderRows.scrollRectToVisible(new Rectangle(row.getX(), row.getY(), row.getWidth(), row.getHeight()));
        }
    }

    /** Highlights a folder from outside without feeding a selection event back to the window. */
    public void selectFolderSilently(long id) {
        if (mode == Mode.FOLDER && folderId == id) {
            return;
        }
        mode = Mode.FOLDER;
        folderId = id;
        updateSelection();
        revealFolder(id);
    }

    /** Moves the selection by one folder in display order. Only used while a folder row has focus. */
    private void moveFolderSelection(int delta) {
        if (folderOrder.isEmpty()) {
            return;
        }
        int index = mode == Mode.FOLDER ? folderOrder.indexOf(folderId) : -1;
        int next = index < 0 ? (delta > 0 ? 0 : folderOrder.size() - 1) : index + delta;
        if (next < 0 || next >= folderOrder.size()) {
            return;
        }
        long target = folderOrder.get(next);
        // Select first, then move focus. Requesting focus before the selection lets rapid key
        // repeats leave focus one row behind the selected one.
        select(Mode.FOLDER, target);
        NavRow row = folderComponents.get(target);
        if (row != null) {
            row.requestFocusInWindow();
        }
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
        folderCount.setText(String.valueOf(folders.size()));

        int previousViewY = folderScroll == null ? 0 : folderScroll.getViewport().getViewPosition().y;
        Long focused = null;
        for (var entry : folderComponents.entrySet()) {
            if (entry.getValue().isFocusOwner()) {
                focused = entry.getKey();
                break;
            }
        }
        folderRows.removeAll();
        folderComponents.clear();
        folderOrder.clear();
        for (Folder folder : folders) {
            NavRow row = new NavRow(folder.name(), Icons.folder(17, Theme.MUTED));
            String tags = folder.tags().isEmpty() ? "" : "\nタグ: " + String.join(", ", folder.tags());
            row.setToolTipText(folder.path().toString() + (folder.explicit() ? "" : "  (自動検出)") + tags);
            row.setCount(folder.photoCount());
            row.addMouseListener(rowListener(() -> {
                row.requestFocusInWindow();
                select(Mode.FOLDER, folder.id());
            }));
            row.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_UP || event.getKeyCode() == KeyEvent.VK_DOWN) {
                        event.consume();
                        moveFolderSelection(event.getKeyCode() == KeyEvent.VK_UP ? -1 : 1);
                    }
                }
            });
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    maybeShowFolderMenu(row, folder, event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    maybeShowFolderMenu(row, folder, event);
                }
            });
            row.setAlignmentX(LEFT_ALIGNMENT);
            folderRows.add(row);
            folderComponents.put(folder.id(), row);
            folderOrder.add(folder.id());
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
        final Long focusedFolder = focused;
        folderRows.revalidate();
        folderRows.repaint();
        // Rebuilding the rows briefly shrinks the view, which resets the viewport to the top.
        // Restore the old position after layout, then only move if the selected row is out of view.
        SwingUtilities.invokeLater(() -> {
            if (folderScroll != null) {
                JViewport viewport = folderScroll.getViewport();
                int maximum = Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height);
                int y = Math.max(0, Math.min(previousViewY, maximum));
                viewport.setViewPosition(new Point(viewport.getViewPosition().x, y));
            }
            if (mode == Mode.FOLDER) {
                revealFolder(folderId);
            }
            if (focusedFolder != null) {
                NavRow row = folderComponents.get(focusedFolder);
                if (row != null) {
                    row.requestFocusInWindow();
                }
            }
        });
    }

    /** isPopupTrigger is platform specific: only one of press/release reports it for a click. */
    private void maybeShowFolderMenu(NavRow row, Folder folder, MouseEvent event) {
        if (event.isPopupTrigger()) {
            showFolderMenu(row, folder, event);
        }
    }

    private void showFolderMenu(NavRow row, Folder folder, MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(sortMenu(folder));
        menu.addSeparator();
        menu.add(item("名前を変更", () -> listener.onRenameFolder(folder.id())));
        menu.add(item("タグを編集…", () -> listener.onEditFolderTags(folder.id())));
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

    /** A small flat header button whose background and text change on hover. */
    private ARXButton headerButton(String text, javax.swing.Icon icon, String tooltip, Runnable action) {
        ARXButton button = new ARXButton(text, icon, ARXButton.Variant.SECONDARY, action);
        button.setFont(Theme.font(Font.PLAIN, 12));
        button.setToolTipText(tooltip);
        return button;
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
        // Repaint the whole strip as well: rapid key repeats can otherwise leave stale highlight
        // pixels from rows that scrolled past in the meantime.
        folderRows.repaint();
    }

    /** A title that shrinks with an ellipsis so the count always stays at the right edge. */
    private static final class TitleLabel extends JLabel {

        TitleLabel(String text) {
            super(text);
            setHorizontalAlignment(SwingConstants.LEFT);
            // Without this, a long folder name's minimum width pushes the count out of the row.
            setMinimumSize(new Dimension(0, 0));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            // A plain Graphics has no text hints; BasicLabelUI normally supplies them. Without
            // these, the truncated title renders noticeably thinner than the other labels.
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                FontMetrics metrics = g.getFontMetrics();
                String shown = text;
                if (metrics.stringWidth(shown) > getWidth()) {
                    while (shown.length() > 1 && metrics.stringWidth(shown + "…") > getWidth()) {
                        shown = shown.substring(0, shown.length() - 1);
                    }
                    shown += "…";
                }
                g.setColor(getForeground());
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(shown, 0, baseline);
            } finally {
                g.dispose();
            }
        }
    }

    /** A stronger tint than ACCENT_SOFT so the selected row is obvious at a glance. */
    private static Color selectedBackground() {
        Color accent = Theme.ACCENT;
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80);
    }

    /**
     * A clickable navigation row.
     */
    private static final class NavRow extends JPanel {

        private final TitleLabel title;

        private final JLabel count = new JLabel();

        private boolean selected;

        private boolean hovered;

        NavRow(String text, javax.swing.Icon icon) {
            // Opaque so a deselected row erases its own highlight. With transparent rows, repeated
            // arrow-key selection left the previous rows' accent paint behind.
            setOpaque(true);
            setBackground(Theme.SIDEBAR);
            setFocusable(true);
            setLayout(new BorderLayout(9, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            setAlignmentX(LEFT_ALIGNMENT);
            JLabel graphic = new JLabel(icon);
            add(graphic, BorderLayout.WEST);
            title = new TitleLabel(text);
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
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // Erase this row's previous paint before drawing the current state.
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (selected || hovered) {
                    g.setColor(selected ? selectedBackground() : Theme.HOVER);
                    g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 9, 9));
                }
            } finally {
                g.dispose();
            }
        }
    }
}
