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
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

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

    private static final int OUTER = 26;

    private static final int CELL_GAP = 18;

    private static final int LABEL_HEIGHT = 24;

    private static final int HEADER_HEIGHT = 52;

    private static final int TOP = 8;

    private final ThumbnailCache cache;

    private List<Photo> photos = List.of();

    private final List<LayoutRow> rows = new ArrayList<>();

    private boolean grouped = true;

    private int thumbSize = 190;

    private int columns = 1;

    private int gridLeft = OUTER;

    private int totalHeight;

    private int selectedIndex = -1;

    private int hoverIndex = -1;

    private Consumer<Photo> onSelection = photo -> {
    };

    private Consumer<Photo> onOpen = photo -> {
    };

    private BiConsumer<Photo, Point> onContextMenu = (photo, point) -> {
    };

    /** One visual row, either a date header or a row of cells. */
    private record LayoutRow(int y, int height, int[] indices, String title, String subtitle) {

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
        setBorder(null);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                int index = indexAt(event.getPoint());
                if (index >= 0) {
                    selectIndex(index, true);
                    if (SwingUtilities.isRightMouseButton(event) || event.isPopupTrigger()) {
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
                relayout();
            }
        });

        installKeys();
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

    /**
     * Replaces the visible photos.
     *
     * @param photos the photos
     * @param grouped whether to insert date headers
     */
    public void setPhotos(List<Photo> photos, boolean grouped) {
        long selectedId = selectedPhoto() == null ? -1 : selectedPhoto().id();
        this.photos = List.copyOf(photos);
        this.grouped = grouped;
        int restored = -1;
        if (selectedId >= 0) {
            for (int i = 0; i < this.photos.size(); i++) {
                if (this.photos.get(i).id() == selectedId) {
                    restored = i;
                    break;
                }
            }
        }
        selectedIndex = restored;
        hoverIndex = -1;
        relayout();
        repaint();
    }

    /**
     * Sets the size of one thumbnail.
     *
     * @param size the edge in pixels, from 120 to 300
     */
    public void setThumbSize(int size) {
        int value = Math.max(120, Math.min(300, size));
        if (value == thumbSize) {
            return;
        }
        thumbSize = value;
        relayout();
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
        if (selectedIndex >= 0) {
            selectedIndex = -1;
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
        if (selectedIndex != index) {
            selectedIndex = index;
            repaint();
        }
        if (notify) {
            onSelection.accept(photos.get(index));
        }
        scrollTo(index);
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
        bind("LEFT", () -> move(-1));
        bind("RIGHT", () -> move(1));
        bind("UP", () -> move(-columns));
        bind("DOWN", () -> move(columns));
        bind("HOME", () -> selectIndex(0, true));
        bind("END", () -> selectIndex(photos.size() - 1, true));
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

    private void bind(String key, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void move(int delta) {
        int next;
        if (selectedIndex < 0) {
            next = photos.isEmpty() ? -1 : 0;
        } else {
            next = Math.max(0, Math.min(photos.size() - 1, selectedIndex + delta));
        }
        if (next >= 0) {
            selectIndex(next, true);
        }
    }

    private void scrollTo(int index) {
        Rectangle bounds = cellRect(index);
        if (bounds != null) {
            bounds.grow(0, 12);
            scrollRectToVisible(bounds);
        }
    }

    private void relayout() {
        rows.clear();
        int width = Math.max(320, getWidth());
        int cellWidth = thumbSize + CELL_GAP + 22;
        int cellHeight = thumbSize + LABEL_HEIGHT + 22;
        columns = Math.max(1, (width - OUTER * 2) / cellWidth);
        gridLeft = Math.max(OUTER, (width - columns * cellWidth) / 2);
        int y = TOP;

        if (photos.isEmpty()) {
            totalHeight = Math.max(getParent() == null ? 300 : getParent().getHeight(), 300);
        } else if (grouped) {
            int index = 0;
            while (index < photos.size()) {
                String date = photos.get(index).dateLabel();
                int start = index;
                index++;
                while (index < photos.size() && date.equals(photos.get(index).dateLabel())) {
                    index++;
                }
                rows.add(new LayoutRow(y, HEADER_HEIGHT, new int[0], date, (index - start) + "枚"));
                y += HEADER_HEIGHT;
                for (int from = start; from < index; from += columns) {
                    int to = Math.min(index, from + columns);
                    int[] indices = new int[to - from];
                    for (int i = 0; i < indices.length; i++) {
                        indices[i] = from + i;
                    }
                    rows.add(new LayoutRow(y, cellHeight, indices, null, null));
                    y += cellHeight;
                }
                y += 8;
            }
            totalHeight = y + 20;
        } else {
            for (int from = 0; from < photos.size(); from += columns) {
                int to = Math.min(photos.size(), from + columns);
                int[] indices = new int[to - from];
                for (int i = 0; i < indices.length; i++) {
                    indices[i] = from + i;
                }
                rows.add(new LayoutRow(y, cellHeight, indices, null, null));
                y += cellHeight;
            }
            totalHeight = y + 24;
        }
        setPreferredSize(new Dimension(width, Math.max(totalHeight, 300)));
        revalidate();
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
        int cellWidth = thumbSize + CELL_GAP + 22;
        int cellHeight = thumbSize + LABEL_HEIGHT + 22;
        return new Rectangle(gridLeft + column * cellWidth, row.y(), cellWidth, cellHeight);
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

            if (photos.isEmpty()) {
                paintEmpty(g);
                return;
            }

            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            for (LayoutRow row : rows) {
                if (row.y() + row.height() < clip.y || row.y() > clip.y + clip.height) {
                    continue;
                }
                if (row.header()) {
                    paintHeader(g, row);
                } else {
                    for (int column = 0; column < row.indices().length; column++) {
                        paintCell(g, row.indices()[column], row, column);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    private void paintHeader(Graphics2D g, LayoutRow row) {
        int baseline = row.y() + 32;
        Font titleFont = Theme.font(Font.BOLD, 15);
        Font countFont = Theme.font(Font.PLAIN, 12);
        g.setFont(titleFont);
        g.setColor(Theme.TEXT);
        FontMetrics metrics = g.getFontMetrics();
        int x = Math.max(OUTER, gridLeft);
        g.drawString(row.title(), x, baseline);
        int titleWidth = metrics.stringWidth(row.title());
        g.setFont(countFont);
        g.setColor(Theme.MUTED);
        FontMetrics countMetrics = g.getFontMetrics();
        g.drawString(row.subtitle(), x + titleWidth + 10, baseline);
        int lineStart = x + titleWidth + 10 + countMetrics.stringWidth(row.subtitle()) + 14;
        int lineEnd = getWidth() - OUTER;
        if (lineEnd > lineStart) {
            g.setColor(Theme.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(lineStart, baseline - 4, lineEnd, baseline - 4);
        }
    }

    private void paintCell(Graphics2D g, int index, LayoutRow row, int column) {
        Photo photo = photos.get(index);
        Rectangle cell = cellBounds(row, column);
        int padding = 11;
        int cardX = cell.x + padding;
        int cardY = cell.y + padding;
        int cardSize = thumbSize;
        boolean selected = index == selectedIndex;
        boolean hovered = index == hoverIndex;

        // Card background and shadow.
        RoundRectangle2D card = new RoundRectangle2D.Float(cardX, cardY, cardSize, cardSize, 14, 14);
        if (selected || hovered) {
            g.setColor(new Color(0, 0, 0, 10));
            g.fill(new RoundRectangle2D.Float(cardX + 1, cardY + 3, cardSize, cardSize, 14, 14));
        }
        g.setColor(Theme.SURFACE);
        g.fill(card);

        Rectangle inner = new Rectangle(cardX + 3, cardY + 3, cardSize - 6, cardSize - 6);
        BufferedImage image = cache.get(photo.id(), photo.modifiedAt());
        if (image == null) {
            cache.request(photo.id(), photo.modifiedAt(), this::repaint);
            g.setColor(Theme.BACKGROUND);
            g.fill(inner);
            int iconSize = Math.min(38, cardSize / 3);
            Icons.image(iconSize, Theme.FAINT).paintIcon(this, g, inner.x + (inner.width - iconSize) / 2, inner.y + (inner.height - iconSize) / 2);
        } else {
            double scale = Math.min((double) inner.width / image.getWidth(), (double) inner.height / image.getHeight());
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = inner.x + (inner.width - width) / 2;
            int y = inner.y + (inner.height - height) / 2;
            g.drawImage(image, x, y, width, height, null);
        }

        if (selected) {
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(2.4f));
            g.draw(new RoundRectangle2D.Float(cardX + 1, cardY + 1, cardSize - 2, cardSize - 2, 13, 13));
            g.setColor(Theme.ACCENT);
            g.fill(new Ellipse2D.Float(cardX + 8, cardY + 8, 24, 24));
            Icons.check(16, Color.WHITE).paintIcon(this, g, cardX + 12, cardY + 12);
        } else if (hovered) {
            g.setColor(new Color(0x2E, 0x6B, 0xE6, 90));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Float(cardX + 1, cardY + 1, cardSize - 2, cardSize - 2, 13, 13));
        }

        if (photo.favorite()) {
            g.setColor(new Color(255, 255, 255, 225));
            g.fill(new Ellipse2D.Float(cardX + cardSize - 34, cardY + 8, 26, 26));
            Icons.star(17, Theme.GOLD, true).paintIcon(this, g, cardX + cardSize - 29, cardY + 13);
        }

        String label = photo.fileName();
        g.setFont(selected ? Theme.font(Font.BOLD, 12) : Theme.font(Font.PLAIN, 12));
        g.setColor(selected ? Theme.ACCENT_DARK : Theme.TEXT);
        FontMetrics metrics = g.getFontMetrics();
        String text = truncate(label, metrics, cardSize);
        int textX = cardX + (cardSize - metrics.stringWidth(text)) / 2;
        int textY = cardY + cardSize + 17;
        g.drawString(text, textX, textY);
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
