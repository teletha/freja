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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.PhotoFilter.SortOrder;
import kiss.I;
import laevatain.ARXButton;
import laevatain.ARXLabel;
import laevatain.ARXMenu;
import laevatain.ARXMenuItem;
import laevatain.ARXNavList;
import laevatain.ARXRadioButtonMenuItem;
import laevatain.ARXTextBinding;

/**
 * The navigation sidebar: a library list built from the reusable {@link ARXNavList} and the
 * registered folders, which use the virtualized {@link FolderList} so a library with tens of
 * thousands of directories paints only what the viewport shows. Captions are bound to live
 * translations from {@link I#translate(String, Object...)}.
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

    private final FolderList folders = new FolderList();

    private final JPanel folderCard = new JPanel(new CardLayout());

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
        // The captions are written in English; each row binds its own live translation, so a
        // language change reaches them without a restart and without ARXLang callbacks here.
        library.setSection(I.translate("Library"));
        library.add("ALL", I.translate("All photos"), Icons.grid(17, Theme.MUTED));
        library.add("FAVORITES", I.translate("Favorites"), Icons.star(17, Theme.MUTED, true));
        library.add("RECENT", I.translate("Recent photos"), Icons.clock(17, Theme.MUTED));
        library.setOnSelect(key -> select(Mode.valueOf(key), -1));
        // A BoxLayout column hands a child only what it asked for and centres the sliver, so the
        // list goes into a stretching holder: the rows then span the sidebar instead of parking on
        // the right.
        JPanel libraryHolder = new JPanel(new BorderLayout());
        libraryHolder.setOpaque(false);
        libraryHolder.add(library, BorderLayout.CENTER);
        top.add(libraryHolder);
        top.add(Box.createVerticalStrut(18));
        top.add(folderHeader());
        add(top, BorderLayout.NORTH);

        folders.setMenu(this::folderMenu);
        folders.setOnSelect(folder -> select(Mode.FOLDER, folder.id()));
        JScrollPane folderScroll = folders.scrolled();

        ARXLabel empty = new ARXLabel(I.translate("No folders are registered"));
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
        ARXLabel title = ARXNavList.headingLabel(I.translate("Folders"));
        heading.add(title);
        folderCount.setFont(Theme.font(Font.PLAIN, 11));
        folderCount.setForeground(Theme.MUTED);
        heading.add(folderCount);
        panel.add(heading, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(headerButton("Tags", Icons.tag(13, Theme.MUTED), "Manage tags", () -> listener.onManageTags()));
        buttons.add(headerButton(null, Icons.plus(14, Theme.MUTED), "Manage watched folders", () -> listener.onAddFolder()));
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    /** A small flat header button whose background and text change on hover. */
    private ARXButton headerButton(String text, javax.swing.Icon icon, String tooltip, Runnable action) {
        // A null label keeps the button icon-only, so there is nothing to translate.
        ARXButton button = new ARXButton(text == null ? null : I.translate(text), icon, ARXButton.Variant.SECONDARY, action);
        button.setFont(Theme.font(Font.PLAIN, 12));
        button.setToolTipText(I.translate(tooltip));
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

    /** isPopupTrigger is platform specific: only one of press and release reports it. */
    private JPopupMenu folderMenu(Folder folder) {
        // The menu is rebuilt on every popup, so a snapshot of the translation is enough here.
        JPopupMenu menu = new JPopupMenu();
        menu.add(sortMenu(folder));
        menu.addSeparator();
        menu.add(item("Rename", () -> listener.onRenameFolder(folder.id())));
        menu.add(item("Edit tags…", () -> listener.onEditFolderTags(folder.id())));
        menu.add(item("Rescan", () -> listener.onRescanFolder(folder.id())));
        menu.add(item("Open in Explorer", () -> listener.onRevealFolder(folder.id())));
        if (folder.explicit()) {
            menu.addSeparator();
            menu.add(item("Remove from library", () -> listener.onRemoveFolder(folder.id())));
        }
        return menu;
    }

    private JMenu sortMenu(Folder folder) {
        JMenu menu = new ARXMenu(I.translate("Sort order"));
        ButtonGroup group = new ButtonGroup();
        for (SortOrder sort : SortOrder.values()) {
            ARXRadioButtonMenuItem item = new ARXRadioButtonMenuItem(label(sort), sort == folder.sort());
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

    /** A menu item whose English caption is translated on the way in. */
    private javax.swing.JMenuItem item(String sentence, Runnable action) {
        javax.swing.JMenuItem item = new ARXMenuItem(I.translate(sentence));
        item.addActionListener(event -> action.run());
        return item;
    }

    private void updateSelection() {
        library.setSelected(mode == Mode.FOLDER ? null : mode.name());
        folders.setSelectedId(mode == Mode.FOLDER ? folderId : -1);
    }
}
