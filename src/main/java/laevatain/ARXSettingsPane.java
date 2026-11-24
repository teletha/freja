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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import kiss.Variable;

import com.formdev.flatlaf.ui.FlatLineBorder;

/**
 * A Windows 11-style settings surface: an {@link ARXNavList} rail on the left and one continuous,
 * scrollable document on the right. Every section registers a rail entry; choosing an entry glides
 * the viewport to that section, and scrolling keeps the rail selection in sync.
 * <p>
 * The building blocks the pages are made of - rounded {@link #card cards} of
 * {@link #row label-and-control rows} separated by {@link #divider hairlines} - are exposed as
 * statics so any host can compose its own pages. Colours and fonts are read from the
 * {@code ARX.*} UI defaults, keeping the component independent of a concrete theme class.
 */
public final class ARXSettingsPane extends JPanel {

    /** One anchored page of the document. */
    public static final class Section extends JPanel {

        private final String id;

        private final JLabel heading = new JLabel();

        private final JLabel hint = new JLabel();

        private int slot;

        private Section(String id, String title, String subtitle) {
            this(id, Variable.of(title), Variable.of(subtitle));
        }

        /**
         * A section whose heading and description follow variables: hand in
         * {@code Lang.text("...")} and the heading, the description and the rail entry change with
         * the interface language.
         */
        private Section(String id, Variable<String> title, Variable<String> subtitle) {
            this.id = id;
            setName(id);
            setOpaque(false);
            setLayout(new GridBagLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            heading.setFont(titleFont());
            heading.setForeground(textColor());
            hint.setFont(subtitleFont());
            hint.setForeground(mutedColor());
            title.observing().to(heading::setText);
            subtitle.observing().to(hint::setText);
            add(header(heading, hint), anchor(0, GridBagConstraints.HORIZONTAL));
        }

        /** Appends a component which brings its own chrome, e.g. a pair of preview tiles. */
        public void addContent(Component component) {
            slot++;
            add(component, anchor(slot, GridBagConstraints.HORIZONTAL));
        }

        /** Appends a rounded card built from the given rows. */
        public void addCard(Component rows) {
            slot++;
            add(card(rows), anchor(slot, GridBagConstraints.HORIZONTAL));
        }

        /** Appends a heading which groups the cards below it. */
        public void addGroup(Variable<String> title) {
            slot++;
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            JLabel label = new JLabel();
            label.setFont(groupFont());
            label.setForeground(textColor());
            title.observing().to(label::setText);
            holder.add(label, BorderLayout.CENTER);
            add(holder, anchor(slot, GridBagConstraints.HORIZONTAL));
        }

        /** Appends a heading which groups the cards below it, e.g. "Other options". */
        public void addGroup(String title) {
            addGroup(Variable.of(title));
        }

        /** The identifier this section registered under. */
        public String id() {
            return id;
        }

        /**
         * Keep the section at its preferred height no matter how tall the column gets: a pinned
         * maximum would go stale as soon as cards are added, and an unlimited one lets BoxLayout
         * stretch the section, which centres its content and opens gaps between sections.
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private GridBagConstraints anchor(int row, int fill) {
            GridBagConstraints cell = new GridBagConstraints();
            cell.gridx = 0;
            cell.gridy = row;
            cell.weightx = 1.0;
            cell.fill = fill;
            cell.anchor = GridBagConstraints.WEST;
            if (row > 0) {
                cell.insets = new Insets(18, 0, 0, 0);
            }
            return cell;
        }

        /** Packs the two heading labels into the grid block the section puts on top. */
        private static JComponent header(JLabel heading, JLabel hint) {
            JPanel block = new JPanel(new GridBagLayout());
            block.setOpaque(false);
            GridBagConstraints west = new GridBagConstraints();
            west.gridx = 0;
            west.gridy = 0;
            west.weightx = 1.0;
            west.anchor = GridBagConstraints.WEST;
            block.add(heading, west);
            west.gridy = 1;
            west.insets = new Insets(4, 0, 0, 0);
            block.add(hint, west);
            return block;
        }
    }

    private static final int DEFAULT_RAIL_WIDTH = 246;

    private static final int MINIMUM_COLUMN = 360;

    private static final int MAXIMUM_COLUMN = 720;

    private final ARXNavList rail = new ARXNavList();

    private final Map<String, Section> sections = new LinkedHashMap<>();

    private final JPanel document = new JPanel();

    private final JPanel column;

    private final JScrollPane scroll;

    private Timer glide;

    private int target;

    private boolean animating;

    public ARXSettingsPane() {
        this("設定", DEFAULT_RAIL_WIDTH);
    }

    public ARXSettingsPane(String railTitle) {
        this(Variable.of(railTitle), DEFAULT_RAIL_WIDTH);
    }

    /** A rail whose heading follows a variable, e.g. a translated sentence. */
    public ARXSettingsPane(Variable<String> railTitle) {
        this(railTitle, DEFAULT_RAIL_WIDTH);
    }

    public ARXSettingsPane(String railTitle, int railWidth) {
        this(Variable.of(railTitle), railWidth);
    }

    private ARXSettingsPane(Variable<String> railTitle, int railWidth) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(pageColor());

        rail.setSection(railTitle.get());
        railTitle.observing().to(rail::setSection);
        rail.setOnSelect(this::show);
        JPanel railPane = new JPanel(new BorderLayout());
        railPane.setOpaque(true);
        railPane.setBackground(navColor());
        railPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, hairlineColor()),
                BorderFactory.createEmptyBorder(20, 16, 16, 16)));
        railPane.setMinimumSize(new Dimension(railWidth, 0));
        railPane.setPreferredSize(new Dimension(railWidth, 0));
        railPane.setMaximumSize(new Dimension(railWidth, Integer.MAX_VALUE));
        railPane.add(rail, BorderLayout.NORTH);
        add(railPane, BorderLayout.WEST);

        document.setOpaque(false);
        document.setLayout(new BoxLayout(document, BoxLayout.Y_AXIS));
        document.setAlignmentX(Component.LEFT_ALIGNMENT);
        document.setMinimumSize(new Dimension(MINIMUM_COLUMN, 0));
        document.setMaximumSize(new Dimension(MAXIMUM_COLUMN, Integer.MAX_VALUE));

        column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.X_AXIS));
        column.add(document);
        column.add(Box.createHorizontalGlue());

        scroll = new TransparentScrollPane(column);
        scroll.getVerticalScrollBar().setUnitIncrement(32);
        scroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (!animating) {
                trackVisible(event.getValue() + 60);
            }
        });
        glide = new Timer(16, event -> glideScrollBar());

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(26, 30, 20, 30));
        content.add(scroll, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
    }

    /**
     * Registers a page: a rail entry, an anchored section heading and the scroll tracking between
     * the two. Fill the returned section with {@link Section#addCard} and {@link
     * Section#addGroup}.
     */
    public Section addSection(String id, String title, String subtitle, Icon icon) {
        return addSection(id, Variable.of(title), Variable.of(subtitle), icon);
    }

    /**
     * Registers a page whose heading follows a variable. The rail entry keeps the same text, so a
     * translated heading and its navigation label never drift apart.
     */
    public Section addSection(String id, Variable<String> title, Variable<String> subtitle, Icon icon) {
        if (!sections.isEmpty()) {
            document.add(Box.createVerticalStrut(36));
        }
        Section section = new Section(id, title, subtitle);
        sections.put(id, section);
        document.add(section);
        ARXNavList.Row entry = rail.add(id, title.get(), icon);
        title.observing().to(text -> {
            if (entry != null) {
                entry.setTitle(text);
            }
        });
        return section;
    }

    /** Selects the rail entry and glides the document to that section. */
    public void show(String id) {
        rail.setSelected(id);
        Section section = sections.get(id);
        if (section == null) {
            return;
        }
        JScrollBar bar = scroll.getVerticalScrollBar();
        int limit = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
        target = Math.min(sectionTop(section), limit);
        if (bar.getValue() == target) {
            animating = false;
            return;
        }
        animating = true;
        glide.restart();
    }

    /** The section currently nearest the top of the viewport. */
    public String getSelected() {
        return rail.getSelected();
    }

    /** Moves the bar a fraction closer to the target, always landing on it without overshoot. */
    private void glideScrollBar() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        int current = bar.getValue();
        int remaining = target - current;
        if (Math.abs(remaining) <= 2) {
            bar.setValue(target);
            animating = false;
            glide.stop();
            return;
        }
        int step = (int) Math.copySign(Math.max(8, Math.abs(remaining) * 0.3), remaining);
        // Clamping the step to the target is essential: a fixed minimum step would otherwise
        // oscillate around the destination forever and keep the animation flag stuck on.
        int next = step > 0 ? Math.min(current + step, target) : Math.max(current + step, target);
        bar.setValue(next);
        if (bar.getValue() == current) {
            // The layout has not settled yet and the bar cannot move; stop instead of spinning.
            animating = false;
            glide.stop();
        }
    }

    /** Highlights the rail entry which owns the section nearest the top of the viewport. */
    private void trackVisible(int position) {
        String current = null;
        for (Map.Entry<String, Section> entry : sections.entrySet()) {
            if (sectionTop(entry.getValue()) <= position) {
                current = entry.getKey();
            } else {
                break;
            }
        }
        if (current != null) {
            rail.setSelected(current);
        }
    }

    private int sectionTop(Section section) {
        return SwingUtilities.convertPoint(section, 0, 0, column).y;
    }

    /** Wraps rows in a rounded, outlined card. */
    public static JComponent card(Component rows) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(surfaceColor());
        card.setBorder(new FlatLineBorder(new Insets(1, 1, 1, 1), hairlineColor(), 1, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(rows, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows.getPreferredSize().height + 2));
        return card;
    }

    /** One settings row: label and description on the left, control vertically centred right. */
    public static JPanel row(String title, String description, Component control) {
        return row(Variable.of(title), Variable.of(description), control);
    }

    /** A row whose caption is a translated sentence and whose description is fixed. */
    public static JPanel row(Variable<String> title, String description, Component control) {
        return row(title, Variable.of(description), control);
    }

    /** A row whose description is a translated sentence and whose caption is fixed. */
    public static JPanel row(String title, Variable<String> description, Component control) {
        return row(Variable.of(title), description, control);
    }

    /**
     * A row whose label and description follow variables, so a translated sentence keeps the row
     * caption, its description and the rail entry in one language.
     */
    public static JPanel row(Variable<String> title, Variable<String> description, Component control) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel();
        name.setFont(labelFont());
        name.setForeground(textColor());
        name.setHorizontalAlignment(SwingConstants.LEFT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = new JLabel();
        hint.setFont(hintFont());
        hint.setForeground(mutedColor());
        hint.setHorizontalAlignment(SwingConstants.LEFT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.observing().to(name::setText);
        // A row without a description stays on one line: visibility follows the text.
        description.observing().to(value -> {
            hint.setText(value == null ? "" : value);
            boolean empty = value == null || value.isEmpty();
            hint.setVisible(!empty);
            if (hint.isShowing()) {
                row.revalidate();
                row.repaint();
            }
        });
        text.add(name);
        text.add(hint);
        row.add(text, BorderLayout.CENTER);
        // Anchoring the control EAST keeps it flush with the right edge while the extra cell
        // height centres it against the two-line label instead of letting it sit on top.
        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        GridBagConstraints anchor = new GridBagConstraints();
        anchor.gridx = 0;
        anchor.gridy = 0;
        anchor.weightx = 1.0;
        anchor.weighty = 1.0;
        anchor.anchor = GridBagConstraints.EAST;
        east.add(control, anchor);
        row.add(east, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** Stacks rows with hairlines between them, the usual body of a card. */
    public static JPanel rows(Component... entries) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.weightx = 1.0;
        cell.fill = GridBagConstraints.HORIZONTAL;
        for (int index = 0; index < entries.length; index++) {
            cell.gridy = index;
            panel.add(entries[index], cell);
        }
        return panel;
    }

    /** A hairline between rows, inset from the card edges. */
    public static JPanel divider() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(hairlineColor());
        line.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    /** A muted, right-aligned value label used in information rows. */
    public static JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(valueFont());
        label.setForeground(mutedColor());
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    /** A scroll pane which keeps its borderless, transparent chrome across UI updates. */
    private static final class TransparentScrollPane extends JScrollPane {

        TransparentScrollPane(Component view) {
            super(view);
            flatten();
        }

        @Override
        public void updateUI() {
            // Changing the theme reinstalls every UI, and the look and feel hands this scroll pane
            // its default hairline border and an opaque viewport back. Restore the invisible chrome
            // so the document keeps floating on the page background.
            super.updateUI();
            flatten();
        }

        private void flatten() {
            setBorder(null);
            setOpaque(false);
            setBackground(pageColor());
            getViewport().setOpaque(false);
            getViewport().setBackground(pageColor());
        }
    }

    private static Color pageColor() {
        return color("ARX.background", new Color(0xF7F8FA));
    }

    private static Color navColor() {
        return color("ARX.navBackground", new Color(0xF1F3F6));
    }

    private static Color surfaceColor() {
        return color("ARX.surface", Color.WHITE);
    }

    private static Color hairlineColor() {
        return color("ARX.hairline", new Color(0xE2E5EA));
    }

    private static Color textColor() {
        return color("ARX.text", new Color(0x25282D));
    }

    private static Color mutedColor() {
        return color("ARX.textMuted", new Color(0x7B818B));
    }

    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    private static Font titleFont() {
        return font("ARX.font.title", Font.BOLD, 19);
    }

    private static Font groupFont() {
        return font("ARX.font.group", Font.BOLD, 13);
    }

    private static Font subtitleFont() {
        return font("ARX.font.subtitle", Font.PLAIN, 12);
    }

    private static Font labelFont() {
        return font("ARX.font.label", Font.PLAIN, 13);
    }

    private static Font hintFont() {
        return font("ARX.font.hint", Font.PLAIN, 11);
    }

    private static Font valueFont() {
        return font("ARX.font.value", Font.PLAIN, 12);
    }

    private static Font font(String key, int style, int size) {
        Font value = UIManager.getFont(key);
        if (value != null) {
            return value;
        }
        Font base = UIManager.getFont("defaultFont");
        String family = base == null ? Font.SANS_SERIF : base.getFamily();
        return new Font(family, style, size);
    }
}
