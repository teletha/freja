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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Tag;
import freja.store.Library;
import viewtify.Viewtify;
import viewtify.keys.Key;
import viewtify.ui.UICheckBox;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UIText;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * A GitHub-issue-style label picker shown inside a Viewtify popup: a new-label field, a searchable
 * list of coloured labels and one-click toggling. Like the Swing {@code TagPicker} it persists
 * every change immediately, so there is no Apply step.
 */
public final class TagPickerView extends View {

    private final Library library;

    private final long folderId;

    private final Runnable onChange;

    private final Set<String> selected = new LinkedHashSet<>();

    private UIVBox rows;

    private UIScrollPane list;

    private UILabel heading;

    private UILabel close;

    private UIText<String> search;

    /** Creates a picker for one folder. */
    public TagPickerView(Library library, long folderId, Runnable onChange) {
        this.library = library;
        this.folderId = folderId;
        this.onChange = onChange == null ? () -> {
        } : onChange;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(hbox, () -> {
                        $(heading);
                        $(ui(grow()));
                        $(close);
                    });
                    $(search);
                    $(list, style.list, () -> {
                        $(rows);
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
        heading.text(en("Select labels")).style(style.heading);
        close.text("✕").style(style.close).tooltip(en("Close"));
        close.when(User.LeftClick, event -> Viewtify.dialog().close());
        search.placeholder(en("Search or create labels…")).clearable().prefix(FontAwesomeSolid.SEARCH);
        search.when(User.Input, event -> render());
        search.keybind(Key.Enter, () -> createLabel());
        search.keybind(Key.Escape, () -> Viewtify.dialog().close());
        list.ui().setFitToWidth(true);
        rows.ui().setSpacing(2);
        selected.addAll(library.folderTags(folderId));
        render();
        search.focus();
    }

    /** Rebuilds the visible rows for the current filter text. */
    private void render() {
        String query = search.value() == null ? "" : search.value().trim().toLowerCase(Locale.ROOT);
        rows.ui().getChildren().clear();
        List<Tag> tags = library.tags();
        for (Tag tag : tags) {
            if (!tag.name().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            rows.ui().getChildren().add(row(tag));
        }
        boolean exact = tags.stream().anyMatch(tag -> tag.name().equalsIgnoreCase(query));
        if (!query.isEmpty() && !exact) {
            UILabel create = new UILabel(this).text(search.value().trim() + "  " + en("Create new label").get()).icon(FontAwesomeSolid.PLUS);
            create.style(style.createRow);
            create.when(User.LeftClick, event -> createLabel());
            rows.ui().getChildren().add(create.ui());
        }
        if (tags.isEmpty()) {
            UILabel none = new UILabel(this).text(en("No tags yet"));
            none.style(style.empty);
            rows.ui().getChildren().add(none.ui());
        }
    }

    /** One checkable tag row with the delete affordance. */
    private javafx.scene.Node row(Tag tag) {
        UICheckBox check = new UICheckBox(this).text(tag.name()).value(selected.contains(tag.name()));
        check.ui().setStyle("-fx-text-fill: " + FxTagColors.css(tag.name()) + ";");
        check.when(User.Action, event -> {
            if (check.is(true)) {
                selected.add(tag.name());
            } else {
                selected.remove(tag.name());
            }
            applyNow();
        });
        List<String> linked = library.linkedTargets(tag.id()).stream().map(Tag::name).toList();
        UILabel hint = new UILabel(this).text(linked.isEmpty() ? "" : "→ " + String.join(", ", linked));
        hint.style(style.linked);
        UILabel remove = new UILabel(this).text("×");
        remove.style(style.remove);
        remove.tooltip(en("Delete this tag"));
        remove.when(User.LeftClick, event -> Viewtify
                .dialog()
                .title(en("Delete tag").get())
                .button(en("Delete").get(), en("Cancel").get())
                .show(AlertType.CONFIRMATION, en("Delete tag {0} from every folder? The photos are not deleted.", tag.name()).get())
                .to(type -> {
                    if (type == null || type.getButtonData() != ButtonData.OK_DONE) {
                        return;
                    }
                    library.deleteTags(List.of(tag.id()));
                    selected.remove(tag.name());
                    render();
                    onChange.run();
                }));
        HBox line = new HBox(6);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("tag-row");
        line.getChildren().addAll(check.ui(), hint.ui(), grow(), remove.ui());
        return line;
    }

    /** Creates the typed label and attaches it. */
    private void createLabel() {
        String name = search.value() == null ? "" : search.value().trim();
        if (name.isEmpty()) {
            return;
        }
        try {
            library.addTag(name);
            search.value("");
            selected.add(name);
            applyNow();
            render();
        } catch (RuntimeException e) {
            Viewtify.dialog().show(AlertType.ERROR, e.getMessage());
        }
    }

    /** Persists the selection right away, like the Swing picker. */
    private void applyNow() {
        try {
            library.setFolderTags(folderId, List.copyOf(selected));
            onChange.run();
        } catch (RuntimeException e) {
            Viewtify.dialog().show(AlertType.ERROR, e.getMessage());
        }
    }

    private static Region grow() {
        Region region = new Region();
        HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    /** Visual language of the picker. */
    interface style extends StyleDSL {

        Style root = () -> {
            display.width(280, px);
            padding.vertical(12, px).horizontal(12, px);
            background.color("-freja-surface");
            border.width(1, px).color("-freja-border");
            border.radius(8, px);
        };

        Style heading = () -> {
            font.size(13, px).weight(700).color("-freja-text");
            padding.bottom(8, px);
        };

        Style close = () -> {
            font.size(13, px).color("-freja-muted");
            padding.horizontal(4, px);
            cursor.pointer();
            $.hover(() -> font.color("-freja-text"));
        };

        Style list = () -> {
            display.height(260, px);
            margin.top(8, px);
        };

        Style createRow = () -> {
            font.size(12, px);
            padding.vertical(5, px).horizontal(6, px);
            cursor.pointer();
            $.hover(() -> background.color("-freja-hover"));
        };

        Style linked = () -> font.size(11, px).color("-freja-muted");

        Style remove = () -> {
            font.size(13, px).weight(700).color("-freja-muted");
            padding.horizontal(6, px);
            cursor.pointer();
            $.hover(() -> font.color("-freja-danger"));
        };

        Style empty = () -> {
            font.size(12, px).color("-freja-muted");
            padding.vertical(12, px).horizontal(4, px);
        };
    }
}
