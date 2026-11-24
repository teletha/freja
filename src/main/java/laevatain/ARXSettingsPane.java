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
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.ui.FlatLineBorder;

import kiss.I;
import kiss.Variable;

/**
 * A Windows 11-style settings surface: an {@link ARXNavList} rail on the left and one continuous,
 * scrollable document on the right. Every section registers a rail entry; choosing an entry glides
 * the viewport to that section, scrolling keeps the rail in sync, and the search field in its own
 * pane above the document narrows the whole page to the rows which answer to the query.
 * <p>
 * A page is built from {@link Row rows} inside rounded {@link Card cards}, and rows carry the words
 * they answer to: their caption, their description, the English origin of both and whatever
 * {@link Row#keywords extra terms} the host registers. That way "フォント", "font" and "typeface"
 * all find the same setting, whatever language the page is currently shown in.
 */
public final class ARXSettingsPane extends JPanel {

    /**
     * A caption with its English origin kept for search. The displayed wording follows a live
     * translation, while the origin stays stable so a query finds a row in every language.
     */
    public record Text(String source, Variable<String> value) {

        /** The wording currently shown, falling back to the origin while it is being fetched. */
        public String display() {
            String resolved = value == null ? null : value.get();
            return resolved == null || resolved.isBlank() ? source : resolved;
        }

        /** Creates a caption whose wording follows the active interface language. */
        public static Text of(String source) {
            return new Text(source, I.translate(source));
        }
    }

    /** One searchable settings row. */
    public static final class Row extends JPanel {

        private final ARXWrapLabel caption = new ARXWrapLabel();

        private final ARXWrapLabel description = new ARXWrapLabel();

        /** The phrases this row answers to: captions, their origins and the registered keywords. */
        private final Set<String> terms = new LinkedHashSet<>();

        private Row(Variable<String> title, Variable<String> hint, Component control) {
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
            setLayout(new BorderLayout(16, 0));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            caption.setFont(labelFont());
            caption.setForeground(textColor());
            description.setFont(hintFont());
            description.setForeground(mutedColor());
            text.add(caption);
            text.add(description);

            if (title != null) {
                title.observing().to(value -> {
                    caption.setSource(value);
                    remember(value);
                });
            }
            if (hint != null) {
                hint.observing().to(value -> {
                    description.setSource(value);
                    // A row without a description stays on one line.
                    description.setVisible(value != null && !value.isEmpty());
                    remember(value);
                });
            } else {
                description.setVisible(false);
            }
            add(text, BorderLayout.CENTER);
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
            add(east, BorderLayout.EAST);
        }

        /**
         * Registers terms this row should answer to which appear in no caption: synonyms, old
         * names, or the words a user is most likely to type.
         */
        public Row keywords(String... words) {
            for (String word : words) {
                remember(word);
            }
            return this;
        }

        /** The caption currently shown. */
        public String caption() {
            return caption.source();
        }

        /** Whether this row answers to the query; an empty query matches everything. */
        public boolean matches(String needle) {
            if (needle.isEmpty()) {
                return true;
            }
            String query = needle.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (term != null && term.toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            return false;
        }

        private void remember(String value) {
            if (value != null && !value.isBlank()) {
                terms.add(value);
            }
        }
    }

    /** A rounded card which re-lays itself around the rows a query leaves standing. */
    public static final class Card extends JPanel {

        private final List<Row> rows = new ArrayList<>();

        /**
         * The hairlines between the rows, created once and reused: the rebuild below compares the
         * card's children by identity, and a freshly made divider would never match.
         */
        private final List<JPanel> hairlines = new ArrayList<>();

        private boolean shown = true;

        private Card(List<Row> entries) {
            rows.addAll(entries);
            for (int index = 1; index < entries.size(); index++) {
                hairlines.add(divider());
            }
            setOpaque(true);
            setBackground(surfaceColor());
            setBorder(new FlatLineBorder(new Insets(1, 1, 1, 1), hairlineColor(), 1, 8));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            layout(true);
        }

        /** Rebuilds the card from the rows which are still in the query, hairlines between them. */
        private void layout(boolean all) {
            List<Component> wanted = new ArrayList<>();
            int used = 0;
            for (Row row : rows) {
                if (!all && !row.isVisible()) {
                    continue;
                }
                if (!wanted.isEmpty() && used < hairlines.size()) {
                    wanted.add(hairlines.get(used++));
                }
                wanted.add(row);
            }
            // Rebuilding a card means removing and re-adding components, which drags the focus
            // along with it. Only do it when the content actually changed.
            if (same(wanted)) {
                shown = !wanted.isEmpty();
                setVisible(shown);
                return;
            }
            removeAll();
            for (Component component : wanted) {
                add(component);
            }
            shown = !wanted.isEmpty();
            setVisible(shown);
            revalidate();
            repaint();
        }

        /** Whether the card already holds exactly these components, dividers included. */
        private boolean same(List<Component> wanted) {
            Component[] current = getComponents();
            if (current.length != wanted.size()) {
                return false;
            }
            for (int index = 0; index < current.length; index++) {
                if (current[index] != wanted.get(index)) {
                    return false;
                }
            }
            return true;
        }

        /** Applies a query to the rows and reports whether the card still shows anything. */
        public boolean filter(String needle) {
            for (Row row : rows) {
                row.setVisible(needle.isEmpty() || row.matches(needle));
            }
            layout(needle.isEmpty());
            return shown;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** A block of a section which is not a card: a tile grid, a chart, anything with keywords. */
    public static final class Block extends JPanel {

        private final Set<String> terms = new LinkedHashSet<>();

        private Block(Component component, List<String> keywords, String origin) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(component, BorderLayout.CENTER);
            remember(origin);
            keywords.forEach(this::remember);
        }

        /** Registers extra words this block should answer to. */
        public Block keywords(String... words) {
            for (String word : words) {
                remember(word);
            }
            return this;
        }

        private void remember(String value) {
            if (value != null && !value.isBlank()) {
                terms.add(value);
            }
        }

        /** Whether the query touches this block. */
        public boolean matches(String needle) {
            if (needle.isEmpty()) {
                return true;
            }
            String query = needle.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (term.toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            // GridBagLayout shrinks rows below their preferred height when the enclosing document
            // is short; a block must keep its full height or its contents get crushed.
            return new Dimension(0, getPreferredSize().height);
        }
    }

    /** One anchored page of the document. */
    public static final class Section extends JPanel {

        private final String id;

        private final JLabel heading = new JLabel();

        private final JLabel hint = new JLabel();

        /** The searchable parts of the page, in document order: cards and blocks. */
        private final List<JComponent> parts = new ArrayList<>();

        private final Set<String> terms = new LinkedHashSet<>();

        private String pendingGroup;

        private int slot;

        private Section(String id, Text title, Text subtitle) {
            this.id = id;
            setName(id);
            setOpaque(false);
            setLayout(new GridBagLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            heading.setFont(titleFont());
            heading.setForeground(textColor());
            hint.setFont(subtitleFont());
            hint.setForeground(mutedColor());
            title.value().observing().to(value -> {
                heading.setText(value);
                remember(value);
            });
            remember(title.source());
            if (subtitle == null) {
                hint.setVisible(false);
            } else {
                subtitle.value().observing().to(value -> {
                    hint.setText(value);
                    remember(value);
                });
                remember(subtitle.source());
            }
            add(header(heading, hint), cell(0, false));
        }

        /** Appends a card built from the given rows. */
        public Card addCard(Row... entries) {
            return addCard(List.of(entries));
        }

        /** Appends a card built from the given rows. */
        public Card addCard(List<Row> entries) {
            Card card = new Card(entries);
            parts.add(card);
            attach(card);
            return card;
        }

        /** Appends a component which brings its own chrome, e.g. a pair of preview tiles. */
        public Block addContent(Component component, String... keywords) {
            Block block = new Block(component, List.of(keywords), pendingGroup);
            pendingGroup = null;
            parts.add(block);
            attach(block);
            return block;
        }

        /** Appends a heading above the next block, e.g. "Other options". */
        public void addGroup(Text group) {
            remember(group.source());
            pendingGroup = group.source();
            group.value().observing().to(this::remember);
        }

        /** Appends a heading above the next block. */
        public void addGroup(String group) {
            addGroup(Text.of(group));
        }

        /** The identifier this section registered under. */
        public String id() {
            return id;
        }

        /**
         * Applies a query: cards drop the rows which do not answer, blocks hide themselves unless
         * the query names the page or a card next to them still shows something. Returns whether
         * the page shows anything.
         */
        public boolean filter(String needle) {
            boolean named = needle.isEmpty() || matches(needle);
            boolean anyCard = false;
            for (JComponent part : parts) {
                if (part instanceof Card card) {
                    // Every card has to run through the filter: writing this as
                    // `anyCard = anyCard || card.filter(...)` short-circuits once one card matched
                    // and leaves the later cards stuck in their previous hidden state.
                    boolean shown = card.filter(named ? "" : needle);
                    anyCard = anyCard || shown;
                }
            }
            boolean any = anyCard;
            for (JComponent part : parts) {
                if (part instanceof Block block) {
                    // A block belongs to the page: it stays while a card next to it answers, so a
                    // search for "theme" keeps the tile grid with the theme rows above it.
                    boolean visible = named || anyCard || block.matches(needle);
                    block.setVisible(visible);
                    any = any || visible;
                }
            }
            setVisible(any);
            revalidate();
            return any;
        }

        private boolean matches(String needle) {
            String query = needle.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (term.toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }
            }
            return false;
        }

        private void remember(String value) {
            if (value != null && !value.isBlank()) {
                terms.add(value);
            }
        }

        private void attach(JComponent part) {
            if (pendingGroup != null && part instanceof Block) {
                // A group heading belongs to the block below it, so both share the block's terms.
                remember(pendingGroup);
            }
            slot++;
            add(part, cell(slot, true));
            revalidate();
            repaint();
        }

        private GridBagConstraints cell(int row, boolean gap) {
            GridBagConstraints cell = new GridBagConstraints();
            cell.gridx = 0;
            cell.gridy = row;
            cell.weightx = 1.0;
            cell.fill = GridBagConstraints.HORIZONTAL;
            cell.anchor = GridBagConstraints.WEST;
            if (gap) {
                cell.insets = new Insets(18, 0, 0, 0);
            }
            return cell;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
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

    private final JPanel document = new JPanel(new GridBagLayout());

    private final JPanel column;

    private final JLabel none = new JLabel();

    /** The next row index in the single-column document grid. */
    private int documentRow;

    private final JScrollPane scroll;

    private final JPanel searchPane = new JPanel(new BorderLayout());

    private ARXSearchField search;

    /**
     * Prints a stack trace whenever the document jumps by more than a few rows, so the caller which
     * moved the bar can be identified: {@code -Dfreja.debug.settingsScroll=true} turns it on.
     */
    private static final boolean TRACE_SCROLL = Boolean.getBoolean("freja.debug.settingsScroll");

    /** The last value reported by the trace, so only genuine jumps are printed. */
    private int tracedValue;

    private Timer glide;

    private int target;

    private boolean animating;

    public ARXSettingsPane() {
        this("Settings", DEFAULT_RAIL_WIDTH);
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
        railPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, hairlineColor()), BorderFactory
                .createEmptyBorder(20, 16, 16, 16)));
        railPane.setMinimumSize(new Dimension(railWidth, 0));
        railPane.setPreferredSize(new Dimension(railWidth, 0));
        railPane.setMaximumSize(new Dimension(railWidth, Integer.MAX_VALUE));
        railPane.add(rail, BorderLayout.NORTH);
        add(railPane, BorderLayout.WEST);

        document.setOpaque(false);
        document.setAlignmentX(Component.LEFT_ALIGNMENT);
        document.setMinimumSize(new Dimension(MINIMUM_COLUMN, 0));
        document.setMaximumSize(new Dimension(MAXIMUM_COLUMN, Integer.MAX_VALUE));
        none.setFont(labelFont());
        none.setForeground(mutedColor());
        none.setHorizontalAlignment(SwingConstants.LEFT);
        none.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        none.setVisible(false);
        // The hint lives in the document itself: GridBagLayout ignores invisible components, so it
        // only takes space when a query truly leaves nothing on screen.
        attach(none, 0);
        // The hint already holds row 0, so the pages start below it.
        documentRow = 1;

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
        if (TRACE_SCROLL) {
            scroll.getVerticalScrollBar().addAdjustmentListener(event -> {
                int value = event.getValue();
                if (Math.abs(value - tracedValue) < 120) {
                    tracedValue = value;
                    return;
                }
                tracedValue = value;
                System.out.println("[settings] scroll " + value + " of max " + event.getAdjustable().getMaximum()
                        + " visible " + event.getAdjustable().getVisibleAmount() + (animating ? " (glide)" : ""));
                new Exception("[settings] scroll moved").printStackTrace(System.out);
            });
        }
        glide = new Timer(16, event -> glideScrollBar());

        searchPane.setOpaque(false);
        searchPane.setBorder(BorderFactory.createEmptyBorder(20, 30, 0, 30));
        searchPane.setVisible(false);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, 30, 20, 30));
        content.add(scroll, BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(searchPane, BorderLayout.NORTH);
        right.add(content, BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);
    }

    /**
     * Installs the search field in its own pane above the document. Typing narrows every page to
     * the rows which answer to the query, hides the pages and rail entries left empty, and glides
     * to the first hit; clearing the field restores the whole document. Both captions follow their
     * variables, so the field reads in the current language, and Ctrl+F reaches it from anywhere
     * inside the page.
     */
    public final void search(Text placeholder, Text noneMessage) {
        search = new ARXSearchField(placeholder.display(), null);
        search.setFont(labelFont());
        search.onTextChanged(() -> filter(search.query()));
        placeholder.value().observing().to(search::setPlaceholder);
        noneMessage.value().observing().to(none::setText);
        search.setPreferredSize(new Dimension(340, 30));
        searchPane.add(search, BorderLayout.WEST);
        searchPane.setVisible(true);
        searchPane.revalidate();
        searchPane.repaint();
        search.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control F"), "focusSettingsSearch");
        search.getActionMap().put("focusSettingsSearch", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                search.reveal();
            }
        });
    }

    /** Applies a query to the document; an empty query shows everything again. */
    public void filter(String needle) {
        String query = needle == null ? "" : needle.trim();
        // Filtering rebuilds cards and rail rows, and removing a component from the tree hands its
        // focus elsewhere. Remember who had it, and give it back when the query is done.
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        String first = null;
        for (Map.Entry<String, Section> entry : sections.entrySet()) {
            boolean visible = entry.getValue().filter(query);
            rail.setVisible(entry.getKey(), visible);
            if (visible && first == null) {
                first = entry.getKey();
            }
        }
        none.setVisible(first == null && !query.isEmpty());
        if (first != null) {
            rail.setSelected(first);
            show(first);
        } else {
            // Nothing to anchor on: take the hint into view instead of leaving the old position.
            scroll.getVerticalScrollBar().setValue(0);
        }
        document.revalidate();
        column.revalidate();
        scroll.revalidate();
        scroll.repaint();
        restoreFocus(owner);
    }

    /**
     * Puts the focus back where it was before the rebuild, as long as that component is still on
     * screen. Without this, typing one character into the search field ends the edit: the card
     * layout takes the field's rows away and Swing moves the focus on.
     */
    private void restoreFocus(Component owner) {
        if (owner == null || owner == this || !owner.isShowing()) {
            return;
        }
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != owner) {
            owner.requestFocusInWindow();
        }
    }

    /** The query currently in the search field. */
    public String query() {
        return search == null ? "" : search.query();
    }

    /** Registers a page: a rail entry, an anchored section heading and the scroll tracking. */
    public Section addSection(String id, Text title, Text subtitle, Icon icon) {
        Section section = new Section(id, title, subtitle);
        sections.put(id, section);
        // The gap between pages comes from the cell insets: a strut added without a GridBag
        // constraint would simply never be laid out.
        attach(section, documentRow++);
        ARXNavList.Row entry = rail.add(id, title.display(), icon);
        title.value().observing().to(text -> {
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
        if (section == null || !section.isVisible()) {
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

    /** One settings row: caption and description on the left, control vertically centred right. */
    public static Row row(Text caption, Text description, Component control) {
        return new Row(caption == null ? null : caption.value(), description == null ? null : description.value(), control);
    }

    /** A row whose caption is translated live and whose description is a plain label. */
    public static Row row(Text caption, String description, Component control) {
        return new Row(caption.value(), description == null || description.isEmpty() ? null : Variable.of(description), control);
    }

    /** A row whose text never changes: captions and descriptions are searchable as written. */
    public static Row row(String caption, String description, Component control) {
        return new Row(Variable.of(caption), Variable.of(description), control);
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

    /** A value label whose text follows a variable, e.g. a translated status. */
    public static JLabel value(Variable<String> text) {
        JLabel label = value("");
        text.observing().to(label::setText);
        return label;
    }

    /**
     * Puts a component in the next row of the document column. Everything in the column needs a
     * constraint - GridBagLayout silently leaves an unconstrained component unlaid-out - and the
     * gap between pages is carried by the insets rather than a strut.
     */
    private void attach(JComponent component, int row) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = row;
        cell.weightx = 1.0;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.anchor = GridBagConstraints.NORTHWEST;
        if (row > 0 && component != none) {
            cell.insets = new Insets(36, 0, 0, 0);
        }
        document.add(component, cell);
        document.revalidate();
        document.repaint();
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
            Section section = entry.getValue();
            if (!section.isVisible()) {
                continue;
            }
            if (sectionTop(section) <= position) {
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
}
