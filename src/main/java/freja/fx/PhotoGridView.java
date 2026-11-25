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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import freja.model.Photo;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import kiss.Disposable;
import kiss.Variable;
import stylist.Style;
import stylist.StyleDSL;
import viewtify.Viewtify;
import viewtify.ui.UIContextMenu;
import viewtify.ui.UILabel;
import viewtify.ui.UIPane;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UIStackPane;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * The thumbnail wall: folder or date sections with a header and a wrapped grid of photo cells.
 * <p>
 * This is the virtualized counterpart of the Swing {@code PhotoGrid}. Only the rows intersecting
 * the scroll viewport are materialized; cell nodes are recycled as the user scrolls, so opening a
 * library with tens of thousands of photos costs the same as opening a small one.
 * </p>
 */
public final class PhotoGridView extends View {

    /** One header plus the photos below it. */
    public record Section(long folderId, String label, List<String> tags, List<Photo> photos) {
    }

    private static final double GAP = 2;

    private static final double OUTER = 26;

    private static final double TOP = 8;

    private static final double BOTTOM = 20;

    private static final double HEADER_HEIGHT = 64;

    private static final double SECTION_GAP = 8;

    private static final double EMPTY_HEIGHT = 80;

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

    private enum Kind {
        HEADER, PHOTOS, EMPTY
    }

    /** A virtual row in the wall. Only PHOTOS rows carry cells. */
    private record Row(Kind kind, double y, double height, int section, List<Photo> photos) {
    }

    private UIScrollPane scroll;

    private UIPane canvas;

    /** The thumbnail cache; injected by the window. */
    private ThumbnailStore thumbs;

    /** The current photo order, flattened over all sections. */
    private List<Photo> photos = List.of();

    /** The data the wall was built from, kept for re-laying out after zooming or resizing. */
    private List<Section> sections = List.of();

    /** The virtual layout, in document order. */
    private final List<Row> rows = new ArrayList<>();

    /** Cards currently attached to the canvas, keyed by photo id. */
    private final Map<Long, Card> visibleCards = new HashMap<>();

    /** Detached cards, ready to be bound to another photo. */
    private final List<Card> cardPool = new ArrayList<>();

    /** Section headers are few, so they are created lazily and kept addressable. */
    private final Map<Integer, Node> headerNodes = new HashMap<>();

    private Node emptyNode;

    private double contentHeight;

    private int columns = 1;

    private long stickyFolder = -1;

    private boolean updating;

    private boolean layoutDirty;

    private double lastTop = Double.NaN;

    private double lastViewHeight = Double.NaN;

    private double lastWidth = Double.NaN;

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
                    $(canvas, style.body);
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
        canvas.ui().setMinWidth(0);
        canvas.ui().setMaxWidth(Double.MAX_VALUE);

        // Scrolling and resizing are the only events which can change the visible set of cells.
        scroll.ui().vvalueProperty().addListener((o, old, value) -> updateVisible());
        scroll.ui().viewportBoundsProperty().addListener((o, old, value) -> updateVisible());
        canvas.ui().widthProperty().addListener((o, old, value) -> {
            if (old.doubleValue() != value.doubleValue()) {
                computeLayout();
                updateVisible();
            }
        });
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

    /** Rebuilds the virtual layout without creating any cell. */
    private void render() {
        photos = new ArrayList<>();
        for (Section section : sections) {
            photos.addAll(section.photos());
        }

        // Drop cards and cached headers which belong to the previous catalog.
        for (Card card : visibleCards.values()) {
            card.unbind();
            canvas.ui().getChildren().remove(card.node());
            cardPool.add(card);
        }
        visibleCards.clear();
        canvas.ui().getChildren().removeAll(headerNodes.values());
        headerNodes.clear();
        if (emptyNode != null) {
            canvas.ui().getChildren().remove(emptyNode);
            emptyNode = null;
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

        computeLayout();
        updateVisible();
    }

    /** Computes the y position of every virtual row from the current width and thumbnail size. */
    private void computeLayout() {
        rows.clear();
        double width = canvas.ui().getWidth();
        if (width <= 0) {
            width = scroll.ui().getWidth() - 20;
        }
        double available = Math.max(thumbSize + GAP, width - 2 * OUTER);
        columns = Math.max(1, (int) Math.floor((available + GAP) / (thumbSize + GAP)));

        double y = TOP;
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (section.label() != null && !section.label().isBlank()) {
                rows.add(new Row(Kind.HEADER, y, HEADER_HEIGHT, i, null));
                y += HEADER_HEIGHT;
            }
            List<Photo> list = section.photos();
            for (int first = 0; first < list.size(); first += columns) {
                int last = Math.min(first + columns, list.size());
                rows.add(new Row(Kind.PHOTOS, y, thumbSize + GAP, i, list.subList(first, last)));
                y += thumbSize + GAP;
            }
            y += SECTION_GAP;
        }
        if (photos.isEmpty()) {
            rows.add(new Row(Kind.EMPTY, y, EMPTY_HEIGHT, -1, null));
            y += EMPTY_HEIGHT;
        }
        contentHeight = y + BOTTOM;
        canvas.ui().setMinHeight(contentHeight);
        canvas.ui().setPrefHeight(contentHeight);
        layoutDirty = true;
    }

    /**
     * Materializes just the rows around the viewport and recycles the cells which scrolled away.
     */
    private void updateVisible() {
        if (updating) {
            return;
        }
        updating = true;
        try {
            double viewHeight = scroll.ui().getViewportBounds().getHeight();
            if (viewHeight <= 0) {
                viewHeight = scroll.ui().getHeight();
            }
            double maximum = Math.max(0, contentHeight - viewHeight);
            double top = scroll.ui().getVvalue() * maximum;
            double buffer = Math.max(200, thumbSize * 2d);
            double from = top - buffer;
            double to = top + viewHeight + buffer;
            double width = Math.max(1, canvas.ui().getWidth());

            // ScrollPane fires a steady stream of vvalue/viewport notifications. Skip the work
            // unless the visible window actually moved or the layout changed.
            if (!layoutDirty && Math.abs(top - lastTop) < 1 && Math.abs(viewHeight - lastViewHeight) < 1 && Math
                    .abs(width - lastWidth) < 1) {
                return;
            }
            lastTop = top;
            lastViewHeight = viewHeight;
            lastWidth = width;
            layoutDirty = false;

            Set<Node> wanted = new HashSet<>();
            Map<Long, Card> next = new HashMap<>();
            double topHeader = Double.MAX_VALUE;
            long topFolder = -1;

            for (Row row : rows) {
                if (row.y() + row.height() < from || row.y() > to) {
                    continue;
                }
                switch (row.kind()) {
                case HEADER -> {
                    Node node = headerNodes.computeIfAbsent(row.section(), index -> header(sections.get(index)));
                    place(node, OUTER, row.y(), width - 2 * OUTER, HEADER_HEIGHT, wanted);
                    if (row.y() <= top + HEADER_HEIGHT && row.y() < topHeader) {
                        topHeader = row.y();
                        topFolder = sections.get(row.section()).folderId();
                    }
                }
                case PHOTOS -> {
                    List<Photo> list = row.photos();
                    for (int column = 0; column < list.size(); column++) {
                        Photo photo = list.get(column);
                        Card card = visibleCards.get(photo.id());
                        if (card == null) {
                            card = acquireCard();
                        }
                        card.bind(photo);
                        place(card.node(), OUTER + column * (thumbSize + GAP), row.y(), thumbSize, thumbSize, wanted);
                        next.put(photo.id(), card);
                    }
                }
                case EMPTY -> {
                    if (emptyNode == null) {
                        emptyNode = emptyNote();
                    }
                    place(emptyNode, OUTER, row.y(), width - 2 * OUTER, EMPTY_HEIGHT, wanted);
                }
                }
            }

            // Recycle every card which is not in the viewport any more.
            for (Map.Entry<Long, Card> entry : visibleCards.entrySet()) {
                if (!next.containsKey(entry.getKey())) {
                    Card card = entry.getValue();
                    card.unbind();
                    canvas.ui().getChildren().remove(card.node());
                    cardPool.add(card);
                }
            }
            visibleCards.clear();
            visibleCards.putAll(next);

            for (Node node : headerNodes.values()) {
                if (!wanted.contains(node)) {
                    canvas.ui().getChildren().remove(node);
                }
            }
            if (emptyNode != null && !wanted.contains(emptyNode)) {
                canvas.ui().getChildren().remove(emptyNode);
            }

            if (topFolder >= 0 && topFolder != stickyFolder) {
                stickyFolder = topFolder;
                onStickyFolder.accept(topFolder);
            }
            paintSelection();
        } finally {
            updating = false;
        }
    }

    private void place(Node node, double x, double y, double width, double height, Set<Node> wanted) {
        if (!canvas.ui().getChildren().contains(node)) {
            canvas.ui().getChildren().add(node);
        }
        node.relocate(x, y);
        node.resize(width, height);
        wanted.add(node);
    }

    private Card acquireCard() {
        return cardPool.isEmpty() ? new Card() : cardPool.remove(cardPool.size() - 1);
    }

    private Node emptyNote() {
        UILabel note = new UILabel(this).text(en("No photos match the current filter"));
        note.style(style.empty);
        return note.ui();
    }

    /** The section header: name, count and the clickable tag chips. */
    private Node header(Section section) {
        UILabel title = new UILabel(this).text(section.label());
        title.style(style.headerTitle);
        UILabel count = new UILabel(this).text(section.photos().size() + "枚");
        count.style(style.headerCount);
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8, title.ui(), count.ui());
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new javafx.geometry.Insets(10, 12, 2, 12));
        if (section.folderId() >= 0) {
            javafx.scene.layout.Region space = new javafx.scene.layout.Region();
            javafx.scene.layout.HBox.setHgrow(space, javafx.scene.layout.Priority.ALWAYS);
            List<Node> chips = new ArrayList<>();
            for (String tag : section.tags()) {
                UILabel chip = new UILabel(this).text("#" + tag);
                chip.style(style.chip);
                chip.ui().setStyle("-fx-background-color: " + FxTagColors.css(tag) + "; -fx-text-fill: white;");
                chip.when(User.LeftClick, event -> onTagFolder.accept(section.folderId(), chip.ui()));
                chips.add(chip.ui());
            }
            if (chips.isEmpty()) {
                UILabel chip = new UILabel(this).text("+ タグ");
                chip.style(style.chip);
                chip.ui()
                        .setStyle("-fx-background-color: -freja-surface; -fx-text-fill: -freja-accent; -fx-border-color: -freja-accent; -fx-border-width: 1; -fx-border-radius: 10;");
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
        row.setStyle("-fx-border-color: -freja-border; -fx-border-width: 0 0 1 0;");
        return row;
    }

    /**
     * A reusable thumbnail cell. Its handlers read the currently bound photo, so it can be
     * recycled.
     */
    private final class Card {

        private final UIStackPane card = new UIStackPane(PhotoGridView.this);

        private final ImageView image = new ImageView();

        private final UILabel favorite = new UILabel(PhotoGridView.this);

        private final UILabel format = new UILabel(PhotoGridView.this);

        private final ContextMenu menu = new ContextMenu();

        private Photo photo;

        private Disposable loading;

        private Card() {
            image.setPreserveRatio(true);
            image.setSmooth(true);
            favorite.text("★").style(style.favoriteBadge);
            favorite.ui().setManaged(false);
            favorite.ui().setMouseTransparent(true);
            format.style(style.formatBadge);
            format.ui().setManaged(false);
            format.ui().setMouseTransparent(true);

            StackPane picture = new StackPane(image, favorite.ui(), format.ui());
            StackPane.setAlignment(favorite.ui(), Pos.TOP_RIGHT);
            StackPane.setAlignment(format.ui(), Pos.BOTTOM_RIGHT);
            card.ui().getChildren().setAll(picture);
            card.style(style.card);

            card.when(User.LeftClick, event -> {
                Photo current = photo;
                if (current == null) {
                    return;
                }
                if (event.isControlDown() || event.isMetaDown()) {
                    toggle(current);
                } else if (event.isShiftDown() && selected.get() != null) {
                    selectRange(current);
                } else {
                    selectOnly(current);
                }
            });
            card.when(User.DoubleClick, event -> {
                if (photo != null) {
                    onOpen.accept(photo);
                }
            });
            // Regions have no contextMenuProperty, so the Viewtify menu builder is materialized
            // into a raw ContextMenu on every request instead of ContextMenuHelper.context.
            card.ui().setOnContextMenuRequested(event -> {
                Photo current = photo;
                if (current == null) {
                    return;
                }
                List<MenuItem> items = new ArrayList<>();
                onContext.accept(new UIContextMenu("photo-" + current.id(), items), current, card.ui());
                menu.getItems().setAll(items);
                if (!items.isEmpty()) {
                    menu.show(card.ui(), event.getScreenX(), event.getScreenY());
                }
            });
        }

        private Node node() {
            return card.ui();
        }

        private void bind(Photo value) {
            if (photo == value) {
                return;
            }
            if (loading != null) {
                loading.dispose();
                loading = null;
            }
            photo = value;
            image.setImage(null);
            favorite.visible(value.favorite());
            String formatLabel = value.formatLabel();
            format.text(formatLabel);
            format.visible(!formatLabel.isEmpty());
            image.setFitWidth(Math.max(1, thumbSize - 8));
            image.setFitHeight(Math.max(1, thumbSize - 8));

            Image cached = thumbs == null ? null : thumbs.peek(value);
            if (cached != null) {
                image.setImage(cached);
            } else if (thumbs != null) {
                Photo wanted = value;
                loading = thumbs.imageFor(value).observe().on(Viewtify.UIThread).to(loaded -> {
                    if (photo == wanted) {
                        image.setImage(loaded);
                    }
                });
            }
        }

        private void unbind() {
            if (loading != null) {
                loading.dispose();
                loading = null;
            }
            photo = null;
            image.setImage(null);
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
        for (Map.Entry<Long, Card> entry : visibleCards.entrySet()) {
            entry.getValue().card.style(selection.containsKey(entry.getKey()), style.cardSelected);
        }
    }

    /**
     * Scrolls to the section of a folder, when it exists.
     *
     * @param folderId the folder
     * @return true when a section was found and reached
     */
    public boolean scrollToFolder(long folderId) {
        for (Row row : rows) {
            if (row.kind() == Kind.HEADER && sections.get(row.section()).folderId() == folderId) {
                double viewHeight = scroll.ui().getViewportBounds().getHeight();
                double maximum = Math.max(0, contentHeight - viewHeight);
                scroll.ui().setVvalue(maximum <= 0 ? 0 : Math.min(1, Math.max(0, row.y() / maximum)));
                return true;
            }
        }
        return false;
    }

    public void scrollToTop() {
        scroll.ui().setVvalue(0);
    }

    /** Visual language of the wall. */
    interface style extends StyleDSL {

        Style scroll = () -> {
            background.color("-freja-background");
            $.descendant(".viewport", () -> background.color("-freja-background"));
            $.descendant(".corner", () -> background.color("-freja-background"));
        };

        Style body = () -> background.color("-freja-background");

        Style card = () -> {
            background.color("transparent");
            border.width(2, px).color("transparent");
            border.radius(12, px);
            cursor.pointer();
            $.hover(() -> background.color("-freja-hover"));
        };

        Style cardSelected = () -> {
            background.color("-freja-selection");
            border.color("-freja-accent");
        };

        Style favoriteBadge = () -> {
            font.size(14, px).color("-freja-gold");
            background.color("rgba(255,255,255,0.88)");
            padding.size(4, px);
            border.radius(13, px);
        };

        Style formatBadge = () -> {
            font.size(11, px).color("#C8CDD4");
            background.color("rgba(0,0,0,0.5)");
            padding.vertical(2, px).horizontal(5, px);
            border.radius(4, px);
        };

        Style chip = () -> {
            font.size(11, px).weight(700).color("white");
            padding.vertical(3, px).horizontal(8, px);
            border.radius(10, px);
            background.color("-freja-surface");
            cursor.pointer();
        };

        Style headerTitle = () -> font.size(16, px).weight(700).color("-freja-text");

        Style headerCount = () -> {
            font.size(11, px).weight(700).color("-freja-accent-dark");
            background.color("-freja-accent-soft");
            padding.vertical(2, px).horizontal(7, px);
            border.radius(9, px);
        };

        Style empty = () -> {
            font.size(13, px).color("-freja-muted");
            padding.size(24, px);
        };
    }
}
