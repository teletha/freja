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
import java.util.List;
import java.util.Locale;

import org.controlsfx.control.PopOver.ArrowLocation;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.SelectionMode;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Tag;
import freja.store.Library;
import viewtify.ViewtyDialog.DialogView;
import viewtify.Viewtify;
import viewtify.keys.Key;
import viewtify.ui.UIButton;
import viewtify.ui.UICheckBox;
import viewtify.ui.UILabel;
import viewtify.ui.UITableColumn;
import viewtify.ui.UITableView;
import viewtify.ui.UIText;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * A dialog for creating, renaming and removing tags, with bulk replacement and tag links. The
 * Viewtify counterpart of the Swing {@code TagManager}; closing it reports whether anything
 * changed so the window can refresh.
 */
public final class TagManagerView extends DialogView<Boolean> {

    private final Library library = MainView.current().library();

    private final List<Tag> all = new ArrayList<>();

    private UIText<String> search;

    private UIText<String> newTag;

    private UIButton addButton;

    private UIButton renameButton;

    private UIButton removeButton;

    private UIButton replaceButton;

    private UIButton linkButton;

    private UILabel status;

    private UITableView<Tag> table;

    private UITableColumn<Tag, String> name;

    private UITableColumn<Tag, Integer> count;

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(hbox, () -> {
                        $(newTag, style.input);
                        $(addButton);
                    });
                    $(search, style.input);
                    $(table, style.table, () -> {
                        $(name);
                        $(count);
                    });
                    $(hbox, style.buttons, () -> {
                        $(renameButton);
                        $(removeButton);
                        $(replaceButton);
                        $(linkButton);
                    });
                    $(status);
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
        addButton.text(en("Add")).icon(FontAwesomeSolid.PLUS);
        renameButton.text(en("Rename"));
        removeButton.text(en("Delete"));
        replaceButton.text(en("Replace…"));
        linkButton.text(en("Links…"));
        search.placeholder(en("Search tags")).clearable().prefix(FontAwesomeSolid.SEARCH);
        newTag.placeholder(en("New tag name"));

        name.text(en("Tag")).model(t -> t.name()).operatable(false);
        count.text(en("Folders")).model(t -> t.folderCount()).operatable(false);
        table.mode(SelectionMode.MULTIPLE);
        table.keybind(Key.Delete, () -> remove());
        reload();

        search.when(User.Input, event -> reload());
        newTag.keybind(Key.Enter, () -> add());
        addButton.when(User.Action, event -> add());
        renameButton.when(User.Action, event -> rename());
        removeButton.when(User.Action, event -> remove());
        replaceButton.when(User.Action, event -> replace());
        linkButton.when(User.Action, event -> editLinks());
        table.selected().to(tag -> updateStatus());
    }

    /** Refills the table for the current filter. */
    private void reload() {
        all.clear();
        all.addAll(library.tags());
        String query = search.value() == null ? "" : search.value().trim().toLowerCase(Locale.ROOT);
        table.items(query.isEmpty() ? all : all.stream().filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query)).toList());
        updateStatus();
    }

    private void updateStatus() {
        int selected = table.selectedItems().size();
        status.text(selected == 0 ? en("{0} tags", all.size()).get() : en("{0} selected", selected).get());
        status.style(style.muted);
        renameButton.enable(selected == 1);
        removeButton.enable(selected > 0);
        linkButton.enable(selected == 1);
    }

    private void add() {
        String name = newTag.value() == null ? "" : newTag.value().trim();
        if (name.isEmpty()) {
            return;
        }
        try {
            library.addTag(name);
            newTag.value("");
            value = true;
            reload();
        } catch (RuntimeException e) {
            Viewtify.dialog().show(AlertType.ERROR, e.getMessage());
        }
    }

    /** Renames the selected tag through the standard input dialog. */
    private void rename() {
        Tag tag = table.selectedItem().get();
        if (tag == null) {
            return;
        }
        Viewtify.dialog().showInput(en("Rename tag").get(), tag.name()).to(input -> {
            String trimmed = input == null ? "" : input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            try {
                library.renameTag(tag.id(), trimmed);
                value = true;
                reload();
            } catch (RuntimeException e) {
                Viewtify.dialog().show(AlertType.ERROR, e.getMessage());
            }
        });
    }

    private void remove() {
        List<Tag> selected = List.copyOf(table.selectedItems());
        if (selected.isEmpty()) {
            return;
        }
        Viewtify
                .dialog()
                .title(en("Delete tags").get())
                .button(en("Delete").get(), en("Cancel").get())
                .show(AlertType.CONFIRMATION, en("Remove {0} tags from every folder? The photos are not deleted.", selected.size()).get())
                .to(type -> {
                    if (type == null || type.getButtonData() != ButtonData.OK_DONE) {
                        return;
                    }
                    library.deleteTags(selected.stream().map(Tag::id).toList());
                    value = true;
                    reload();
                });
    }

    /** Renames every tag whose name contains {@code source}, replacing that part with {@code target}. */
    private void replace() {
        Viewtify.dialog().showInput(en("Find which tag name").get(), "").to(from -> {
            String source = from == null ? "" : from.trim();
            if (source.isEmpty()) {
                return;
            }
            Viewtify.dialog().showInput(en("Replace with").get(), "").to(to -> {
                String target = to == null ? "" : to.trim();
                if (target.isEmpty()) {
                    return;
                }
                int changed = library.bulkRenameTags(all.stream().filter(t -> t.name().toLowerCase(Locale.ROOT).contains(source.toLowerCase(Locale.ROOT))).map(Tag::id).toList(), source, target);
                if (changed > 0) {
                    value = true;
                    reload();
                }
                status.text(en("{0} tags renamed", changed).get());
            });
        });
    }

    /** Lets the user choose which tags the selected tag links to, like the Swing editor. */
    private void editLinks() {
        Tag source = table.selectedItem().get();
        if (source == null) {
            return;
        }
        List<Long> linked = library.linkedTargets(source.id()).stream().map(Tag::id)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        View picker = new LinkPicker(source, linked, ids -> {
            library.setTagLinks(source.id(), ids);
            value = true;
            reload();
        });
        Viewtify.dialog().location(table.ui(), ArrowLocation.BOTTOM_CENTER).showPopup(() -> picker);
    }

    /** The nested popup listing candidate link targets with checkboxes. */
    private final class LinkPicker extends View {

        private final Tag source;

        private final List<Long> linked;

        private final java.util.function.Consumer<List<Long>> onSave;

        private UIVBox rows;

        private LinkPicker(Tag source, List<Long> linked, java.util.function.Consumer<List<Long>> onSave) {
            this.source = source;
            this.linked = linked;
            this.onSave = onSave;
        }

        @Override
        protected ViewDSL declareUI() {
            return new ViewDSL() {
                {
                    $(vbox, style.linkPopup, () -> {
                        $(rows);
                    });
                }
            };
        }

        @Override
        protected void initialize() {
            rows.ui().setSpacing(4);
            for (Tag tag : library.tags()) {
                if (tag.id() == source.id()) {
                    continue;
                }
                UICheckBox check = new UICheckBox(this).text(tag.name()).value(linked.contains(tag.id()));
                check.when(User.Action, event -> {
                    if (check.is(true)) {
                        if (!linked.contains(tag.id())) {
                            linked.add(tag.id());
                        }
                    } else {
                        linked.remove(tag.id());
                    }
                    onSave.accept(linked);
                });
                rows.ui().getChildren().add(check.ui());
            }
            if (rows.ui().getChildren().isEmpty()) {
                UILabel none = new UILabel(this).text(en("No other tags yet"));
                rows.ui().getChildren().add(none.ui());
            }
        }
    }

    /** Visual language of the manager. */
    interface style extends StyleDSL {

        Style root = () -> {
            display.width(460, px);
            padding.size(4, px);
        };

        Style input = () -> margin.bottom(6, px);

        Style table = () -> {
            display.minHeight(260, px);
            display.maxHeight(380, px);
            margin.vertical(6, px);
        };

        Style buttons = () -> margin.bottom(6, px);

        Style muted = () -> font.size(11, px).color("-fx-mid-text-color");

        Style linkPopup = () -> {
            display.width(280, px);
            padding.size(12, px);
            overflow.hidden();
        };
    }
}
