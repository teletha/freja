/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import kiss.Variable;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.PhotoFilter.SortOrder;
import viewtify.ui.UIContextMenu;
import viewtify.ui.UICheckMenuItem;
import viewtify.ui.UIHBox;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The navigation sidebar: special library entries and the registered folders, with the per-folder
 * context menu. This is the Viewtify counterpart of the Swing {@code Sidebar}.
 */
public final class SidebarView extends View {

    /** The selected library view. */
    public enum Mode {
        ALL, FAVORITES, RECENT, FOLDER
    }

    /** Callbacks for navigation actions. */
    public interface Listener {

        default void onSelect(Mode mode, long folderId) {
        }

        default void onAddFolder() {
        }

        default void onRescanFolder(long folderId) {
        }

        default void onSortFolder(long folderId, SortOrder sort) {
        }

        default void onRenameFolder(long folderId) {
        }

        default void onRemoveFolder(long folderId) {
        }

        default void onRevealFolder(long folderId) {
        }

        default void onEditFolderTags(long folderId) {
        }

        default void onManageTags() {
        }
    }

    /** A row of one of the lists, kept addressable for selection and count updates. */
    private final class Row {

        private final UIHBox box;

        private final UILabel title;

        private final UILabel trailing;

        private final String key;

        private Row(UIHBox box, UILabel title, UILabel trailing, String key) {
            this.box = box;
            this.title = title;
            this.trailing = trailing;
            this.key = key;
        }
    }

    private UILabel libraryHeading;

    private UILabel foldersHeading;

    private UILabel foldersCount;

    private UILabel emptyHint;

    private UILabel manageTags;

    private UILabel addFolder;

    private UIVBox libraryList;

    private UIVBox folderSection;

    private UIScrollPane folderScroll;

    private UIVBox folderList;

    /** The special library rows, keyed by mode name. */
    private final Map<String, Row> libraryRows = new LinkedHashMap<>();

    /** The folder rows, keyed by folder id. */
    private final Map<Long, Row> folderRows = new LinkedHashMap<>();

    /** The key of the highlighted row; empty while nothing is selected. */
    private final Variable<String> selectedKey = Variable.empty();

    /** The latest snapshot of every folder behind a row, so the context menu reads fresh data. */
    private final Map<Long, Folder> known = new LinkedHashMap<>();

    private Listener listener = new Listener() {
    };

    private Mode mode = Mode.ALL;

    private long folderId = -1;

    public void setListener(Listener listener) {
        this.listener = listener == null ? this.listener : listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(vbox, style.section, () -> {
                        $(libraryHeading);
                        $(libraryList);
                    });
                    $(hsep);
                    $(folderSection, style.section, () -> {
                        $(hbox, style.folderHeader, () -> {
                            $(foldersHeading);
                            $(foldersCount);
                            $(ui(grow()), style.grow);
                            $(manageTags);
                            $(addFolder);
                        });
                        $(folderScroll, style.listArea, () -> {
                            $(folderList);
                        });
                        $(emptyHint);
                    });
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        folderScroll.ui().setFitToWidth(true);
        folderScroll.ui().setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        javafx.scene.layout.VBox.setVgrow(folderScroll.ui(), javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.VBox.setVgrow(folderSection.ui(), javafx.scene.layout.Priority.ALWAYS);
        libraryHeading.text(en("Library")).style(style.heading);
        foldersHeading.text(en("Folders")).style(style.heading);
        emptyHint.text(en("No folders are registered")).style(style.empty);
        emptyHint.visible(false).managed(false);
        manageTags.text("").icon(FontAwesomeSolid.TAGS).style(style.toolButton);
        manageTags.tooltip(en("Manage tags"));
        manageTags.when(User.LeftClick, event -> listener.onManageTags());
        addFolder.text("").icon(FontAwesomeSolid.PLUS).style(style.toolButton);
        addFolder.tooltip(en("Manage watched folders"));
        addFolder.when(User.LeftClick, event -> listener.onAddFolder());

        libraryRows.put("ALL", navRow("ALL", FontAwesomeSolid.TH_LARGE, en("All photos")));
        libraryRows.put("FAVORITES", navRow("FAVORITES", FontAwesomeSolid.STAR, en("Favorites")));
        libraryRows.put("RECENT", navRow("RECENT", FontAwesomeSolid.HISTORY, en("Recent photos")));
    }

    /** Builds one special library row and wires its click. */
    private Row navRow(String key, Ikon icon, Variable<String> title) {
        Row row = buildRow("L:" + key, icon, title, libraryList.ui());
        row.box.when(User.LeftClick, event -> select(SidebarView.Mode.valueOf(key), -1));
        return row;
    }

    private Row buildRow(String key, Ikon icon, Variable<String> title, Pane parent) {
        UILabel leading = new UILabel(this).icon(icon);
        UILabel label = new UILabel(this).text(title);
        UILabel trailing = new UILabel(this);
        UIHBox box = new UIHBox(this);
        box.ui().getChildren().setAll(leading.ui(), label.ui(), grow(), trailing.ui());
        box.style(style.navRow);
        leading.style(style.navIcon);
        label.style(style.navTitle);
        trailing.style(style.trailing);
        box.styleWhile(selectedKey.observe().map(key::equals), style.navSelected);
        return new Row(box, label, trailing, key);
    }

    /** Builds one folder row, with click and context menu attached exactly once. */
    private Row folderRow(Folder folder) {
        long id = folder.id();
        Row row = buildRow("F:" + id, FontAwesomeSolid.FOLDER, Variable.of(folder.name()), folderList.ui());
        row.box.when(User.LeftClick, event -> select(Mode.FOLDER, id));
        // HBox is not a Control, so the Viewtify menu builder is materialized on request.
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        row.box.ui().setOnContextMenuRequested(event -> {
            Folder current = known.getOrDefault(id, folder);
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            UIContextMenu builder = new UIContextMenu("folder-" + id, items);
            for (SortOrder order : SortOrder.values()) {
                UICheckMenuItem item = builder.check(sortLabel(order));
                item.value(order == current.sort());
                item.action(() -> listener.onSortFolder(id, order));
            }
            builder.separator();
            builder.menu(en("Rename")).action(() -> listener.onRenameFolder(id));
            builder.menu(en("Edit tags…")).action(() -> listener.onEditFolderTags(id));
            builder.menu(en("Rescan")).action(() -> listener.onRescanFolder(id));
            builder.menu(en("Open in Explorer")).action(() -> listener.onRevealFolder(id));
            if (current.explicit()) {
                builder.separator();
                builder.menu(en("Remove from library")).action(() -> listener.onRemoveFolder(id));
            }
            menu.getItems().setAll(items);
            menu.show(row.box.ui(), event.getScreenX(), event.getScreenY());
        });
        return row;
    }

    /** Applies title, tooltip and count to a folder row. */
    private void refreshFolderRow(Row row, Folder folder) {
        known.put(folder.id(), folder);
        row.title.text(folder.name());
        row.box.tooltip(folder.path() + (folder.explicit() ? "" : "  " + en("(auto-detected)").get()) + (folder
                .tags()
                .isEmpty() ? "" : "\n" + en("Tags").get() + ": " + String.join(", ", folder.tags())));
        row.trailing.text(Integer.toString(folder.photoCount()));
    }

    /**
     * Replaces the navigation data.
     *
     * @param stats library counters
     * @param registered the folders to display
     * @param mode the active mode
     * @param folderId the active folder, or -1
     */
    public void setData(LibraryStats stats, List<Folder> registered, Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        libraryRows.get("ALL").trailing.text(Integer.toString(stats.photos()));
        libraryRows.get("FAVORITES").trailing.text(Integer.toString(stats.favorites()));
        libraryRows.get("RECENT").trailing.text(Integer.toString(stats.recent()));
        foldersCount.text(Integer.toString(registered.size()));

        // Rows are updated in place so a refresh arriving while the user clicks never discards the
        // row which was just pressed, exactly like the Swing sidebar. Existing rows are re-appended
        // to keep the registration order.
        List<Long> keep = new ArrayList<>(registered.size());
        for (Folder folder : registered) {
            keep.add(folder.id());
            Row row = folderRows.get(folder.id());
            if (row == null) {
                folderRows.put(folder.id(), folderRow(folder));
            } else {
                refreshFolderRow(row, folder);
                folderList.ui().getChildren().remove(row.box.ui());
                folderList.ui().getChildren().add(row.box.ui());
            }
        }
        List<Long> gone = new ArrayList<>(folderRows.keySet());
        gone.removeAll(keep);
        for (long id : gone) {
            Row row = folderRows.remove(id);
            known.remove(id);
            folderList.ui().getChildren().remove(row.box.ui());
        }
        emptyHint.visible(registered.isEmpty()).managed(registered.isEmpty());
        applySelection();
    }

    /** Highlights a folder from outside without feeding a selection event back to the window. */
    public void selectFolderSilently(long id) {
        if (mode == Mode.FOLDER && folderId == id) {
            return;
        }
        mode = Mode.FOLDER;
        folderId = id;
        applySelection();
    }

    private void select(Mode mode, long id) {
        this.mode = mode;
        this.folderId = id;
        applySelection();
        listener.onSelect(mode, id);
    }

    private void applySelection() {
        selectedKey.set(mode == Mode.FOLDER ? "F:" + folderId : "L:" + mode.name());
    }

    /** The sort entries in the current language. */
    private Variable<String> sortLabel(SortOrder order) {
        return en(switch (order) {
        case NAME -> "By name";
        case DATE_NEWEST -> "Newest first";
        case DATE_OLDEST -> "Oldest first";
        case SIZE -> "By size";
        });
    }

    /** A horizontal spacer which eats the remaining width. */
    private static Region grow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /** Visual language of the sidebar. */
    interface style extends StyleDSL {

        Style root = () -> {
            background.color("derive(-fx-base, -3%)");
            display.minWidth(180, px);
        };

        Style section = () -> padding.vertical(10, px).horizontal(14, px);

        Style heading = () -> {
            font.size(11, px).color("-fx-mid-text-color");
            padding.vertical(6, px);
        };

        Style folderHeader = () -> padding.vertical(4, px);

        Style toolButton = () -> {
            font.size(12, px).color("-fx-mid-text-color");
            padding.vertical(2, px).horizontal(6, px);
            cursor.pointer();
            $.hover(() -> font.color("-fx-text-base-color"));
        };

        Style listArea = () -> overflow.auto();

        Style navRow = () -> {
            padding.vertical(5, px).horizontal(8, px);
            cursor.pointer();
            font.size(13, px);
            $.hover(() -> background.color("derive(-fx-base, 6%)"));
        };

        Style navSelected = () -> {
            background.color("-fx-accent");
            font.weight(700);
        };

        Style navIcon = () -> font.size(12, px).color("-fx-mid-text-color");

        Style navTitle = () -> {
            display.maxWidth(150, px);
            overflow.hidden();
        };

        Style trailing = () -> font.size(11, px).color("-fx-mid-text-color");

        Style empty = () -> {
            font.size(11, px).color("-fx-mid-text-color");
            padding.vertical(10, px).horizontal(12, px);
        };

        Style grow = () -> display.minWidth(4, px);
    }
}
