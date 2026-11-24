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
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.PhotoFilter.SortOrder;
import laevatain.ARXButton;
import laevatain.ARXNavList;

/**
 * The navigation sidebar: a library list and the registered folders, both built from the reusable
 * {@link ARXNavList}.
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

    private final ARXNavList library = new ARXNavList();

    private final ARXNavList folders = new ARXNavList();

    private final JScrollPane folderScroll;

    private final JPanel folderCard = new JPanel(new CardLayout());

    private final JLabel folderCount = new JLabel();

    /** The folders behind the current rows, so a menu can be rebuilt from a row key. */
    private final Map<Long, Folder> known = new HashMap<>();

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
        library.setSection("ライブラリ");
        library.add("ALL", "すべての写真", Icons.grid(17, Theme.MUTED));
        library.add("FAVORITES", "お気に入り", Icons.star(17, Theme.MUTED, true));
        library.add("RECENT", "最近の写真", Icons.clock(17, Theme.MUTED));
        library.setOnSelect(key -> select(Mode.valueOf(key), -1));
        top.add(library);
        top.add(Box.createVerticalStrut(18));
        top.add(folderHeader());
        add(top, BorderLayout.NORTH);

        folders.setMenu(row -> folderMenu(Long.parseLong(row.key())));
        folders.setOnSelect(key -> select(Mode.FOLDER, Long.parseLong(key)));
        folderScroll = folders.scrolled();

        JLabel empty = new JLabel("登録されたフォルダはありません");
        empty.setForeground(Theme.FAINT);
        empty.setFont(Theme.font(Font.PLAIN, 11));
        empty.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 0));
        JPanel emptyHolder = new JPanel(new BorderLayout());
        emptyHolder.setOpaque(true);
        emptyHolder.setBackground(Theme.SIDEBAR);
        emptyHolder.add(empty, BorderLayout.NORTH);

        folderCard.setOpaque(true);
        folderCard.setBackground(Theme.SIDEBAR);
        folderCard.add(folderScroll, "list");
        folderCard.add(emptyHolder, "empty");
        add(folderCard, BorderLayout.CENTER);
    }

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    private JComponent folderHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        heading.setOpaque(false);
        heading.add(ARXNavList.headingLabel("フォルダ"));
        folderCount.setFont(Theme.font(Font.PLAIN, 11));
        folderCount.setForeground(Theme.MUTED);
        heading.add(folderCount);
        panel.add(heading, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(headerButton("タグ", Icons.tag(13, Theme.MUTED), "タグを管理", () -> listener.onManageTags()));
        buttons.add(headerButton(null, Icons.plus(14, Theme.MUTED), "監視フォルダを管理", () -> listener.onAddFolder()));
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    /** A small flat header button whose background and text change on hover. */
    private ARXButton headerButton(String text, javax.swing.Icon icon, String tooltip, Runnable action) {
        ARXButton button = new ARXButton(text, icon, ARXButton.Variant.SECONDARY, action);
        button.setFont(Theme.font(Font.PLAIN, 12));
        button.setToolTipText(tooltip);
        return button;
    }

    private void select(Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        updateSelection();
        listener.onSelect(mode, folderId);
    }

    /** Highlights a folder from outside without feeding a selection event back to the window. */
    public void selectFolderSilently(long id) {
        if (mode == Mode.FOLDER && folderId == id) {
            return;
        }
        mode = Mode.FOLDER;
        folderId = id;
        updateSelection();
        folders.reveal(String.valueOf(id));
    }

    /**
     * Replaces the navigation data.
     *
     * @param stats library counters
     * @param registered the registered folders
     * @param mode the active mode
     * @param folderId the active folder, or -1
     */
    public void setData(LibraryStats stats, List<Folder> registered, Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        library.row("ALL").setTrailing(Integer.toString(stats.photos()));
        library.row("FAVORITES").setTrailing(Integer.toString(stats.favorites()));
        library.row("RECENT").setTrailing(Integer.toString(stats.recent()));
        folderCount.setText(Integer.toString(registered.size()));

        int previousViewY = folderScroll.getViewport().getViewPosition().y;
        String focused = focusedRow();
        folders.clear();
        known.clear();
        for (Folder folder : registered) {
            ARXNavList.Row row = folders.add(Long.toString(folder.id()), folder.name(),
                    Icons.folder(17, Theme.MUTED));
            String tags = folder.tags().isEmpty() ? "" : "\nタグ: " + String.join(", ", folder.tags());
            row.setToolTipText(folder.path().toString() + (folder.explicit() ? "" : "  (自動検出)") + tags);
            row.setTrailing(Integer.toString(folder.photoCount()));
            known.put(folder.id(), folder);
        }
        updateSelection();
        showFolders(!registered.isEmpty());
        folders.revalidate();
        folders.repaint();
        // Rebuilding the rows briefly shrinks the view, which resets the viewport to the top.
        // Restore the old position after layout, then only move if the selected row is out of view.
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = folderScroll;
            int maximum = Math.max(0, scroll.getViewport().getViewSize().height
                    - scroll.getViewport().getExtentSize().height);
            int y = Math.max(0, Math.min(previousViewY, maximum));
            scroll.getViewport().setViewPosition(new Point(scroll.getViewport().getViewPosition().x, y));
            if (mode == Mode.FOLDER) {
                folders.reveal(Long.toString(folderId));
            }
            if (focused != null) {
                ARXNavList.Row row = folders.row(focused);
                if (row != null) {
                    row.requestFocusInWindow();
                }
            }
        });
    }

    /** The key of the folder row which currently owns the focus, or null. */
    private String focusedRow() {
        for (String key : folders.keys()) {
            ARXNavList.Row row = folders.row(key);
            if (row != null && row.isFocusOwner()) {
                return key;
            }
        }
        return null;
    }

    /** Swaps between the folder list and the empty hint. */
    private void showFolders(boolean visible) {
        ((CardLayout) folderCard.getLayout()).show(folderCard, visible ? "list" : "empty");
    }

    /** isPopupTrigger is platform specific: only one of press and release reports it. */
    private JPopupMenu folderMenu(long id) {
        Folder folder = known.get(id);
        if (folder == null) {
            return null;
        }
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
        return menu;
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
        library.setSelected(mode == Mode.FOLDER ? null : mode.name());
        folders.setSelected(mode == Mode.FOLDER ? Long.toString(folderId) : null);
        // Repaint the whole strip as well: rapid key repeats can otherwise leave stale highlight
        // pixels from rows that scrolled past in the meantime.
        folders.repaint();
    }
}
