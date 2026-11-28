/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import freja.Icons;
import freja.Theme;
import freja.media.Folder;
import freja.media.LibraryStats;
import freja.media.PhotoFilter.SortOrder;
import kiss.I;
import laevatain.ARXTextBinding;

import laevatain.api.ComponentHelper;
import laevatain.component.ARXLabel;
import laevatain.component.ARXMenu;
import laevatain.component.ARXMenuItem;
import laevatain.component.ARXPanel;
import laevatain.component.ARXRadioButtonMenuItem;
import laevatain.component.ARXScrollPane;
import laevatain.composite.ARXNavList;
import laevatain.composite.ARXSectionHeader;
import laevatain.composite.ARXSectionListPanel;

/**
 * The navigation sidebar: a library list built from the reusable {@link ARXNavList} and the
 * registered folders, which use the virtualized {@link FolderList} so a library with tens of
 * thousands of directories paints only what the viewport shows. Captions are bound to live
 * translations from {@link I#translate(String, Object...)}.
 */
public final class Sidebar extends JPanel implements ComponentHelper<Sidebar> {
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
        void onRemoveFolders(List<Long> folderIds);
        void onDeleteFolderFromDisk(List<Long> folderIds);
        void onRevealFolder(long folderId);
        void onEditFolderTags(long folderId);
        void onManageTags();
    }
    private final ARXNavList library = new ARXNavList();
    private final FolderList folders = new FolderList();
    private final JPanel folderCard = new ARXPanel(new CardLayout());
    private final JLabel folderCount = new ARXLabel();
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
        public void onRemoveFolders(List<Long> folderIds) {
        }
        @Override
        public void onDeleteFolderFromDisk(List<Long> folderIds) {
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
        layout(new BorderLayout()).background(Theme.SIDEBAR)
                .preferredSize(246, 600)
                .border(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        JPanel top = new ARXPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(18, 16, 8, 16));
        // The captions are written in English; each row binds its own live translation, so a
        // language change reaches them without a restart and without ARXLang callbacks here.
        library.setSection(I.translate("Library"));
        library.add("ALL", I.translate("All photos"), Icons.nav("view-grid", Theme.MUTED));
        library.add("FAVORITES", I.translate("Favorites"), Icons.nav("star", Theme.MUTED));
        library.add("RECENT", I.translate("Recent photos"), Icons.nav("clock", Theme.MUTED));
        library.setOnSelect(key -> select(Mode.valueOf(key), -1));
        // A BoxLayout column hands a child only what it asked for and centres the sliver, so the
        // list goes into a stretching holder: the rows then span the sidebar instead of parking on
        // the right.
        JPanel libraryHolder = new ARXPanel(new BorderLayout());
        libraryHolder.setOpaque(false);
        libraryHolder.add(library, BorderLayout.CENTER);
        top.add(libraryHolder);
        top.add(Box.createVerticalStrut(18));
        add(top, BorderLayout.NORTH);
        folders.setMenu(this::folderMenu);
        folders.setOnSelect(folder -> select(Mode.FOLDER, folder.id()));
        folders.setOnRemove(this::removeSelectedFolders);
        folderCount.setFont(Theme.font(Font.PLAIN, 11));
        folderCount.setForeground(Theme.MUTED);
        String title = I.translate("Folders").get();
        ARXSectionHeader.Action[] actions = {
                new ARXSectionHeader.Action(I.translate("Tags").get(), Icons.tag(13, Theme.MUTED),
                        I.translate("Manage tags").get(), () -> listener.onManageTags()),
                new ARXSectionHeader.Action(Icons.plus(14, Theme.MUTED),
                        I.translate("Manage watched folders").get(), listener::onAddFolder)
        };
        ARXLabel empty = new ARXLabel(I.translate("No folders are registered"));
        empty.setForeground(Theme.FAINT);
        empty.setFont(Theme.font(Font.PLAIN, 11));
        empty.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 0));
        folderCard.setOpaque(true);
        folderCard.setBackground(Theme.SIDEBAR);
        ARXSectionListPanel listPanel = new ARXSectionListPanel(title, folderCount, folders.scrolled(), actions);
        ARXSectionListPanel emptyPanel = new ARXSectionListPanel(title, folderCount, new ARXScrollPane(empty), actions);
        for (ARXSectionListPanel pane : new ARXSectionListPanel[] {listPanel, emptyPanel}) {
            pane.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        }
        folderCard.add(listPanel, "list");
        folderCard.add(emptyPanel, "empty");
        add(folderCard, BorderLayout.CENTER);
    }
    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
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
        folders.reveal(id);
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
        // The rows are virtualized now, so a refresh only replaces records and repaints the cells
        // on screen. Keeping the first visible row stable means a scan which appends a folder far
        // below the viewport does not yank the rail back to the top.
        int firstVisible = folders.getFirstVisibleIndex();
        boolean hadFocus = folders.isFocusOwner();
        folders.setFolders(registered);
        updateSelection();
        showFolders(!registered.isEmpty());
        SwingUtilities.invokeLater(() -> {
            int size = folders.getModel().getSize();
            if (firstVisible >= 0 && size > 0) {
                folders.ensureIndexIsVisible(Math.min(firstVisible, size - 1));
            }
            if (mode == Mode.FOLDER) {
                folders.reveal(folderId);
            }
            if (hadFocus) {
                folders.requestFocusInWindow();
            }
        });
    }
    /** Swaps between the folder list and the empty hint. */
    private void showFolders(boolean visible) {
        ((CardLayout) folderCard.getLayout()).show(folderCard, visible ? "list" : "empty");
    }
    /** Removes every folder selected in the rail; a plain click still navigates to one folder. */
    private void removeSelectedFolders(List<Folder> folders) {
        if (folders.isEmpty()) {
            return;
        }
        listener.onRemoveFolders(folders.stream().map(Folder::id).toList());
    }
    /** isPopupTrigger is platform specific: only one of press and release reports it. */
    private JPopupMenu folderMenu(Folder folder) {
        // A right-click on a row which is part of a multi-selection applies to the whole selection.
        List<Folder> selected = folders.selectedFolders();
        List<Folder> targets = selected.size() > 1 && selected.contains(folder) ? selected : List.of(folder);
        // The menu is rebuilt on every popup, so a snapshot of the translation is enough here.
        JPopupMenu menu = new JPopupMenu();
        menu.add(sortMenu(folder));
        menu.addSeparator();
        menu.add(item("Rename", Icons.pencil(14, Theme.MUTED), () -> listener.onRenameFolder(folder.id())));
        menu.add(item("Edit tags…", Icons.tag(14, Theme.MUTED), () -> listener.onEditFolderTags(folder.id())));
        menu.add(item("Rescan", Icons.refresh(14, Theme.MUTED), () -> {
            for (Folder target : targets) {
                listener.onRescanFolder(target.id());
            }
        }));
        menu.add(item("Open in Explorer", Icons.folder(14, Theme.MUTED), () -> listener.onRevealFolder(folder.id())));
        menu.addSeparator();
        menu.add(item("Remove from library", Icons.close(14, Theme.MUTED),
                () -> listener.onRemoveFolders(targets.stream().map(Folder::id).toList())));
        menu.add(item("Delete from disk", Icons.trash(14, Theme.DANGER),
                () -> listener.onDeleteFolderFromDisk(targets.stream().map(Folder::id).toList())));
        return menu;
    }
    private JMenu sortMenu(Folder folder) {
        JMenu menu = new ARXMenu(I.translate("Sort order"));
        menu.setIcon(Icons.sortOrder(14, Theme.MUTED));
        ButtonGroup group = new ButtonGroup();
        for (SortOrder sort : SortOrder.values()) {
            boolean selected = sort == folder.sort();
            ARXRadioButtonMenuItem item = new ARXRadioButtonMenuItem(label(sort), sortIcon(sort), selected);
            Icons.markSelected(item, selected);
            item.addActionListener(event -> listener.onSortFolder(folder.id(), sort));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }
    /**
     * The sort entries in the current language. The model keeps its own labels because the toolbar
     * still shows them untranslated; this mapping is what the sidebar menu offers.
     */
    private static kiss.Variable<String> label(SortOrder order) {
        return I.translate(switch (order) {
        case NAME -> "By name";
        case DATE_NEWEST -> "Newest first";
        case DATE_OLDEST -> "Oldest first";
        case SIZE -> "By size";
        });
    }
    /** The icon that represents each sort order in menus. */
    private static Icon sortIcon(SortOrder order) {
        return switch (order) {
        case NAME -> Icons.sortName(14, Theme.MUTED);
        case DATE_NEWEST -> Icons.sortNewestFirst(14, Theme.MUTED);
        case DATE_OLDEST -> Icons.sortOldestFirst(14, Theme.MUTED);
        case SIZE -> Icons.sortBySize(14, Theme.MUTED);
        };
    }
    /** A menu item whose English caption is translated on the way in. */
    private javax.swing.JMenuItem item(String sentence, Runnable action) {
        return item(new ARXMenuItem(I.translate(sentence)), action);
    }
    /** The same, with a leading icon; the caption is still a live translation. */
    private javax.swing.JMenuItem item(String sentence, Icon icon, Runnable action) {
        return item(new ARXMenuItem(I.translate(sentence), icon), action);
    }
    private static javax.swing.JMenuItem item(javax.swing.JMenuItem item, Runnable action) {
        item.addActionListener(event -> action.run());
        return item;
    }
    private void updateSelection() {
        library.setSelected(mode == Mode.FOLDER ? null : mode.name());
        folders.setSelectedId(mode == Mode.FOLDER ? folderId : -1);
    }
}