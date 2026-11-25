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

import java.util.List;
import java.util.Locale;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.geometry.Pos;
import stylist.Style;
import stylist.StyleDSL;
import freja.model.Tag;
import freja.store.Library;
import viewtify.Viewtify;
import viewtify.keys.Key;
import viewtify.ui.UIButton;
import viewtify.ui.UICheckBox;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UIText;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The toolbar tag filter: a searchable checklist of all labels. Ticking one narrows the grid to
 * photos whose folder carries the tag, exactly like the Swing popup.
 */
public final class TagFilterView extends View {

    private final Library library;

    private final List<Long> selectedTagIds;

    private final Runnable onFilter;

    private UIScrollPane list;

    private UIVBox rows;

    private UILabel heading;

    private UIText<String> filter;

    private UIButton clear;

    /** @param selectedTagIds the live filter list; mutations here take effect on the next refresh */
    public TagFilterView(Library library, List<Long> selectedTagIds, Runnable onFilter) {
        this.library = library;
        this.selectedTagIds = selectedTagIds;
        this.onFilter = onFilter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(heading);
                    $(filter);
                    $(list, style.list, () -> {
                        $(rows);
                    });
                    $(hbox, style.footer, () -> {
                        $(clear);
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
        heading.text(en("Filter by tags")).style(style.heading);
        filter.placeholder(en("Search tags")).clearable().prefix(FontAwesomeSolid.SEARCH);
        filter.when(User.Input, event -> render());
        filter.keybind(Key.Escape, () -> Viewtify.dialog().close());
        clear.text(en("Clear selection"));
        clear.style(style.clear);
        clear.when(User.Action, event -> {
            selectedTagIds.clear();
            onFilter.run();
            Viewtify.dialog().close();
        });
        list.ui().setFitToWidth(true);
        rows.ui().setSpacing(2);
        render();
    }

    private void render() {
        String query = filter.value() == null ? "" : filter.value().trim().toLowerCase(Locale.ROOT);
        rows.ui().getChildren().clear();
        List<Tag> tags = library.tags();
        for (Tag tag : tags) {
            if (!tag.name().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            UICheckBox check = new UICheckBox(this).text(tag.name()).value(selectedTagIds.contains(tag.id()));
            check.when(User.Action, event -> {
                if (check.is(true)) {
                    if (!selectedTagIds.contains(tag.id())) {
                        selectedTagIds.add(tag.id());
                    }
                } else {
                    selectedTagIds.remove(tag.id());
                }
                onFilter.run();
            });
            UILabel count = new UILabel(this).text(tag.folderCount() + "  " + en("folder").get());
            count.style(style.count);
            javafx.scene.layout.HBox line = new javafx.scene.layout.HBox(8);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getChildren().addAll(check.ui(), grow(), count.ui());
            rows.ui().getChildren().add(line);
        }
        if (rows.ui().getChildren().isEmpty()) {
            UILabel none = new UILabel(this).text(en("No tags match"));
            none.style(style.count);
            rows.ui().getChildren().add(none.ui());
        }
        clear.enable(!selectedTagIds.isEmpty());
    }

    private static javafx.scene.layout.Region grow() {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    /** Visual language of the filter popup. */
    interface style extends StyleDSL {

        Style root = () -> {
            display.width(320, px);
            display.height(420, px);
            padding.vertical(12, px).horizontal(12, px);
            background.color("-fx-control-inner-background");
        };

        Style heading = () -> {
            font.size(14, px).weight(700);
            padding.bottom(8, px);
        };

        Style list = () -> {
            display.maxHeight(300, px);
            margin.vertical(8, px);
        };

        Style footer = () -> {
            margin.top(4, px);
        };

        Style count = () -> font.size(11, px).color("-fx-mid-text-color");

        Style clear = () -> font.size(12, px);
    }
}
