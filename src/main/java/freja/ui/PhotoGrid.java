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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import freja.model.Folder;
import freja.model.Photo;

/**
 * A virtualized, Picasa-like thumbnail grid.
 * <p>
 * Only the cells overlapping the clip rectangle are painted and only their thumbnails are
 * requested. A library with tens of thousands of photos therefore scrolls with a small, constant
 * cost.
 * </p>
 */
public final class PhotoGrid extends JComponent implements Scrollable {

    /** How the visible photos are divided by headers. */
    public enum GroupMode {
        NONE, DATE, FOLDER
    }

    private static final int OUTER = 26;

    private static final int CELL_GAP = 2;

    private static final int CELL_PADDING = 6;

    private static final int HEADER_HEIGHT = 64;

    private static final int TOP = 8;

    private static final DateTimeFormatter RANGE_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");

    private final ThumbnailCache cache;

    private List<Photo> photos = List.of();

    private final List<LayoutRow> rows = new ArrayList<>();

    private GroupMode grouping = GroupMode.DATE;

    private List<Folder> sections = List.of();

    private Timer scrollAnimator;

    private JScrollBar animatedScrollBar;

    private int scrollTarget;

    private int scrollStartValue;

    private long scrollStartTime;

    private int scrollDuration;

    private String stickyKey;

    /** Suppresses folder-change notifications while a resize rebuilds the virtual layout. */
    private boolean suppressStickyNotification;

    /**
     * The folder a sidebar selection is bringing into view. Until it reaches the top of the
     * viewport - or the user scrolls by hand - the sticky header must not move the sidebar away
     * from it. A folder close to the end of the library cannot always be scrolled to the top, and
     * following the sticky header there would silently select its neighbour.
     */
    private long pinnedFolder = -1;

    private JScrollPane scrollPane;

    private int thumbSize = 170;

    private int columns = 1;

    private int gridLeft = OUTER;

    private int totalHeight;

    /** The lead selection, used by the inspector and the keyboard. */
    private int selectedIndex = -1;

    private final Set<Integer> selectedIndices = new HashSet<>();

    /** Where a Shift-click range starts. */
    private int anchorIndex = -1;

    private int hoverIndex = -1;

    private Consumer<Photo> onSelection = photo -> {
    };

    private Consumer<Photo> onOpen = photo -> {
    };

    private BiConsumer<Photo, Point> onContextMenu = (photo, point) -> {
    };

    private Consumer<Long> onStickyFolder = folderId -> {
    };

    /** Receives the folder whose header's tag chip was clicked. */
    private BiConsumer<Long, Point> onTagFolder = (folderId, point) -> {
    };

    /** Clickable areas of the tag chips painted in the current frame. */
    private final List<TagHit> tagHits = new ArrayList<>();

    private record TagHit(String folderKey, Rectangle bounds) {
    }

    private Consumer<List<Photo>> onRemove = photos -> {
    };

    private Consumer<List<Photo>> onDeleteFromDisk = photos -> {
    };

    /** One visual row, either a date header or a row of cells with per-photo heights. */
    private record LayoutRow(int y, int height, int[] indices, String title, int count, String note, String groupKey,
            int[] cellHeights, List<String> tags) {

        boolean header() {
            return indices.length == 0;
        }
    }

    public PhotoGrid(ThumbnailCache cache) {
        this.cache = cache;
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        setCursor(Cursor.getDefaultCursor());
        setBorder(BorderFactory.createEmptyBorder());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                TagHit hit = tagHitAt(event.getPoint());
                if (hit != null && SwingUtilities.isLeftMouseButton(event)) {
                    try {
                        onTagFolder.accept(Long.parseLong(hit.folderKey()), event.getPoint());
                    } catch (NumberFormatException ignored) {
                    }
                    return;
                }
                int top = getVisibleRect().y;
                if (grouping == GroupMode.FOLDER && stickyKey != null && event.getY() >= top && event.getY() < top + HEADER_HEIGHT) {
                    try {
                        scrollToFolder(Long.parseLong(stickyKey));
                    } catch (NumberFormatException ignored) {
                    }
                    return;
                }
                int index = indexAt(event.getPoint());
                if (index >= 0) {
                    boolean popup = SwingUtilities.isRightMouseButton(event) || event.isPopupTrigger();
                    boolean control = event.isControlDown() || event.isMetaDown();
                    if (popup && selectedIndices.contains(index)) {
                        // Clicking a already-selected item with the right button must keep the
                        // whole selection so the context menu can act on all of it.
                    } else if (event.isShiftDown() && anchorIndex >= 0) {
                        selectRange(anchorIndex, index, control);
                    } else if (control && !popup) {
                        toggleIndex(index);
                        anchorIndex = index;
                    } else {
                        selectIndex(index, true);
                        anchorIndex = index;
                    }
                    if (popup) {
                        onContextMenu.accept(photos.get(index), event.getPoint());
                    }
                } else if (SwingUtilities.isLeftMouseButton(event)) {
                    clearSelection();
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    int index = indexAt(event.getPoint());
                    if (index >= 0) {
                        onOpen.accept(photos.get(index));
                    }
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                if (tagHitAt(event.getPoint()) != null) {
                    setHover(-1);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                int top = getVisibleRect().y;
                if (grouping == GroupMode.FOLDER && stickyKey != null && event.getY() >= top && event.getY() < top + HEADER_HEIGHT) {
                    setHover(-1);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                int index = indexAt(event.getPoint());
                if (index != hoverIndex) {
                    setHover(index);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                setHover(-1);
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                preserveStickyFolder();
            }
        });

        addMouseWheelListener(this::handleMouseWheel);

        installKeys();
    }

    /**
     * A resize changes the column count, which moves every section header. Keep the same folder at
     * the top of the viewport so the sidebar selection does not jump to a different folder.
     */
    private void preserveStickyFolder() {
        String key = stickyKey;
        LayoutRow before = key == null ? null : headerFor(key);
        int offset = before == null ? 0 : getVisibleRect().y - before.y();
        suppressStickyNotification = true;
        try {
            relayout();
        } finally {
            suppressStickyNotification = false;
        }
        if (before != null) {
            LayoutRow after = headerFor(key);
            if (after != null) {
                JScrollPane pane = scrollPane;
                if (pane == null) {
                    pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                }
                if (pane != null) {
                    JScrollBar bar = pane.getVerticalScrollBar();
                    int destination = Math.max(bar.getMinimum(),
                            Math.min(bar.getMaximum() - bar.getVisibleAmount(), after.y() + offset));
                    bar.setValue(destination);
                }
            }
            stickyKey = key;
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        installScrollListener();
    }

    private void installScrollListener() {
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (pane == null) {
            return;
        }
        if (scrollPane == pane) {
            return;
        }
        scrollPane = pane;
        // A sticky header cannot tolerate the viewport's blit optimization: old pixels, including
        // a previously drawn header, can be shifted into the wrong place for one frame.
        pane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        pane.getVerticalScrollBar().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                // Dragging the scroll bar is the user taking over; the sidebar follows again.
                pinnedFolder = -1;
            }
        });
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent event) {
        handleMouseWheel(event);
        if (!event.isConsumed()) {
            super.processMouseWheelEvent(event);
        }
    }

    private void handleMouseWheel(MouseWheelEvent event) {
        if (event.isConsumed()) {
            return;
        }
        JScrollPane pane = scrollPane;
        if (pane == null) {
            pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        }
        if (pane == null) {
            return;
        }
        JScrollBar bar = pane.getVerticalScrollBar();
        int units = event.getUnitsToScroll();
        if (units != 0) {
            // The user is taking over the scrolling, so the sidebar follows the view again.
            pinnedFolder = -1;
            int base = scrollAnimator != null && animatedScrollBar == bar ? scrollTarget : bar.getValue();
            int target = base + units * Math.max(1, bar.getUnitIncrement());
            target = Math.max(bar.getMinimum(), Math.min(bar.getMaximum() - bar.getVisibleAmount(), target));
            animateTo(bar, target);
            event.consume();
        }
    }

    public void setOnSelection(Consumer<Photo> listener) {
        this.onSelection = listener == null ? photo -> {
        } : listener;
    }

    public void setOnOpen(Consumer<Photo> listener) {
        this.onOpen = listener == null ? photo -> {
        } : listener;
    }

    public void setOnContextMenu(BiConsumer<Photo, Point> listener) {
        this.onContextMenu = listener == null ? (photo, point) -> {
        } : listener;
    }

    public void setOnRemove(Consumer<List<Photo>> listener) {
        this.onRemove = listener == null ? photos -> {
        } : listener;
    }

    public void setOnDeleteFromDisk(Consumer<List<Photo>> listener) {
        this.onDeleteFromDisk = listener == null ? photos -> {
        } : listener;
    }

    /** Receives the folder whose header is currently sticky at the top of the viewport. */
    public void setOnStickyFolder(Consumer<Long> listener) {
        this.onStickyFolder = listener == null ? folderId -> {
        } : listener;
    }

    /** Receives the folder to open in the tag editor when its header chip is clicked. */
    public void setOnTagFolder(BiConsumer<Long, Point> listener) {
        this.onTagFolder = listener == null ? (folderId, point) -> {
        } : listener;
    }

    /**
     * Replaces the visible photos.
     *
     * @param photos the photos
     * @param grouping how to divide the photos with headers
     */
    public void setPhotos(List<Photo> photos, GroupMode grouping) {
        setPhotos(photos, grouping, List.of());
    }

    /**
     * Replaces the visible photos.
     *
     * @param photos the photos, ordered by folder when using {@link GroupMode#FOLDER}
     * @param grouping how to divide the photos with headers
     * @param sections the folder sections in display order, including empty folders
     */
    public void setPhotos(List<Photo> photos, GroupMode grouping, List<Folder> sections) {
        Set<Long> selectedIds = new HashSet<>();
        for (int index : selectedIndices) {
            if (index >= 0 && index < photos.size()) {
                selectedIds.add(photos.get(index).id());
            }
        }
        long leadId = selectedPhoto() == null ? -1 : selectedPhoto().id();
        this.photos = List.copyOf(photos);
        this.grouping = grouping;
        this.sections = List.copyOf(sections);
        selectedIndices.clear();
        int restored = -1;
        int first = -1;
        for (int i = 0; i < this.photos.size(); i++) {
            long id = this.photos.get(i).id();
            if (selectedIds.contains(id)) {
                selectedIndices.add(i);
                if (first < 0) {
                    first = i;
                }
            }
            if (id == leadId) {
                restored = i;
            }
        }
        selectedIndex = selectedIndices.contains(restored) ? restored : first;
        anchorIndex = selectedIndex;
        hoverIndex = -1;
        relayoutKeepingFolder();
        if (grouping != GroupMode.FOLDER || (pinnedFolder >= 0 && headerFor(Long.toString(pinnedFolder)) == null)) {
            // Leaving the folder grouping, or the selected folder was filtered away.
            pinnedFolder = -1;
        }
        repaint();
    }

    /**
     * Sets the size of one thumbnail.
     *
     * @param size the edge in pixels, from 120 to 220
     */
    public void setThumbSize(int size) {
        int value = Math.max(120, Math.min(220, size));
        if (value == thumbSize) {
            return;
        }
        thumbSize = value;
        relayoutKeepingFolder();
        repaint();
    }

    public int thumbSize() {
        return thumbSize;
    }

    /**
     * Returns the selected photo.
     *
     * @return the selected photo, or null
     */
    public Photo selectedPhoto() {
        return selectedIndex >= 0 && selectedIndex < photos.size() ? photos.get(selectedIndex) : null;
    }

    /**
     * Returns every selected photo in display order.
     *
     * @return the selected photos, or an empty list
     */
    public List<Photo> selectedPhotos() {
        return selectedIndices.stream().sorted().map(photos::get).toList();
    }

    /**
     * Selects a photo by identity and scrolls it into view.
     *
     * @param photo a photo
     */
    public void select(Photo photo) {
        if (photo == null) {
            return;
        }
        for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).id() == photo.id()) {
                selectIndex(i, true);
                return;
            }
        }
    }

    /**
     * Selects the first photo.
     */
    public void selectFirst() {
        if (!photos.isEmpty()) {
            selectIndex(0, true);
        }
    }

    /**
     * Clears the selection.
     */
    public void clearSelection() {
        if (!selectedIndices.isEmpty()) {
            selectedIndices.clear();
            selectedIndex = -1;
            anchorIndex = -1;
            onSelection.accept(null);
            repaint();
        }
    }

    /**
     * Returns the visible photos.
     *
     * @return the photos in display order
     */
    public List<Photo> photos() {
        return photos;
    }

    private void selectIndex(int index, boolean notify) {
        if (index < 0 || index >= photos.size()) {
            return;
        }
        boolean changed = selectedIndex != index || selectedIndices.size() != 1 || !selectedIndices.contains(index);
        selectedIndices.clear();
        selectedIndices.add(index);
        selectedIndex = index;
        anchorIndex = index;
        if (changed) {
            repaint();
        }
        if (notify) {
            onSelection.accept(photos.get(index));
        }
        scrollTo(index);
    }

    /** Adds or removes one item, as a Ctrl-click does. */
    private void toggleIndex(int index) {
        if (index < 0 || index >= photos.size()) {
            return;
        }
        if (selectedIndices.remove(index)) {
            if (selectedIndex == index) {
                selectedIndex = selectedIndices.stream().sorted().mapToInt(Integer::intValue).findFirst().orElse(-1);
            }
        } else {
            selectedIndices.add(index);
            selectedIndex = index;
        }
        onSelection.accept(selectedPhoto());
        repaint(index);
        scrollTo(index);
    }

    /** Selects the span between two items, as a Shift-click does. */
    private void selectRange(int from, int to, boolean additive) {
        if (from < 0 || to < 0 || from >= photos.size() || to >= photos.size()) {
            return;
        }
        if (!additive) {
            selectedIndices.clear();
        }
        int start = Math.min(from, to);
        int end = Math.max(from, to);
        for (int i = start; i <= end; i++) {
            selectedIndices.add(i);
        }
        selectedIndex = to;
        anchorIndex = from;
        onSelection.accept(photos.get(to));
        repaint();
        scrollTo(to);
    }

    private void selectAll() {
        if (photos.isEmpty()) {
            return;
        }
        for (int i = 0; i < photos.size(); i++) {
            selectedIndices.add(i);
        }
        selectedIndex = photos.size() - 1;
        anchorIndex = 0;
        onSelection.accept(photos.get(selectedIndex));
        repaint();
    }

    private void repaint(int index) {
        Rectangle bounds = cellRect(index);
        if (bounds != null) {
            repaint(bounds);
        }
    }

    private void setHover(int index) {
        if (hoverIndex == index) {
            return;
        }
        Rectangle old = cellRect(hoverIndex);
        hoverIndex = index;
        setCursor(index < 0 ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (old != null) {
            repaint(old);
        }
        Rectangle current = cellRect(index);
        if (current != null) {
            repaint(current);
        }
    }

    private void installKeys() {
        bind("LEFT", () -> move(-1, false));
        bind("RIGHT", () -> move(1, false));
        bind("UP", () -> move(-columns, false));
        bind("DOWN", () -> move(columns, false));
        bind("shift LEFT", () -> move(-1, true));
        bind("shift RIGHT", () -> move(1, true));
        bind("shift UP", () -> move(-columns, true));
        bind("shift DOWN", () -> move(columns, true));
        bind("control A", this::selectAll);
        bind("meta A", this::selectAll);
        bind("ESCAPE", this::clearSelection);
        bind("DELETE", () -> deleteSelected(false));
        bind("shift DELETE", () -> deleteSelected(true));
        bind("HOME", () -> moveTo(0, false));
        bind("END", () -> moveTo(photos.size() - 1, false));
        bind("shift END", () -> moveTo(photos.size() - 1, true));
        bind("shift HOME", () -> moveTo(0, true));
        bind("ENTER", () -> {
            if (selectedPhoto() != null) {
                onOpen.accept(selectedPhoto());
            }
        });
        bind("SPACE", () -> {
            if (selectedPhoto() != null) {
                onOpen.accept(selectedPhoto());
            }
        });
    }

    private void deleteSelected(boolean fromDisk) {
        List<Photo> targets = selectedPhotos();
        if (targets.isEmpty()) {
            return;
        }
        if (fromDisk) {
            onDeleteFromDisk.accept(targets);
        } else {
            onRemove.accept(targets);
        }
    }

    private void bind(String key, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // Keyboard navigation moves the view itself, so the sidebar follows again.
                pinnedFolder = -1;
                action.run();
            }
        });
    }

    private void move(int delta, boolean extend) {
        int base = selectedIndex < 0 ? 0 : selectedIndex;
        moveTo(Math.max(0, Math.min(photos.size() - 1, base + delta)), extend);
    }

    private void moveTo(int index, boolean extend) {
        if (index < 0 || index >= photos.size()) {
            return;
        }
        if (extend && anchorIndex >= 0) {
            selectRange(anchorIndex, index, false);
        } else {
            selectIndex(index, true);
            anchorIndex = index;
        }
    }

    private void scrollTo(int index) {
        Rectangle bounds = cellRect(index);
        if (bounds != null) {
            bounds.grow(0, 12);
            animateToVisible(bounds);
        }
    }

    /**
     * Scrolls to the header of a folder section. Sections at most three positions away in the
     * display order animate; anything further jumps directly.
     *
     * @param folderId the folder identifier
     * @return true when the folder has a section in the current layout
     */
    public boolean scrollToFolder(long folderId) {
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (pane != null) {
            // Laying out here can reflow the whole grid - the first show, or a divider move - so
            // the target below is measured only after the rows have their final positions.
            pane.validate();
        }
        String key = Long.toString(folderId);
        int target = -1;
        int targetOrder = -1;
        int currentOrder = -1;
        int order = -1;
        for (LayoutRow row : rows) {
            if (!row.header() || row.groupKey() == null) {
                continue;
            }
            order++;
            if (key.equals(row.groupKey())) {
                target = row.y();
                targetOrder = order;
            }
            if (row.groupKey().equals(stickyKey)) {
                currentOrder = order;
            }
        }
        if (target < 0) {
            return false;
        }
        pinnedFolder = folderId;
        if (pane == null) {
            return true;
        }
        JScrollBar bar = pane.getVerticalScrollBar();
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
        int destination = Math.max(minimum, Math.min(maximum, target));
        // Neighbouring sections animate; anything further is a jump. Folder order is the measure,
        // not pixels: a section can hold hundreds of photos and still be only one step away.
        boolean nearby = currentOrder >= 0 && targetOrder >= 0 && Math.abs(targetOrder - currentOrder) <= 3;
        if (nearby) {
            scrollBarTo(bar, destination);
        } else {
            stopScrollAnimation();
            bar.setValue(destination);
        }
        return true;
    }

    /** Pins the sidebar selection so sticky headers do not move it until the folder reaches the top. */
    public void pinFolder(long folderId) {
        pinnedFolder = folderId;
    }

    /** Drops the selection pin, e.g. when the whole-library view is chosen. */
    public void clearPinnedFolder() {
        pinnedFolder = -1;
    }

    /**
     * The folder whose section will sit at the top once the running scroll animation settles, or -1
     * when nothing is animating. A folder jump glides through the sections in between, and the
     * sticky header reports each of them as it passes; persisting the current one while the window
     * closes would reopen a folder the user was only travelling across.
     *
     * @return the destination folder identifier, or -1
     */
    public long scrollTargetFolder() {
        if (pinnedFolder >= 0) {
            return pinnedFolder;
        }
        if (scrollAnimator == null || grouping != GroupMode.FOLDER || rows.isEmpty()) {
            return -1;
        }
        return folderAt(scrollTarget);
    }

    /**
     * The folder whose section currently owns the top of the viewport, read from the layout rather
     * than from the last painted sticky header. The header only catches up on the next paint, so a
     * window closing in between could otherwise persist an older folder.
     *
     * @return the folder identifier, or -1
     */
    public long visibleFolderId() {
        if (grouping != GroupMode.FOLDER || rows.isEmpty()) {
            return -1;
        }
        return folderAt(getVisibleRect().y);
    }

    private long folderAt(int y) {
        LayoutRow header = currentFolderHeader(y);
        if (header == null || header.groupKey() == null) {
            return -1;
        }
        try {
            return Long.parseLong(header.groupKey());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Brings the view back to the first photo. Selecting the entry the grid is already showing is a
     * deliberate request to return to its start, so it needs the same glide the folder jump gives.
     */
    public void scrollToTop() {
        pinnedFolder = -1;
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (pane == null) {
            return;
        }
        pane.validate();
        JScrollBar bar = pane.getVerticalScrollBar();
        animateTo(bar, bar.getMinimum());
    }

    private void animateToVisible(Rectangle bounds) {
        JScrollPane pane = scrollPane;
        if (pane == null) {
            pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        }
        if (pane == null) {
            return;
        }
        pane.validate();
        JScrollBar bar = pane.getVerticalScrollBar();
        Rectangle visible = getVisibleRect();
        int destination = bar.getValue();
        if (bounds.y < visible.y) {
            destination += bounds.y - visible.y - 12;
        } else if (bounds.y + bounds.height > visible.y + visible.height) {
            destination += bounds.y + bounds.height - visible.y - visible.height + 12;
        }
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
        scrollBarTo(bar, Math.max(minimum, Math.min(maximum, destination)));
    }

    private void scrollBarTo(JScrollBar bar, int destination) {
        animateTo(bar, destination);
    }

    /** Time-based cubic ease-out: a consistent, smooth glide regardless of the distance. */
    private void animateTo(JScrollBar bar, int destination) {
        if (bar == null) {
            return;
        }
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
        int target = Math.max(minimum, Math.min(maximum, destination));
        int start = bar.getValue();
        if (start == target) {
            stopScrollAnimation();
            return;
        }
        animatedScrollBar = bar;
        scrollTarget = target;
        scrollStartValue = start;
        scrollStartTime = System.nanoTime();
        scrollDuration = Math.max(160, Math.min(420, Math.abs(target - start) / 2 + 120));
        if (scrollAnimator != null) {
            scrollAnimator.stop();
        }
        scrollAnimator = new Timer(10, event -> {
            JScrollBar current = animatedScrollBar;
            if (current == null) {
                stopScrollAnimation();
                return;
            }
            long elapsed = (System.nanoTime() - scrollStartTime) / 1_000_000L;
            double progress = Math.min(1.0, (double) elapsed / scrollDuration);
            double eased = 1 - Math.pow(1 - progress, 3);
            current.setValue((int) Math.round(scrollStartValue + (scrollTarget - scrollStartValue) * eased));
            if (progress >= 1.0) {
                stopScrollAnimation();
            }
        });
        scrollAnimator.setInitialDelay(0);
        scrollAnimator.start();
    }

    private void stopScrollAnimation() {
        if (scrollAnimator != null) {
            scrollAnimator.stop();
            scrollAnimator = null;
        }
        if (animatedScrollBar != null) {
            scrollTarget = animatedScrollBar.getValue();
        }
    }

    /**
     * Relays out the rows while keeping the same folder anchored at the top of the viewport. A
     * refresh - a scan importing photos, a folder count changing - grows the sections above the
     * current one, and keeping only the pixel position would silently move the view, and with it
     * the sidebar selection, to a different folder.
     */
    private void relayoutKeepingFolder() {
        LayoutRow before = visibleHeader();
        String key = before == null ? null : before.groupKey();
        int offset = before == null || scrollAnimator != null ? 0 : getVisibleRect().y - before.y();
        if (scrollAnimator != null) {
            // The animation measured its destination against the old rows; settle there instead of
            // letting the new layout reinterpret the stale pixel target.
            stopScrollAnimation();
        }
        relayout();
        if (key != null) {
            LayoutRow after = headerFor(key);
            if (after != null) {
                scrollBarToY(after.y() + offset);
            }
        }
    }

    /** The folder section owning the top of the viewport, or the animation's destination. */
    private LayoutRow visibleHeader() {
        if (grouping != GroupMode.FOLDER || rows.isEmpty()) {
            return null;
        }
        return currentFolderHeader(scrollAnimator != null ? scrollTarget : getVisibleRect().y);
    }

    private void scrollBarToY(int y) {
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (pane == null) {
            return;
        }
        pane.validate();
        JScrollBar bar = pane.getVerticalScrollBar();
        int minimum = bar.getMinimum();
        int maximum = Math.max(minimum, bar.getMaximum() - bar.getVisibleAmount());
        bar.setValue(Math.max(minimum, Math.min(maximum, y)));
    }

    private void relayout() {
        rows.clear();
        int width = Math.max(320, getWidth());
        int cellWidth = thumbSize + CELL_GAP + CELL_PADDING * 2;
        columns = Math.max(1, (width - OUTER * 2) / cellWidth);
        gridLeft = Math.max(OUTER, (width - columns * cellWidth) / 2);
        int y = TOP;

        if (photos.isEmpty() && !(grouping == GroupMode.FOLDER && !sections.isEmpty())) {
            totalHeight = Math.max(getParent() == null ? 300 : getParent().getHeight(), 300);
        } else if (grouping == GroupMode.FOLDER) {
            if (sections.isEmpty()) {
                y = addPhotoRows(y, 0, photos.size());
            } else {
                int index = 0;
                for (Folder section : sections) {
                    int start = index;
                    while (index < photos.size() && photos.get(index).folderId() == section.id()) {
                        index++;
                    }
                    rows.add(new LayoutRow(y, HEADER_HEIGHT, new int[0], section.name(), index - start, dateRange(start, index), Long.toString(section.id()), null, section.tags()));
                    y += HEADER_HEIGHT;
                    y = addPhotoRows(y, start, index);
                    y += 8;
                }
                if (index < photos.size()) {
                    int start = index;
                    index = photos.size();
                    rows.add(new LayoutRow(y, HEADER_HEIGHT, new int[0], "その他", index - start, dateRange(start, index), null, null, List.of()));
                    y += HEADER_HEIGHT;
                    y = addPhotoRows(y, start, index);
                    y += 8;
                }
            }
            totalHeight = y + 24;
        } else if (grouping == GroupMode.DATE) {
            int index = 0;
            while (index < photos.size()) {
                Photo first = photos.get(index);
                String key = first.dateLabel();
                int start = index;
                index++;
                while (index < photos.size() && key.equals(photos.get(index).dateLabel())) {
                    index++;
                }
                rows.add(new LayoutRow(y, HEADER_HEIGHT, new int[0], key, index - start, null, key, null, List.of()));
                y += HEADER_HEIGHT;
                y = addPhotoRows(y, start, index);
                y += 8;
            }
            totalHeight = y + 20;
        } else {
            y = addPhotoRows(y, 0, photos.size());
            totalHeight = y + 24;
        }
        setPreferredSize(new Dimension(width, Math.max(totalHeight, 300)));
        revalidate();
    }

    /** Lays out a band of photos with a fixed width and aspect-ratio-dependent heights. */
    private int addPhotoRows(int y, int from, int to) {
        for (int start = from; start < to; start += columns) {
            int end = Math.min(to, start + columns);
            int[] indices = new int[end - start];
            int[] heights = new int[end - start];
            int tallest = 0;
            for (int i = 0; i < indices.length; i++) {
                indices[i] = start + i;
                heights[i] = imageHeight(photos.get(start + i));
                tallest = Math.max(tallest, heights[i]);
            }
            int height = tallest + CELL_PADDING * 2;
            rows.add(new LayoutRow(y, height, indices, null, 0, null, null, heights, List.of()));
            y += height;
        }
        return y;
    }

    /**
     * Landscape photos get a shorter cell so rows stay dense; portrait photos are capped at a
     * square cell and are centred with gaps on the left and right.
     */
    private int imageHeight(Photo photo) {
        int width = photo.width();
        int height = photo.height();
        if (width <= 0 || height <= 0) {
            return thumbSize;
        }
        long scaled = Math.round((double) thumbSize * height / width);
        return (int) Math.max(60, Math.min(thumbSize, scaled));
    }

    private String dateRange(int from, int to) {
        LocalDate minimum = null;
        LocalDate maximum = null;
        for (int i = from; i < to; i++) {
            LocalDate date = photos.get(i).capturedLocal().toLocalDate();
            if (minimum == null || date.isBefore(minimum)) {
                minimum = date;
            }
            if (maximum == null || date.isAfter(maximum)) {
                maximum = date;
            }
        }
        if (minimum == null) {
            return null;
        }
        return minimum.equals(maximum) ? minimum.format(RANGE_DATE) : minimum.format(RANGE_DATE) + " – " + maximum.format(RANGE_DATE);
    }

    private int indexAt(Point point) {
        for (LayoutRow row : rows) {
            if (row.header() || point.y < row.y() || point.y >= row.y() + row.height()) {
                continue;
            }
            for (int column = 0; column < row.indices().length; column++) {
                if (cellBounds(row, column).contains(point)) {
                    return row.indices()[column];
                }
            }
            return -1;
        }
        return -1;
    }

    private Rectangle cellRect(int index) {
        if (index < 0 || index >= photos.size()) {
            return null;
        }
        for (LayoutRow row : rows) {
            for (int column = 0; column < row.indices().length; column++) {
                if (row.indices()[column] == index) {
                    return cellBounds(row, column);
                }
            }
        }
        return null;
    }

    private Rectangle cellBounds(LayoutRow row, int column) {
        int cellWidth = thumbSize + CELL_GAP + CELL_PADDING * 2;
        int height = row.cellHeights() == null ? thumbSize : row.cellHeights()[column];
        int tallest = 0;
        if (row.cellHeights() != null) {
            for (int candidate : row.cellHeights()) {
                tallest = Math.max(tallest, candidate);
            }
        }
        // A short landscape photo beside a portrait one is centred in the taller row.
        int top = row.y() + CELL_PADDING + (tallest - height) / 2;
        return new Rectangle(gridLeft + column * cellWidth + CELL_PADDING, top, thumbSize, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Theme.BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            tagHits.clear();

            if (rows.isEmpty()) {
                paintEmpty(g);
                return;
            }

            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            int top = getVisibleRect().y;
            updateSticky(top);

            for (LayoutRow row : rows) {
                if (row.y() + row.height() < clip.y || row.y() > clip.y + clip.height) {
                    continue;
                }
                if (row.header()) {
                    if (grouping == GroupMode.FOLDER && stickyKey != null && stickyKey.equals(row.groupKey())) {
                        continue;
                    }
                    paintHeader(g, row, row.y(), false);
                } else {
                    for (int column = 0; column < row.indices().length; column++) {
                        paintCell(g, row.indices()[column], row, column);
                    }
                }
            }

            paintSticky(g, top);
        } finally {
            g.dispose();
        }
    }

    private void updateSticky(int top) {
        if (suppressStickyNotification) {
            return;
        }
        if (grouping != GroupMode.FOLDER || rows.isEmpty()) {
            stickyKey = null;
            pinnedFolder = -1;
            return;
        }
        LayoutRow current = currentFolderHeader(top);
        String key = current == null ? null : current.groupKey();
        boolean changed = key == null ? stickyKey != null : !key.equals(stickyKey);
        stickyKey = key;
        if (pinnedFolder >= 0) {
            if (key == null || !Long.toString(pinnedFolder).equals(key)) {
                // Still travelling towards - or unable to reach the top because the section is too
                // close to the end. Either way the sidebar keeps the folder the user picked.
                return;
            }
            pinnedFolder = -1;
        }
        if (changed && key != null) {
            try {
                onStickyFolder.accept(Long.parseLong(key));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private LayoutRow currentFolderHeader(int top) {
        LayoutRow first = null;
        LayoutRow passed = null;
        for (LayoutRow row : rows) {
            if (!row.header() || row.groupKey() == null) {
                continue;
            }
            if (first == null) {
                first = row;
            }
            if (row.y() <= top) {
                passed = row;
            } else {
                break;
            }
        }
        return passed == null ? first : passed;
    }

    private void paintSticky(Graphics2D g, int top) {
        if (grouping != GroupMode.FOLDER || stickyKey == null) {
            return;
        }
        LayoutRow current = headerFor(stickyKey);
        if (current == null) {
            return;
        }

        // CSS-like sticky headers are pushed by the next section header as it reaches the top.
        // Drawing the current header at a geometric offset avoids painting a second, time-based
        // copy of the previous header, which caused the flicker.
        LayoutRow next = nextHeaderAfter(stickyKey);
        int stickyY = top;
        if (next != null && current.y() <= top) {
            int distance = next.y() - top;
            if (distance >= 0 && distance < HEADER_HEIGHT) {
                stickyY = top - (HEADER_HEIGHT - distance);
            }
        }

        java.awt.Shape clip = g.getClip();
        try {
            java.awt.Rectangle band = new java.awt.Rectangle(0, top, getWidth(), HEADER_HEIGHT);
            if (clip != null) {
                band = band.intersection(clip.getBounds());
            }
            if (band.isEmpty()) {
                return;
            }
            g.setClip(band);
            paintHeader(g, current, stickyY, true);
        } finally {
            g.setClip(clip);
        }
    }

    private LayoutRow nextHeaderAfter(String key) {
        boolean found = false;
        for (LayoutRow row : rows) {
            if (row.header() && row.groupKey() != null) {
                if (found) {
                    return row;
                }
                if (row.groupKey().equals(key)) {
                    found = true;
                }
            }
        }
        return null;
    }

    private LayoutRow headerFor(String key) {
        for (LayoutRow row : rows) {
            if (row.header() && key.equals(row.groupKey())) {
                return row;
            }
        }
        return null;
    }

    /** Draws a folder's tags as chips on the metadata line, stopping with an ellipsis when full. */
    private void paintTagChips(Graphics2D g, String folderKey, List<String> tags, int x, int baseline, int available) {
        Font font = Theme.font(Font.BOLD, 11);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int gap = 6;
        int chipHeight = 20;
        int chipY = baseline - 14;
        String addText = "+ タグ";
        int addWidth = metrics.stringWidth(addText) + 16;
        int drawn = 0;
        int used = 0;
        for (String tag : tags) {
            int width = metrics.stringWidth(tag) + 24;
            int leading = drawn == 0 ? 0 : gap;
            if (used + leading + width + addWidth > available) {
                break;
            }
            int chipX = x + used + leading;
            Color color = TagColors.of(tag);
            g.setColor(color);
            g.fill(new RoundRectangle2D.Float(chipX, chipY, width, chipHeight, 10, 10));
            int iconSize = 11;
            Icons.tag(iconSize, Color.WHITE).paintIcon(this, g, chipX + 6, chipY + (chipHeight - iconSize) / 2);
            g.setColor(Color.WHITE);
            g.drawString(tag, chipX + 20, baseline);
            used += leading + width;
            drawn++;
        }
        if (drawn < tags.size()) {
            int leading = drawn == 0 ? 0 : gap;
            g.setColor(Theme.MUTED);
            g.drawString("…", x + used + leading, baseline);
            used += leading + metrics.stringWidth("…") + 8;
        }
        // A permanent "add" chip makes tagging a one-click action from the header itself.
        if (used + addWidth <= available) {
            int addX = x + used + (used == 0 ? 0 : gap);
            g.setColor(Theme.SURFACE);
            g.fill(new RoundRectangle2D.Float(addX, chipY, addWidth, chipHeight, 10, 10));
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Float(addX, chipY, addWidth, chipHeight, 10, 10));
            g.setStroke(new BasicStroke(1f));
            g.drawString(addText, addX + 8, baseline);
            used = addX + addWidth - x;
        }
        // The whole metadata line is the click target, not just the visible chips: this keeps the
        // "+ タグ" affordance clickable even when there is not enough room to draw every chip.
        tagHits.add(new TagHit(folderKey, new Rectangle(x, chipY, available, chipHeight)));
    }

    private TagHit tagHitAt(Point point) {
        for (TagHit hit : tagHits) {
            if (hit.bounds().contains(point)) {
                return hit;
            }
        }
        // Fallback for frames whose hit rectangles have not been painted yet: derive the metadata
        // line from the current layout so a click is never silently lost.
        FontMetrics countMetrics = getFontMetrics(Theme.font(Font.BOLD, 10));
        for (LayoutRow row : rows) {
            if (!row.header() || row.groupKey() == null) {
                continue;
            }
            int y = row.y();
            int chipY = y + 47 - 14;
            if (point.y < chipY || point.y >= chipY + 20) {
                continue;
            }
            int badgeWidth = countMetrics.stringWidth(row.count() + "枚") + 14;
            int from = OUTER + badgeWidth + 10;
            if (point.x >= from && point.x <= getWidth() - OUTER) {
                return new TagHit(row.groupKey(), new Rectangle(from, chipY, getWidth() - OUTER - from, 20));
            }
        }
        return null;
    }


    private void paintHeader(Graphics2D g, LayoutRow row, int y, boolean sticky) {
        // Headers start at the outer edge, independent of how the thumbnail grid is centred.
        int x = OUTER;
        int center = y + HEADER_HEIGHT / 2;
        boolean folderHeader = grouping == GroupMode.FOLDER;
        if (sticky) {
            g.setColor(Theme.SURFACE);
            g.fillRect(0, y, getWidth(), HEADER_HEIGHT);
            g.setColor(Theme.BORDER);
            g.fillRect(0, y + HEADER_HEIGHT - 1, getWidth(), 1);
            g.setColor(new Color(0, 0, 0, 18));
            g.fillRect(0, y + HEADER_HEIGHT, getWidth(), 2);
        }

        if (!folderHeader) {
            Icons.clock(17, Theme.MUTED).paintIcon(this, g, x, center - 9);
        }
        int textX = folderHeader ? x : x + 26;
        int titleBaseline = y + 27;
        Font titleFont = Theme.font(Font.BOLD, 16);
        g.setFont(titleFont);
        FontMetrics titleMetrics = g.getFontMetrics();
        int available = Math.max(80, getWidth() - textX - OUTER);
        g.setColor(Theme.TEXT);
        String title = truncate(row.title(), titleMetrics, available);
        g.drawString(title, textX, titleBaseline);

        // All metadata — count, date range and folder tags — sits on one line under the title.
        String count = row.count() + "枚";
        g.setFont(Theme.font(Font.BOLD, 10));
        FontMetrics countMetrics = g.getFontMetrics();
        int badgeWidth = countMetrics.stringWidth(count) + 14;
        int metaBaseline = y + 47;
        g.setColor(Theme.ACCENT_SOFT);
        g.fill(new RoundRectangle2D.Float(textX, metaBaseline - 13, badgeWidth, 18, 9, 9));
        g.setColor(Theme.ACCENT_DARK);
        g.drawString(count, textX + 7, metaBaseline);

        int metaX = textX + badgeWidth + 10;
        int metaRight = getWidth() - OUTER;
        if (row.note() != null && metaRight - metaX > 30) {
            Font noteFont = Theme.font(Font.PLAIN, 11);
            g.setFont(noteFont);
            FontMetrics noteMetrics = g.getFontMetrics();
            int room = metaRight - metaX;
            String note = truncate(row.note(), noteMetrics, room);
            g.setColor(Theme.MUTED);
            g.drawString(note, metaX, metaBaseline);
            metaX += Math.min(noteMetrics.stringWidth(row.note()), room) + 10;
        }
        if (folderHeader && row.groupKey() != null && metaRight - metaX > 24) {
            paintTagChips(g, row.groupKey(), row.tags(), metaX, metaBaseline, metaRight - metaX);
        }

        int lineStart = textX;
        int lineEnd = Math.max(lineStart, getWidth() - OUTER);
        if (!sticky) {
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(lineStart, y + HEADER_HEIGHT - 1, lineEnd, y + HEADER_HEIGHT - 1);
        }
    }

    private void paintCell(Graphics2D g, int index, LayoutRow row, int column) {
        Photo photo = photos.get(index);
        Rectangle cell = cellBounds(row, column);
        int cardX = cell.x;
        int cardY = cell.y;
        int cardWidth = cell.width;
        int cardHeight = cell.height;
        boolean selected = selectedIndices.contains(index);
        boolean hovered = index == hoverIndex;

        // Thumbnails sit directly on the grid background; no card fill or shadow.
        Rectangle inner = new Rectangle(cardX + 3, cardY + 3, cardWidth - 6, cardHeight - 6);
        BufferedImage image = cache.get(photo.id(), photo.modifiedAt());
        if (image == null) {
            // Request the thumbnail but draw no placeholder: a grey icon flashing before the real
            // image is more distracting than an empty cell.
            cache.request(photo.id(), photo.modifiedAt(), this::repaint);
        } else {
            double scale = Math.min((double) inner.width / image.getWidth(), (double) inner.height / image.getHeight());
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = inner.x + (inner.width - width) / 2;
            int y = inner.y + (inner.height - height) / 2;
            g.drawImage(image, x, y, width, height, null);
        }

        // Match the sidebar's navigation rows: a translucent rounded tint, not a hard outline.
        if (selected || hovered) {
            g.setColor(selected ? withAlpha(Theme.ACCENT, 80) : withAlpha(Theme.HOVER, 110));
            g.fill(new RoundRectangle2D.Float(cardX + 1, cardY + 1, cardWidth - 2, cardHeight - 2, 12, 12));
        }

        if (photo.favorite()) {
            g.setColor(new Color(255, 255, 255, 225));
            g.fill(new Ellipse2D.Float(cardX + cardWidth - 34, cardY + 8, 26, 26));
            Icons.star(17, Theme.GOLD, true).paintIcon(this, g, cardX + cardWidth - 29, cardY + 13);
        }

        // The file name is shown in the status bar, so the cell contains only the thumbnail.
    }

    private void paintEmpty(Graphics2D g) {
        int centerX = getWidth() / 2;
        int centerY = Math.max(130, getHeight() / 2 - 40);
        Icons.image(56, Theme.FAINT).paintIcon(this, g, centerX - 28, centerY - 62);
        g.setFont(Theme.font(Font.BOLD, 18));
        g.setColor(Theme.TEXT);
        String title = "写真がありません";
        drawCentered(g, title, centerX, centerY + 16);
        g.setFont(Theme.font(Font.PLAIN, 13));
        g.setColor(Theme.MUTED);
        drawCentered(g, "フォルダを追加するか、検索条件を変えてください。", centerX, centerY + 42);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, y);
    }

    private static String truncate(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int width = metrics.stringWidth(ellipsis);
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) + width > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(960, 720);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 36;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(36, visibleRect.height - 60) : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
