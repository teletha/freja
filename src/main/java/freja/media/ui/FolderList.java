/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;

import freja.Icons;
import freja.Theme;
import freja.media.Folder;
import kiss.I;

import laevatain.ARXScrollPane;
import laevatain.api.ComponentHelper;
import laevatain.ARXLabel;

/**
 * The folder rail of the library, built on {@link JList} so only the rows inside the viewport are
 * painted. A library which discovers ten thousand directories builds no components for the rows
 * which are scrolled away: the model keeps plain {@link Folder} records and the renderer is one
 * shared cell.
 * <p>
 * The visuals mirror {@link laevatain.ARXNavList}: a rounded selection wash, hover feedback, an
 * icon, an ellipsised title and a right-aligned count.
 * </p>
 */
public final class FolderList extends JList<Folder> implements ComponentHelper<FolderList> {
    /** The height of a row, matching the ARX navigation list's touch target. */
    private static final int ROW_HEIGHT = 38;
    /**
     * A dummy fixed cell width. {@link javax.swing.plaf.basic.BasicListUI} measures every row with
     * the renderer whenever either fixed dimension is unset, which would walk ten thousand folders
     * on each refresh. The real width is the viewport's, because
     * {@link #getScrollableTracksViewportWidth()} returns true.
     */
    private static final int ROW_WIDTH = 1;
    private final Model model = new Model();
    private final Icon folderIcon = Icons.nav("folder", Theme.MUTED);
    private final Cell cell = new Cell();
    private Consumer<Folder> onSelect = folder -> {
    };
    private Consumer<List<Folder>> onRemove = folders -> {
    };
    private Function<Folder, JPopupMenu> menu;
    private int hovered = -1;
    /** The row a Shift-click or drag extends from. */
    private int selectionAnchor = -1;
    /** True while the selection is changed by code, so no navigation event is reported back. */
    private boolean syncing;
    /** Guard against re-installing the interaction handlers from {@link #updateUI()} too early. */
    private boolean installed;
    public FolderList() {
        super();
        setModel(model);
        setCellRenderer(cell);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        setFixedCellHeight(ROW_HEIGHT);
        setFixedCellWidth(ROW_WIDTH);
        background(Theme.SIDEBAR).foreground(Theme.TEXT).opaque(true).focusable(true)
                .border(BorderFactory.createEmptyBorder());
        installInteraction();
        installed = true;
        // An empty tooltip registers the list with the tooltip manager; the per-row text arrives
        // from getToolTipText(MouseEvent) below.
        tooltip("");
        onSelection(event -> {
            if (syncing || event.getValueIsAdjusting()) {
                return;
            }
            Folder folder = getSelectedValue();
            if (folder != null) {
                onSelect.accept(folder);
            }
        });
        for (KeyStroke stroke : new KeyStroke[] {KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)}) {
            getInputMap().put(stroke, "selectFolder");
        }
        getActionMap().put("selectFolder", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Folder folder = getSelectedValue();
                if (folder != null) {
                    onSelect.accept(folder);
                }
            }
        });
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeFolders");
        getActionMap().put("removeFolders", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                List<Folder> targets = selectedFolders();
                if (!targets.isEmpty()) {
                    onRemove.accept(targets);
                }
            }
        });
    }
    /**
     * Reinstalls the look and the pointer handling after a look and feel change. {@link JList}
     * installs its own mouse handler on every {@code updateUI}; leaving it in place would select a
     * row twice and report the same click to the host twice.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (!installed) {
            return;
        }
        setCellRenderer(cell);
        setFixedCellHeight(ROW_HEIGHT);
        setFixedCellWidth(ROW_WIDTH);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        background(Theme.SIDEBAR).foreground(Theme.TEXT);
        installInteraction();
    }
    /** Replaces Swing's mouse handler with one which reports every press exactly once. */
    private void installInteraction() {
        ToolTipManager tooltips = ToolTipManager.sharedInstance();
        for (java.awt.event.MouseListener listener : getMouseListeners()) {
            if (listener != tooltips) {
                removeMouseListener(listener);
            }
        }
        for (java.awt.event.MouseMotionListener listener : getMouseMotionListeners()) {
            // The tooltip manager is also a motion listener and must keep following the pointer.
            if (listener != tooltips) {
                removeMouseMotionListener(listener);
            }
        }
        // One adapter handles both listener roles: mouseDragged belongs to MouseMotionListener,
        // so registering this object twice is what actually makes drag selection work.
        MouseAdapter interaction = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (maybeShowMenu(event) || event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int index = indexAt(event.getPoint());
                if (index >= 0) {
                    requestFocusInWindow();
                    if (event.isShiftDown()) {
                        // Keep the previous anchor: Shift extends from the last plain click.
                        selectIntervalQuietly(selectionAnchor >= 0 ? selectionAnchor : index, index);
                    } else {
                        selectionAnchor = index;
                        if (event.isControlDown() || event.isMetaDown()) {
                            toggleQuietly(index);
                        } else {
                            selectQuietly(index);
                        }
                    }
                    // A modifier click builds the selection; only a plain click navigates. Calling
                    // onSelect here would refresh the view and collapse the multi-selection again.
                    if (!event.isShiftDown() && !event.isControlDown() && !event.isMetaDown()) {
                        // Report on the press, like ARXNavList: a synthesised click is dropped when
                        // the pointer drifts, which made rows feel dead.
                        onSelect.accept(model.get(index));
                    }
                }
            }
            @Override
            public void mouseDragged(MouseEvent event) {
                int index = indexAt(event.getPoint());
                if (index >= 0 && selectionAnchor >= 0 && !event.isControlDown() && !event.isMetaDown()) {
                    selectIntervalQuietly(selectionAnchor, index);
                }
            }
            @Override
            public void mouseReleased(MouseEvent event) {
                maybeShowMenu(event);
            }
            @Override
            public void mouseExited(MouseEvent event) {
                hover(-1);
            }
            @Override
            public void mouseMoved(MouseEvent event) {
                hover(indexAt(event.getPoint()));
            }
        };
        addMouseListener(interaction);
        addMouseMotionListener(interaction);
    }
    /**
     * Replaces the folder rows. Rows which did not change are left alone. Selection follows folder
     * identities: a removed folder cannot fall through to the row which took its place.
     */
    public void setFolders(List<Folder> folders) {
        List<Long> selectedIds = new ArrayList<>();
        for (int index : getSelectedIndices()) {
            selectedIds.add(model.get(index).id());
        }
        boolean previous = syncing;
        syncing = true;
        try {
            if (hovered >= 0) {
                hover(-1);
            }
            model.setFolders(folders);
            restoreSelection(selectedIds);
        } finally {
            syncing = previous;
        }
    }
    /** Keeps only the selected folders which still exist, without inheriting a neighbour's row. */
    private void restoreSelection(List<Long> ids) {
        int[] current = getSelectedIndices();
        boolean unchanged = current.length == ids.size();
        if (unchanged) {
            for (int i = 0; i < ids.size(); i++) {
                if (model.get(current[i]).id() != ids.get(i)) {
                    unchanged = false;
                    break;
                }
            }
        }
        if (unchanged) {
            return;
        }
        clearSelection();
        for (long id : ids) {
            int index = model.indexOf(id);
            if (index >= 0) {
                addSelectionInterval(index, index);
            }
        }
        selectionAnchor = getSelectedIndex();
    }
    /** Receives every folder the user selected with the pointer or the keyboard. */
    public void setOnSelect(Consumer<Folder> handler) {
        onSelect = handler == null ? folder -> {
        } : handler;
    }
    /** Receives all folders selected when Delete is pressed. */
    public void setOnRemove(Consumer<List<Folder>> handler) {
        onRemove = handler == null ? folders -> {
        } : handler;
    }
    /** The selected folders, in list order. */
    public List<Folder> selectedFolders() {
        List<Folder> selected = new ArrayList<>();
        for (int index : getSelectedIndices()) {
            selected.add(model.get(index));
        }
        return List.copyOf(selected);
    }
    /**
     * Installs the context menu. The provider builds the menu for the clicked folder; null disables
     * the popup entirely.
     */
    public void setMenu(Function<Folder, JPopupMenu> provider) {
        menu = provider;
    }
    /**
     * Marks a folder as selected without notifying the host. A negative id clears the selection.
     * A multi-selection is kept when the requested folder is one of its rows: refreshing the view
     * while several folders are selected must not silently reduce them to one.
     */
    public void setSelectedId(long id) {
        if (id >= 0 && getSelectedIndices().length > 1) {
            for (int index : getSelectedIndices()) {
                if (model.get(index).id() == id) {
                    return;
                }
            }
        }
        selectQuietly(model.indexOf(id));
    }
    /** Scrolls the list so the folder is visible; does nothing when it is not in the model. */
    public void reveal(long id) {
        int index = model.indexOf(id);
        if (index >= 0) {
            ensureIndexIsVisible(index);
        }
    }
    /**
     * Wraps the list in a borderless scroll pane whose rows span the viewport width, so a long
     * folder name cannot push the count out of sight.
     */
    public JScrollPane scrolled() {
        ARXScrollPane scroll = new ARXScrollPane(this).background(Theme.SIDEBAR);
        // The rail keeps wheel scrolling (the scroll pane forwards wheel events to its viewport even
        // without a visible bar) but never shows a vertical thumb: the rows own the whole width.
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        // The wheel belongs to the scroll pane: a wheel listener on the list itself would stop
        // AWT from forwarding the event to its ancestor, which is what made the rail ignore the
        // wheel while its scroll bar still worked. Clearing the hover here keeps the highlight
        // from sticking to a row which scrolled away under a stationary pointer.
        scroll.getViewport().addChangeListener(event -> hover(-1));
        return scroll;
    }
    /** The row runs the full width of the viewport rather than the widest name. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }
    @Override
    public String getToolTipText(MouseEvent event) {
        if (event == null) {
            return null;
        }
        int index = indexAt(event.getPoint());
        return index < 0 ? null : tooltip(model.get(index));
    }
    private static String tooltip(Folder folder) {
        StringBuilder text = new StringBuilder(folder.path().toString());
        if (!folder.explicit()) {
            text.append("  ").append(I.translate("(auto-detected)").get());
        }
        if (!folder.tags().isEmpty()) {
            text.append('\n').append(I.translate("Tags").get()).append(": ").append(String.join(", ", folder.tags()));
        }
        return text.toString();
    }
    /** A click on the row, rather than the payload of the list's own selection event. */
    private boolean maybeShowMenu(MouseEvent event) {
        if (menu == null || !event.isPopupTrigger()) {
            return false;
        }
        int index = indexAt(event.getPoint());
        if (index < 0) {
            return false;
        }
        JPopupMenu popup = menu.apply(model.get(index));
        if (popup != null) {
            popup.show(this, event.getX(), event.getY());
        }
        return true;
    }
    /** The row under the point, or -1 when the point is in the empty area below the rows. */
    private int indexAt(Point point) {
        int index = locationToIndex(point);
        if (index < 0) {
            return -1;
        }
        Rectangle bounds = getCellBounds(index, index);
        return bounds != null && bounds.contains(point) ? index : -1;
    }
    private void selectQuietly(int index) {
        quietly(() -> {
            if (index < 0) {
                clearSelection();
            } else {
                setSelectedIndex(index);
            }
        });
    }
    private void selectIntervalQuietly(int from, int to) {
        quietly(() -> setSelectionInterval(Math.min(from, to), Math.max(from, to)));
    }
    private void toggleQuietly(int index) {
        quietly(() -> {
            if (isSelectedIndex(index)) {
                removeSelectionInterval(index, index);
            } else {
                addSelectionInterval(index, index);
            }
        });
    }
    private void quietly(Runnable change) {
        boolean previous = syncing;
        syncing = true;
        try {
            change.run();
        } finally {
            syncing = previous;
        }
    }
    private void hover(int index) {
        if (index == hovered) {
            return;
        }
        int previous = hovered;
        hovered = index;
        repaintRow(previous);
        repaintRow(index);
    }
    private void repaintRow(int index) {
        if (index < 0) {
            return;
        }
        Rectangle bounds = getCellBounds(index, index);
        if (bounds != null) {
            repaint(bounds);
        }
    }
    /** The row cells are painted at the viewport width, so the count is never clipped. */
    private final class Cell extends JPanel implements ListCellRenderer<Folder> {
        private final JLabel iconLabel = new ARXLabel();
        private final JLabel title = new ARXLabel();
        private final JLabel trailing = new ARXLabel();
        private boolean selectedCell;
        private boolean hoverCell;
        Cell() {
            setOpaque(true);
            setLayout(new BorderLayout(9, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            add(iconLabel, BorderLayout.WEST);
            title.setFont(Theme.font(Font.PLAIN, 13));
            title.setForeground(Theme.TEXT);
            // Without this a long name's minimum width pushes the count out of the row.
            title.setMinimumSize(new Dimension(0, 0));
            add(title, BorderLayout.CENTER);
            trailing.setFont(Theme.font(Font.PLAIN, 11));
            trailing.setForeground(Theme.MUTED);
            trailing.setHorizontalAlignment(SwingConstants.RIGHT);
            add(trailing, BorderLayout.EAST);
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends Folder> list, Folder value, int index,
                boolean isSelected, boolean cellHasFocus) {
            iconLabel.setIcon(folderIcon);
            title.setText(value.name());
            trailing.setText(Integer.toString(value.photoCount()));
            selectedCell = isSelected;
            hoverCell = index == hovered;
            setBackground(Theme.SIDEBAR);
            return this;
        }
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!selectedCell && !hoverCell) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // The Fluent selection wash: a rounded tint which leaves a hairline at the edges.
                g.setColor(selectedCell ? Theme.SELECTION : Theme.HOVER);
                g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 9, 9));
            } finally {
                g.dispose();
            }
        }
    }
    /**
     * Holds the folders behind the rows. A refresh with the same ids in the same order only
     * reports the cells whose folder actually changed, so a scan which bumps one count does not
     * rebuild the list - and does not swallow a click which is in flight.
     */
    private static final class Model extends AbstractListModel<Folder> {
        private final List<Folder> items = new ArrayList<>();
        private final Map<Long, Integer> index = new HashMap<>();
        @Override
        public int getSize() {
            return items.size();
        }
        @Override
        public Folder getElementAt(int position) {
            return items.get(position);
        }
        Folder get(int position) {
            return items.get(position);
        }
        int indexOf(long id) {
            Integer position = index.get(id);
            return position == null ? -1 : position;
        }
        void setFolders(List<Folder> next) {
            if (sameOrder(next)) {
                int first = -1;
                int last = -1;
                for (int position = 0; position < next.size(); position++) {
                    if (!items.get(position).equals(next.get(position))) {
                        items.set(position, next.get(position));
                        if (first < 0) {
                            first = position;
                        }
                        last = position;
                    }
                }
                if (first >= 0) {
                    fireContentsChanged(this, first, last);
                }
                return;
            }
            int previousSize = items.size();
            items.clear();
            index.clear();
            items.addAll(next);
            for (int position = 0; position < items.size(); position++) {
                index.put(items.get(position).id(), position);
            }
            if (previousSize > 0) {
                fireIntervalRemoved(this, 0, previousSize - 1);
            }
            if (!items.isEmpty()) {
                fireIntervalAdded(this, 0, items.size() - 1);
            }
        }
        /** Whether the list already holds exactly these folders in this order. */
        private boolean sameOrder(List<Folder> next) {
            if (items.size() != next.size()) {
                return false;
            }
            for (int position = 0; position < next.size(); position++) {
                if (items.get(position).id() != next.get(position).id()) {
                    return false;
                }
            }
            return true;
        }
    }
}