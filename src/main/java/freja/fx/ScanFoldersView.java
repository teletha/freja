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

import java.nio.file.Path;
import java.util.List;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.SelectionMode;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Folder;
import freja.store.Library;
import viewtify.ViewtyDialog.DialogView;
import viewtify.Viewtify;
import viewtify.ui.UIButton;
import viewtify.ui.UICheckBox;
import viewtify.ui.UILabel;
import viewtify.ui.UITableColumn;
import viewtify.ui.UITableView;
import viewtify.ui.UIVBox;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The watched-folder manager: register, scan, mute or remove folders and toggle the startup and
 * sweep behaviour. The Viewtify counterpart of the Swing {@code ScanFoldersDialog}.
 */
public final class ScanFoldersView extends DialogView<Boolean> {

    /** Actions the window performs on the scanner side. */
    public interface Listener {

        void onRegisterFolder(Path path);

        void onRemoveFolders(List<Long> folderIds);

        void onScanFolders(List<Long> folderIds);

        void onClearFolders();

        void onChanged();
    }

    private final Library library;

    private final FrejaSetting setting;

    private final Listener listener;

    private UITableView<Folder> table;

    private UITableColumn<Folder, String> name;

    private UITableColumn<Folder, Integer> count;

    private UITableColumn<Folder, String> path;

    private UIButton addButton;

    private UIButton scanButton;

    private UIButton removeButton;

    private UIButton clearButton;

    private UIButton muteButton;

    private UICheckBox startupScan;

    private UICheckBox sweepScan;

    private UILabel hint;

    private UIVBox body;

    public ScanFoldersView(Library library, FrejaSetting setting, Listener listener) {
        this.library = library;
        this.setting = setting;
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(body, style.root, () -> {
                    $(table, style.table, () -> {
                        $(name);
                        $(count);
                        $(path);
                    });
                    $(hbox, style.buttons, () -> {
                        $(addButton);
                        $(scanButton);
                        $(muteButton);
                        $(removeButton);
                        $(clearButton);
                    });
                    $(startupScan);
                    $(sweepScan);
                    $(hint);
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        value = false;
        name.text(en("Folder")).model(f -> f.name()).operatable(false);
        count.text(en("Photos")).model(f -> f.photoCount()).operatable(false);
        path.text(en("Path")).model(f -> f.path().toString()).operatable(false);
        table.mode(SelectionMode.MULTIPLE);
        table.keybind(viewtify.keys.Key.Delete, () -> remove());

        addButton.text(en("Add folder")).icon(FontAwesomeSolid.FOLDER_PLUS);
        scanButton.text(en("Scan"));
        muteButton.text(en("Mute / unmute"));
        removeButton.text(en("Remove"));
        clearButton.text(en("Clear all"));
        startupScan.text(en("Scan folders when Freja starts"));
        sweepScan.text(en("Sweep for missed changes every 5 minutes"));
        hint.text(en("Photo files are never moved, renamed or deleted here; only the catalog changes."));
        hint.style(style.muted);
        body.ui().setSpacing(8);

        startupScan.value(setting.scanOnStartup.get());
        sweepScan.value(setting.sweep.get());
        startupScan.when(User.Action, event -> {
            setting.scanOnStartup.set(startupScan.is(true));
            setting.store();
            value = true;
            listener.onChanged();
        });
        sweepScan.when(User.Action, event -> {
            setting.sweep.set(sweepScan.is(true));
            setting.store();
            value = true;
            listener.onChanged();
        });

        addButton.when(User.Action, event -> Viewtify.dialog().showDirectory().to(directory -> {
            if (directory == null) {
                return;
            }
            listener.onRegisterFolder(directory.asJavaPath());
            value = true;
            reload();
        }));
        scanButton.when(User.Action, event -> {
            List<Long> ids = selectedIds();
            ids.forEach(id -> library.setFolderScanEnabled(id, true));
            listener.onScanFolders(ids);
            value = true;
            reload();
        });
        muteButton.when(User.Action, event -> {
            for (Folder folder : List.copyOf(table.selectedItems())) {
                library.setFolderScanEnabled(folder.id(), !folder.scanEnabled());
            }
            value = true;
            listener.onChanged();
            reload();
        });
        removeButton.when(User.Action, event -> remove());
        clearButton.when(User.Action, event -> Viewtify
                .dialog()
                .title(en("Clear watched folders").get())
                .button(en("Clear").get(), en("Cancel").get())
                .show(AlertType.CONFIRMATION, en("Remove every folder and its catalog data? The photo files stay on disk.").get())
                .to(type -> {
                    if (type == null || type.getButtonData() != ButtonData.OK_DONE) {
                        return;
                    }
                    listener.onClearFolders();
                    value = true;
                    reload();
                }));
        table.selected().to(folder -> updateButtons());
        reload();
    }

    /** Re-reads the catalog into the table. */
    public void reload() {
        List<Folder> folders = library.folders();
        table.items(folders);
        updateButtons();
    }

    private List<Long> selectedIds() {
        return table.selectedItems().stream().map(Folder::id).toList();
    }

    private void updateButtons() {
        boolean hasSelection = !table.selectedItems().isEmpty();
        scanButton.enable(hasSelection);
        muteButton.enable(hasSelection);
        removeButton.enable(hasSelection);
        clearButton.enable(!table.ui().getItems().isEmpty());
    }

    private void remove() {
        List<Long> ids = selectedIds();
        if (ids.isEmpty()) {
            return;
        }
        Viewtify
                .dialog()
                .title(en("Remove folders").get())
                .button(en("Remove").get(), en("Cancel").get())
                .show(AlertType.CONFIRMATION, en("Remove {0} folders and their catalog data? The photo files stay on disk.", ids.size()).get())
                .to(type -> {
                    if (type == null || type.getButtonData() != ButtonData.OK_DONE) {
                        return;
                    }
                    listener.onRemoveFolders(ids);
                    value = true;
                    reload();
                });
    }

    /** Visual language of the dialog. */
    interface style extends StyleDSL {

        Style root = () -> {
            display.width(560, px);
            padding.size(4, px);
        };

        Style table = () -> {
            display.minHeight(280, px);
            display.maxHeight(400, px);
        };

        Style buttons = () -> margin.vertical(6, px);

        Style muted = () -> font.size(11, px).color("-fx-mid-text-color");
    }
}
