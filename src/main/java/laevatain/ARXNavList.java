/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * A Windows 11-style navigation list: icon rows with a rounded selection wash, an optional
 * trailing annotation, hover feedback, arrow-key navigation and an optional context menu.
 * <p>
 * Colours and fonts come from the {@code ARX.*} UI defaults rather than from a concrete theme
 * class, so any host application can drive the look of the list by publishing those keys.
 */
public final class ARXNavList extends JPanel {

    /** A title that shrinks with an ellipsis so the trailing annotation always stays in view. */
    private static final class TruncatedLabel extends JLabel {

        TruncatedLabel(String text) {
            super(text);
            setHorizontalAlignment(SwingConstants.LEFT);
            // Without this, a long label's minimum width pushes the annotation out of the row.
            setMinimumSize(new Dimension(0, 0));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            String text = getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            // A plain Graphics has no text hints; BasicLabelUI normally supplies them. Without
            // these, the truncated title renders noticeably thinner than the other labels.
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                FontMetrics metrics = g.getFontMetrics();
                String shown = text;
                if (metrics.stringWidth(shown) > getWidth()) {
                    while (shown.length() > 1 && metrics.stringWidth(shown + "…") > getWidth()) {
                        shown = shown.substring(0, shown.length() - 1);
                    }
                    shown += "…";
                }
                g.setColor(getForeground());
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(shown, 0, baseline);
            } finally {
                g.dispose();
            }
        }
    }

    /** One row of the list. Kept public so callers can annotate or extend it. */
    public static final class Row extends JPanel {

        private final String key;

        private final TruncatedLabel label;

        private final JLabel trailing = new JLabel();

        private boolean selected;

        private boolean hovered;

        Row(String key, String text, Icon icon) {
            this.key = key;
            // Opaque so a deselected row erases its own highlight; transparent rows left stale
            // accent paint behind when the selection moved quickly between rows.
            setOpaque(true);
            setBackground(navBackground());
            setLayout(new BorderLayout(9, 0));
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            if (icon != null) {
                add(new JLabel(icon), BorderLayout.WEST);
            }
            label = new TruncatedLabel(text);
            label.setFont(rowFont());
            label.setForeground(textColor());
            add(label, BorderLayout.CENTER);
            trailing.setFont(annotationFont());
            trailing.setForeground(mutedColor());
            trailing.setHorizontalAlignment(SwingConstants.RIGHT);
            add(trailing, BorderLayout.EAST);
        }

        /**
         * Rows keep their preferred height (with a 38px floor for the touch target) so BoxLayout
         * never stretches them apart, whatever annotations arrive later.
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, Math.max(38, getPreferredSize().height));
        }

        /** The identifier this row was registered under. */
        public String key() {
            return key;
        }

        /** Replaces the row's title. */
        public void setTitle(String text) {
            label.setText(text);
        }

        /** Sets the right-aligned annotation, typically a count. Null or empty hides it. */
        public void setTrailing(String text) {
            trailing.setText(text == null ? "" : text);
        }

        /**
         * Tints the row's title, used to flag destructive or active entries. Deliberately not
         * called setForeground: the panel UI installs that property during the superclass
         * constructor, before this row's label exists.
         */
        public void setTitleColor(Color color) {
            label.setForeground(color);
        }

        @Override
        public void setToolTipText(String text) {
            super.setToolTipText(text);
            label.setToolTipText(text);
        }

        void setSelectedRow(boolean value) {
            if (selected != value) {
                selected = value;
                repaint();
            }
        }

        void setHovered(boolean value) {
            if (hovered != value) {
                hovered = value;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (selected || hovered) {
                    // A rounded wash over the whole row bar a hairline: the Fluent selection style.
                    g.setColor(selected ? selectionColor() : hoverColor());
                    g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 9, 9));
                }
            } finally {
                g.dispose();
            }
        }
    }

    private final Map<String, Row> rows = new LinkedHashMap<>();

    private Consumer<String> onSelect = key -> {
    };

    private Function<Row, JPopupMenu> menu;

    private String selected;

    private JLabel section;

    private JPanel sectionHolder;

    public ARXNavList() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /**
     * BoxLayout derives a component's maximum from its own layout manager, which caps this list at
     * its preferred width and lets the parent centre it - the header and every row then drift to
     * the right. Ask for the full column width while keeping the height rigid, so the list is
     * stretched by its parent and never stretched apart by it.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** Appends a row and returns it, so counts and tooltips can be set afterwards. */
    public Row add(String key, String text, Icon icon) {
        Row row = new Row(key, text, icon);
        row.addMouseListener(new MouseAdapter() {
            // Selecting on the press, like a list does, is deliberate: the synthesised click event
            // is dropped whenever the pointer drifts a few pixels while pressing, which made rows
            // feel dead.
            @Override
            public void mousePressed(MouseEvent event) {
                maybeShowMenu(row, event);
                // Selecting on the press, like a list does, is deliberate: the synthesised click
                // event is dropped whenever the pointer drifts a few pixels while pressing, which
                // made rows feel dead. Taking the focus keeps the arrow keys alive afterwards.
                if (event.getButton() == MouseEvent.BUTTON1 && !event.isPopupTrigger()) {
                    row.requestFocusInWindow();
                    select(key, true);
                }
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                row.setHovered(true);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                row.setHovered(false);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                maybeShowMenu(row, event);
            }
        });
        row.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int code = event.getKeyCode();
                if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) {
                    event.consume();
                    moveSelection(row, code == KeyEvent.VK_UP ? -1 : 1);
                } else if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
                    event.consume();
                    select(key, true);
                }
            }
        });
        rows.put(key, row);
        refreshOrder();
        return row;
    }

    /** Removes every row. The section label, listeners and menu provider survive. */
    public void clear() {
        rows.clear();
        refreshOrder();
    }

    /** The row registered under the key, or null. */
    public Row row(String key) {
        return rows.get(key);
    }

    /** The keys in display order. */
    public List<String> keys() {
        return new ArrayList<>(rows.keySet());
    }

    /** Receives the key of every row the user selected with the pointer or the keyboard. */
    public void setOnSelect(Consumer<String> handler) {
        onSelect = handler == null ? key -> {
        } : handler;
    }

    /**
     * Installs the context menu. The provider builds the menu for the clicked row; null disables
     * the popup entirely.
     */
    public void setMenu(Function<Row, JPopupMenu> provider) {
        menu = provider;
    }

    /** Marks the row as selected without notifying the host, e.g. when the view changed elsewhere. */
    public void setSelected(String key) {
        select(key, false);
    }

    /** The selected key, or null when nothing is selected. */
    public String getSelected() {
        return selected;
    }

    /** Scrolls the list so the row is visible; does nothing when the list is not scrolled. */
    public void reveal(String key) {
        Row row = rows.get(key);
        if (row != null) {
            scrollRectToVisible(new Rectangle(row.getX(), row.getY(), row.getWidth(), row.getHeight()));
        }
    }

    /**
     * The muted heading above the rows, drawn in the same style as the list itself. Pass null to
     * hide it.
     */
    public void setSection(String text) {
        if (section == null) {
            sectionHolder = new JPanel(new BorderLayout());
            sectionHolder.setOpaque(false);
            sectionHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
            section = new JLabel();
            section.setFont(sectionFont());
            section.setForeground(mutedColor());
            section.setHorizontalAlignment(SwingConstants.LEFT);
            sectionHolder.add(section, BorderLayout.CENTER);
            add(sectionHolder, 0);
        }
        section.setText(text == null ? "" : text);
        sectionHolder.setVisible(text != null);
        // Pin the height only now that the text is known, and never leave it unpinned: an
        // unlimited maximum lets BoxLayout hand the header a share of the leftover rail height,
        // which stretches the rows far away from the label.
        Dimension box = sectionHolder.getPreferredSize();
        sectionHolder.setMinimumSize(box);
        sectionHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.height));
        revalidate();
    }

    /**
     * Wraps the list in a borderless scroll pane whose view always matches the viewport width, so a
     * long row label cannot push the trailing annotation out of sight.
     */
    public JScrollPane scrolled() {
        JPanel holder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width = 0;
                return size;
            }
        };
        holder.setOpaque(true);
        holder.setBackground(navBackground());
        holder.add(this, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(true);
        scroll.setBackground(navBackground());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(navBackground());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        return scroll;
    }

    private void select(String key, boolean notify) {
        selected = key;
        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            entry.getValue().setSelectedRow(entry.getKey().equals(key));
        }
        repaint();
        if (notify) {
            onSelect.accept(key);
        }
    }

    /** Arrow-key navigation: select the neighbour and hand it the focus. */
    private void moveSelection(Row origin, int delta) {
        List<String> keys = new ArrayList<>(rows.keySet());
        int index = keys.indexOf(origin.key());
        int next = index + delta;
        if (next < 0 || next >= keys.size()) {
            return;
        }
        String key = keys.get(next);
        // Select first, then move focus: requesting focus before the selection lets rapid key
        // repeats leave the focus one row behind the selection.
        select(key, true);
        Row row = rows.get(key);
        reveal(key);
        row.requestFocusInWindow();
    }

    /** isPopupTrigger is platform specific: only one of press and release reports it. */
    private void maybeShowMenu(Row row, MouseEvent event) {
        if (menu != null && event.isPopupTrigger()) {
            JPopupMenu popup = menu.apply(row);
            if (popup != null) {
                popup.show(row, event.getX(), event.getY());
            }
        }
    }

    /** Rebuilds the row order, keeping the section label on top. */
    private void refreshOrder() {
        removeAll();
        if (sectionHolder != null) {
            add(sectionHolder);
            add(Box.createVerticalStrut(6));
        }
        for (Row row : rows.values()) {
            add(row);
        }
        revalidate();
        repaint();
    }

    private static Color navBackground() {
        return color("ARX.navBackground", "PackageList.background", new Color(0xF1F3F6));
    }

    private static Color selectionColor() {
        return color("ARX.selection", "Button.background", new Color(46, 107, 230, 80));
    }

    private static Color hoverColor() {
        return color("ARX.hover", "List.selectionBackground", new Color(0xE7EAEF));
    }

    private static Color textColor() {
        return color("ARX.text", "Label.foreground", new Color(0x25282D));
    }

    private static Color mutedColor() {
        return color("ARX.textMuted", "Label.disabledForeground", new Color(0x7B818B));
    }

    private static Color color(String key, String fallbackKey, Color fallback) {
        Color value = UIManager.getColor(key);
        if (value == null) {
            value = UIManager.getColor(fallbackKey);
        }
        return value == null ? fallback : value;
    }

    private static Font rowFont() {
        return font("ARX.font.row", Font.PLAIN, 13);
    }

    private static Font annotationFont() {
        return font("ARX.font.annotation", Font.PLAIN, 11);
    }

    private static Font sectionFont() {
        return font("ARX.font.section", Font.BOLD, 11);
    }

    private static Font font(String key, int style, int size) {
        Font value = UIManager.getFont(key);
        return value == null ? new Font(UIManager.getFont("defaultFont") == null ? "SansSerif"
                : UIManager.getFont("defaultFont").getFamily(), style, size) : value;
    }

    /** A label used as a section heading inside a scroll pane; matches {@link #setSection}. */
    public static JComponent headingLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(sectionFont());
        label.setForeground(mutedColor());
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
