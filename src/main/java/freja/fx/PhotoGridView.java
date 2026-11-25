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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import freja.model.Photo;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import kiss.Variable;
import stylist.Style;
import stylist.StyleDSL;
import viewtify.Viewtify;
import viewtify.ui.UIContextMenu;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UIStackPane;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The thumbnail wall: folder or date sections, each with a header row and a wrapped flow of
 * photo cells. The Viewtify counterpart of the Swing {@code PhotoGrid}; the cells here are real
 * nodes, while the Swing edition painted them on a canvas.
 */
public final class PhotoGridView extends View {

    /** One header plus the photos below it. */
    public record Section(long folderId, String label, List<String> tags, List<Photo> photos) {
    }

    /** A section rendered with more rows than the initial budget. */
    private static final int SECTION_LIMIT = 250;

    private static final double GAP = 10;

    /** Receives the leading photo whenever the selection settles. */
    private Consumer<Photo> onSelection = photo -> {
    };

    /** Opens the viewer for a photo. */
    private Consumer<Photo> onOpen = photo -> {
    };

    /** Asks the window to build the photo context menu, anchored at the given cell. */
    private TriConsumer onContext = (menu, photo, anchor) -> {
    };

    /** Asks the window to open the tag editor for a folder section header. */
    private BiConsumer<Long, Node> onTagFolder = (folderId, anchor) -> {
    };

    /** Asks the window to follow the sticky folder of a scrolled-past section. */
    private Consumer<Long> onStickyFolder = folderId -> {
    };

    /** Three-argument callback for the context menu builder. */
    public interface TriConsumer {

        void accept(UIContextMenu menu, Photo photo, Node anchor);
    }

    private UIScrollPane scroll;

    private UIVBox body;

    /** The thumbnail cache; injected by the window. */
    private ThumbnailStore thumbs;

    /** The current photo order, flattened over all sections. */
    private List<Photo> photos = List.of();

    /** The data the wall was built from, kept for re-renders after zooming. */
    private List<Section> sections = List.of();

    /** The sections the user expanded beyond the initial budget. */
    private final Map<Long, Boolean> expanded = new LinkedHashMap<>();

    /** The card of every visible photo, for selection painting. */
    private final Map<Long, UIStackPane> cards = new LinkedHashMap<>();

    /** The header anchor of every folder section, for scrollTo. */
    private final Map<Long, Node> anchors = new LinkedHashMap<>();

    /** The photo ids in the current selection, in selection order. */
    private final LinkedHashMap<Long, Photo> selection = new LinkedHashMap<>();

    /** The photo which owns the inspector, or null. */
    private final Variable<Photo> selected = Variable.empty();

    private int thumbSize = 170;

    /** Wires the callbacks. */
    public void setHandlers(Consumer<Photo> onSelection, Consumer<Photo> onOpen, TriConsumer onContext, BiConsumer<Long, Node> onTagFolder, Consumer<Long> onStickyFolder) {
        this.onSelection = onSelection == null ? this.onSelection : onSelection;
        this.onOpen = onOpen == null ? this.onOpen : onOpen;
        this.onContext = onContext == null ? this.onContext : onContext;
        this.onTagFolder = onTagFolder == null ? this.onTagFolder : onTagFolder;
        this.onStickyFolder = onStickyFolder == null ? this.onStickyFolder : onStickyFolder;
    }

    public void setThumbnails(ThumbnailStore thumbs) {
        this.thumbs = thumbs;
    }

    public int thumbSize() {
        return thumbSize;
    }

    /** @return the flat photo list in display order */
    public List<Photo> photos() {
        return photos;
    }

    /** @return the leading selected photo, or null */
    public Photo selectedPhoto() {
        return selected.get();
    }

    /** @return every selected photo, in selection order */
    public List<Photo> selectedPhotos() {
        return List.copyOf(selection.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(scroll, style.scroll, () -> {
                    $(body, style.body);
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        scroll.policy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER, javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.ui().setFitToWidth(true);
        scroll.ui().setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.ui().setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        body.ui().setSpacing(4);
    }

    /** Replaces the whole wall. */
    public void setData(List<Section> sections, int thumbSize) {
        this.sections = List.copyOf(sections);
        this.thumbSize = thumbSize;
        render();
    }

    /** Changes the thumbnail size without touching the catalog. */
    public void setThumbSize(int size) {
        if (size == thumbSize) {
            return;
        }
        this.thumbSize = size;
        render();
    }

    private void render() {
        photos = new ArrayList<>();
        cards.clear();
        anchors.clear();
        List<javafx.scene.Node> rows = new ArrayList<>();
        for (Section section : sections) {
            photos.addAll(section.photos());
            boolean showHeader = section.label() != null && !section.label().isBlank();
            if (showHeader) {
                Node header = header(section);
                if (section.folderId() >= 0) {
                    anchors.put(section.folderId(), header);
                }
                rows.add(header);
            }
            int limit = section.folderId() < 0 || Boolean.TRUE.equals(expanded.get(section.folderId())) ? Integer.MAX_VALUE : SECTION_LIMIT;
            List<Photo> visible = section.photos().size() <= limit ? section.photos() : section.photos().subList(0, limit);
            FlowPane flow = new FlowPane();
            flow.setHgap(GAP);
            flow.setVgap(GAP);
            flow.setAlignment(Pos.TOP_LEFT);
            flow.getChildren().setAll(visible.stream().map(this::cell).map(UIStackPane::ui).toList());
            rows.add(flow);
            if (visible.size() < section.photos().size()) {
                rows.add(moreButton(section, section.photos().size() - visible.size()));
            }
        }
        if (photos.isEmpty()) {
            rows.add(emptyNote());
        }
        // Re-seed the selection against the fresh data; removed photos drop out silently.
        LinkedHashMap<Long, Photo> kept = new LinkedHashMap<>();
        for (Photo photo : photos) {
            Photo previous = selection.get(photo.id());
            if (previous != null) {
                kept.put(photo.id(), photo);
            }
        }
        selection.clear();
        selection.putAll(kept);
        selected.set(kept.isEmpty() ? null : new ArrayList<>(kept.values()).get(kept.size() - 1));
        paintSelection();

        body.ui().getChildren().setAll(rows);
    }

    private Node emptyNote() {
        UILabel note = new UILabel(this).text(en("No photos match the current filter"));
        note.style(style.empty);
        return note.ui();
    }

    private javafx.scene.Node moreButton(Section section, int rest) {
        Button button = new Button(en("Show {0} more", rest).get());
        button.setOnAction(event -> {
            expanded.put(section.folderId(), true);
            render();
        });
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: derive(-fx-base, 45%); -fx-cursor: hand;");
        StackPane box = new StackPane(button);
        box.setPadding(new javafx.geometry.Insets(6, 12, 14, 12));
        return box;
    }

    /** The section header: name, count and the clickable tag chips. */
    private Node header(Section section) {
        UILabel title = new UILabel(this).text(section.label());
        title.style(style.headerTitle);
        UILabel count = new UILabel(this).text(section.photos().size() + " 枚");
        count.style(style.headerCount);
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8, title.ui(), count.ui());
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new javafx.geometry.Insets(10, 12, 2, 12));
        if (section.folderId() >= 0) {
            Region space = new Region();
            javafx.scene.layout.HBox.setHgrow(space, javafx.scene.layout.Priority.ALWAYS);
            List<Node> chips = new ArrayList<>();
            for (String tag : section.tags()) {
                UILabel chip = new UILabel(this).text("#" + tag);
                chip.style(style.chip);
                chip.ui().setStyle("-fx-text-fill: " + FxTagColors.css(tag) + ";");
                chip.when(User.LeftClick, event -> onTagFolder.accept(section.folderId(), chip.ui()));
                chips.add(chip.ui());
            }
            if (chips.isEmpty()) {
                UILabel chip = new UILabel(this).text("#+");
                chip.style(style.chip);
                chip.ui().setOpacity(0.5);
                chip.when(User.LeftClick, event -> onTagFolder.accept(section.folderId(), chip.ui()));
                chips.add(chip.ui());
            }
            javafx.scene.layout.HBox tagRow = new javafx.scene.layout.HBox(6);
            tagRow.getChildren().setAll(chips);
            row.getChildren().addAll(space, tagRow);
        }
        if (section.folderId() >= 0) {
            row.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 1) {
                    onStickyFolder.accept(section.folderId());
                }
            });
        }
        return row;
    }

    /** Builds one thumbnail card. */
    private UIStackPane cell(Photo photo) {
        UIImageHost image = new UIImageHost();
        UILabel favorite = new UILabel(this).text("★");
        favorite.style(style.favoriteBadge);
        favorite.visible(photo.favorite()).managed(false);
        UILabel format = new UILabel(this).text(photo.formatLabel());
        format.style(style.formatBadge);
        format.visible(!photo.formatLabel().isEmpty()).managed(false);
        UILabel name = new UILabel(this).text(photo.fileName());
        name.style(style.cardName);

        StackPane picture = new StackPane(image.node, favorite.ui(), format.ui(), name.ui());
        StackPane.setAlignment(favorite.ui(), Pos.TOP_LEFT);
        StackPane.setAlignment(format.ui(), Pos.TOP_RIGHT);
        StackPane.setAlignment(name.ui(), Pos.BOTTOM_CENTER);
        favorite.ui().setMouseTransparent(true);
        format.ui().setMouseTransparent(true);
        name.ui().setMouseTransparent(true);

        UIStackPane card = new UIStackPane(this);
        card.ui().getChildren().setAll(picture);
        card.style(style.card);
        cards.put(photo.id(), card);
        card.ui().setMinSize(thumbSize, thumbSize);
        card.ui().setMaxSize(thumbSize, thumbSize);
        card.ui().setPrefSize(thumbSize, thumbSize);

        image.load(photo);
        card.when(User.LeftClick, event -> {
            if (event.isControlDown() || event.isMetaDown()) {
                toggle(photo);
            } else if (event.isShiftDown() && selected.get() != null) {
                selectRange(photo);
            } else {
                selectOnly(photo);
            }
        });
        card.when(User.DoubleClick, event -> onOpen.accept(photo));
        // Regions have no contextMenuProperty, so the Viewtify menu builder is materialized into
        // a raw ContextMenu on every request instead of ContextMenuHelper.context.
        ContextMenu raw = new ContextMenu();
        card.ui().setOnContextMenuRequested(event -> {
            List<MenuItem> items = new ArrayList<>();
            onContext.accept(new UIContextMenu("photo-" + photo.id(), items), photo, card.ui());
            raw.getItems().setAll(items);
            if (!items.isEmpty()) {
                raw.show(card.ui(), event.getScreenX(), event.getScreenY());
            }
        });
        return card;
    }

    /** Small helper holding the image node and its async slot. */
    private final class UIImageHost {

        private final javafx.scene.image.ImageView node = new javafx.scene.image.ImageView();

        private UIImageHost() {
            node.setPreserveRatio(true);
            node.setSmooth(true);
            node.setFitWidth(Math.max(1, thumbSize - 8));
            node.setFitHeight(Math.max(1, thumbSize - 8));
        }

        private void load(Photo photo) {
            Image cached = thumbs == null ? null : thumbs.peek(photo);
            if (cached != null) {
                node.setImage(cached);
                return;
            }
            if (thumbs == null) {
                return;
            }
            thumbs.imageFor(photo).observe().on(Viewtify.UIThread).to(node::setImage);
        }
    }

    // ----------------------------------------------------------------------------
    // Selection
    // ----------------------------------------------------------------------------

    private void selectOnly(Photo photo) {
        selection.clear();
        selection.put(photo.id(), photo);
        selected.set(photo);
        paintSelection();
        onSelection.accept(photo);
    }

    private void toggle(Photo photo) {
        if (selection.remove(photo.id()) == null) {
            selection.put(photo.id(), photo);
        }
        selected.set(selection.isEmpty() ? null : lastOf(selection));
        paintSelection();
        onSelection.accept(selected.get());
    }

    private void selectRange(Photo photo) {
        int from = photos.indexOf(selected.get());
        int to = photos.indexOf(photo);
        if (from < 0 || to < 0) {
            selectOnly(photo);
            return;
        }
        if (from > to) {
            int swap = from;
            from = to;
            to = swap;
        }
        selection.clear();
        for (int i = from; i <= to; i++) {
            Photo item = photos.get(i);
            selection.put(item.id(), item);
        }
        selected.set(photo);
        paintSelection();
        onSelection.accept(photo);
    }

    private static Photo lastOf(Map<Long, Photo> map) {
        List<Photo> values = new ArrayList<>(map.values());
        return values.get(values.size() - 1);
    }

    /** Selects a photo from outside, e.g. restored after a refresh. */
    public void select(Photo photo) {
        if (photo == null) {
            selection.clear();
            selected.set((Photo) null);
        } else {
            selection.clear();
            selection.put(photo.id(), photo);
            selected.set(photo);
        }
        paintSelection();
    }

    public void clearSelection() {
        select(null);
    }

    private void paintSelection() {
        for (Map.Entry<Long, UIStackPane> entry : cards.entrySet()) {
            entry.getValue().style(selection.containsKey(entry.getKey()), style.cardSelected);
        }
    }

    /**
     * Scrolls to the section of a folder, when it exists.
     *
     * @param folderId the folder
     * @return true when a section was found and reached
     */
    public boolean scrollToFolder(long folderId) {
        Node anchor = anchors.get(folderId);
        if (anchor == null) {
            return false;
        }
        scroll.scrollTo(anchor, true);
        return true;
    }

    public void scrollToTop() {
        scroll.ui().setVvalue(0);
    }

    /** Visual language of the wall. */
    interface style extends StyleDSL {

        Style scroll = () -> {
            background.color("-fx-background");
            $.descendant(".viewport", () -> background.color("-fx-background"));
            $.descendant(".corner", () -> background.color("-fx-background"));
        };

        Style body = () -> {
            padding.vertical(10, px).horizontal(14, px);
            background.color("-fx-background");
        };

        Style card = () -> {
            background.color("-fx-control-inner-background");
            border.width(2, px).color("transparent");
            border.radius(8, px);
            cursor.pointer();
            $.hover(() -> border.color("derive(-fx-accent, 10%)"));
        };

        Style cardSelected = () -> border.color("-fx-accent");

        Style cardName = () -> {
            font.size(10, px).color("white");
            background.color("rgba(0,0,0,0.55)");
            padding.vertical(2, px).horizontal(6, px);
            overflow.hidden();
            display.maxWidth(180, px);
        };

        Style favoriteBadge = () -> {
            font.size(14, px).color("#F1B64A");
            padding.size(4, px);
        };

        Style formatBadge = () -> {
            font.size(9, px).color("#C8CDD4");
            background.color("rgba(0,0,0,0.5)");
            padding.vertical(2, px).horizontal(5, px);
            border.radius(4, px);
        };

        Style chip = () -> {
            font.size(10, px);
            padding.vertical(2, px).horizontal(6, px);
            border.radius(9, px);
            background.color("derive(-fx-base, 8%)");
            cursor.pointer();
        };

        Style headerTitle = () -> font.size(14, px).weight(700);

        Style headerCount = () -> font.size(11, px).color("-fx-mid-text-color");

        Style empty = () -> {
            font.size(12, px).color("-fx-mid-text-color");
            padding.size(24, px);
        };
    }
}
