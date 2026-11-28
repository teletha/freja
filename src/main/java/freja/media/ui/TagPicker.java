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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.text.AttributedCharacterIterator;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.formdev.flatlaf.FlatClientProperties;

import freja.Icons;
import freja.Theme;
import freja.media.Tag;
import freja.media.store.LibraryTasks;

import laevatain.api.ComponentHelper;
import laevatain.component.ARXLabel;
import laevatain.component.ARXPanel;
import laevatain.component.ARXScrollPane;
import laevatain.component.ARXTextField;

/**
 * A GitHub-issue-style label picker shown as a popover instead of a modal dialog: a new-label field,
 * a searchable list of coloured labels and the current selection in one compact dropdown.
 */
public final class TagPicker extends JPanel implements ComponentHelper<TagPicker> {
    /** The list shows at most this many rows before it starts scrolling. */
    private static final int ROW_HEIGHT = 32;
    private static final int VISIBLE_ROWS = 7;
    private final Window owner;
    private final LibraryTasks tasks;
    private final long folderId;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Entry> entries = new ArrayList<>();
    private final JTextField search = new ARXTextField();
    private final JPanel list = new ARXPanel();
    private final JPanel createRow = new ARXPanel(new BorderLayout(8, 0));
    /** True while an input method is composing text, so its confirmation Enter is ignored. */
    private boolean composing;
    private final Runnable onChange;
    private final Runnable onClose;
    private TagPicker(Window owner, LibraryTasks tasks, long folderId, Runnable onChange, Runnable onClose) {
        this.owner = owner;
        this.tasks = tasks;
        this.folderId = folderId;
        this.onChange = onChange;
        this.onClose = onClose;
        background(Theme.SURFACE)
                .border(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                        BorderFactory.createEmptyBorder(12, 12, 10, 12)))
                .preferredSize(280, 120 + ROW_HEIGHT * VISIBLE_ROWS);
        JPanel header = new ARXPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new ARXLabel("タグを選択");
        title.setFont(Theme.font(Font.BOLD, 14));
        title.setForeground(Theme.TEXT);
        header.add(title);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        add(header, BorderLayout.NORTH);
        JPanel body = new ARXPanel(new BorderLayout(0, 8));
        body.setOpaque(false);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.search(14, Theme.MUTED));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ラベルを検索・新規作成…");
        search.setFont(Theme.font(Font.PLAIN, 13));
        search.setPreferredSize(new Dimension(0, 38));
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        search.setBorder(BorderFactory.createCompoundBorder(search.getBorder(),
                BorderFactory.createEmptyBorder(5, 0, 5, 0)));
        JScrollPane scroll = new ARXScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setPreferredSize(new Dimension(254, ROW_HEIGHT * VISIBLE_ROWS));
        scroll.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        JPanel top = new ARXPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        top.add(search, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        createRow.setOpaque(false);
        createRow.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        createRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        createRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createRow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                createLabel();
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter();
            }
            @Override
            public void removeUpdate(DocumentEvent event) {
                filter();
            }
            @Override
            public void changedUpdate(DocumentEvent event) {
                filter();
            }
        });
        search.addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent event) {
                AttributedCharacterIterator text = event.getText();
                int length = text == null ? 0 : text.getEndIndex() - text.getBeginIndex();
                composing = length > event.getCommittedCharacterCount();
            }
            @Override
            public void caretPositionChanged(InputMethodEvent event) {
            }
        });
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                // An IME confirmation Enter has no character and is ignored; a plain Enter is '\n'.
                if (event.getKeyCode() != KeyEvent.VK_ENTER || event.getKeyChar() == KeyEvent.CHAR_UNDEFINED
                        || event.isConsumed() || composing) {
                    return;
                }
                event.consume();
                createLabel();
            }
        });
        reload();
    }
    /** Shows the picker anchored at the given point in the invoker's coordinate space. */
    public static void show(java.awt.Component invoker, int x, int y, LibraryTasks tasks, long folderId, Runnable onChange) {
        JPopupMenu popup = new JPopupMenu();
        TagPicker picker = new TagPicker(SwingUtilities.getWindowAncestor(invoker), tasks, folderId,
                onChange, () -> popup.setVisible(false));
        popup.add(picker);
        popup.show(invoker, x, y);
        SwingUtilities.invokeLater(picker.search::requestFocusInWindow);
    }
    private void reload() {
        tasks.query(() -> {
            Set<String> folderTags = new LinkedHashSet<>(tasks.library().folderTags(folderId));
            List<TagView> views = new ArrayList<>();
            for (Tag tag : tasks.library().tags()) {
                List<String> linked = tasks.library().linkedTargets(tag.id()).stream().map(Tag::name).toList();
                views.add(new TagView(tag, linked));
            }
            return new PickerData(folderTags, views);
        }, data -> {
            if (data == null) {
                return;
            }
            selected.clear();
            selected.addAll(data.selected());
            entries.clear();
            list.removeAll();
            for (TagView view : data.views()) {
                Tag tag = view.tag();
                JPanel row = new ARXPanel(new BorderLayout(6, 0));
                row.setOpaque(true);
                row.setBackground(Theme.SURFACE);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
                row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                JPanel main = new ARXPanel(new BorderLayout(6, 0));
                main.setOpaque(false);
                JLabel label = new ARXLabel(tag.name());
                label.setIcon(Icons.tag(12, TagColors.of(tag.name())));
                label.setFont(Theme.font(Font.PLAIN, 12));
                label.setForeground(Theme.TEXT);
                main.add(label, BorderLayout.WEST);
                List<String> linked = view.linked();
                if (!linked.isEmpty()) {
                    JLabel linkHint = new ARXLabel("→ " + String.join(", ", linked));
                    linkHint.setFont(Theme.font(Font.PLAIN, 10));
                    linkHint.setForeground(Theme.MUTED);
                    main.add(linkHint, BorderLayout.CENTER);
                }
                JLabel check = new ARXLabel();
                check.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
                main.add(check, BorderLayout.EAST);
                main.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent event) {
                        if (selected.contains(tag.name())) {
                            selected.remove(tag.name());
                        } else {
                            selected.add(tag.name());
                        }
                        updateChecks();
                        applyNow();
                    }
                });
                row.add(main, BorderLayout.CENTER);
                JButton delete = new JButton("×");
                delete.setFont(Theme.font(Font.BOLD, 13));
                delete.setForeground(Theme.MUTED);
                delete.setOpaque(false);
                delete.setContentAreaFilled(false);
                delete.setBorderPainted(false);
                delete.setFocusPainted(false);
                delete.setToolTipText("このタグを削除");
                delete.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
                delete.addActionListener(event -> deleteTag(tag));
                row.add(delete, BorderLayout.EAST);
                Entry entry = new Entry(tag, row, check);
                row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        row.setBackground(Theme.HOVER);
                    }
                    @Override
                    public void mouseExited(MouseEvent event) {
                        row.setBackground(Theme.SURFACE);
                    }
                });
                entries.add(entry);
                list.add(row);
            }
            list.add(createRow);
            filter();
            updateChecks();
            list.revalidate();
            list.repaint();
        });
    }
    private void filter() {
        String query = search.getText() == null ? "" : search.getText().trim();
        String key = query.toLowerCase(Locale.ROOT);
        boolean exact = entries.stream().anyMatch(entry -> entry.tag().name().equalsIgnoreCase(query));
        for (Entry entry : entries) {
            entry.row().setVisible(entry.tag().name().toLowerCase(Locale.ROOT).contains(key));
        }
        createRow.removeAll();
        if (!query.isEmpty() && !exact) {
            JLabel name = new ARXLabel(query);
            name.setFont(Theme.font(Font.PLAIN, 12));
            name.setForeground(Theme.TEXT);
            createRow.add(name, BorderLayout.WEST);
            JLabel hint = new ARXLabel("  新しいラベルを作成");
            hint.setFont(Theme.font(Font.PLAIN, 12));
            hint.setForeground(Theme.TEXT);
            createRow.add(hint, BorderLayout.CENTER);
            createRow.add(new ARXLabel(Icons.plus(14, Theme.ACCENT)), BorderLayout.EAST);
            createRow.setVisible(true);
        } else {
            createRow.setVisible(false);
        }
        createRow.revalidate();
        createRow.repaint();
        list.revalidate();
        list.repaint();
    }
    private void updateChecks() {
        for (Entry entry : entries) {
            entry.check().setIcon(selected.contains(entry.tag().name()) ? Icons.check(15, Theme.ACCENT) : null);
        }
    }
    private void createLabel() {
        String name = search.getText() == null ? "" : search.getText().trim();
        if (name.isEmpty()) {
            return;
        }
        tasks.execute(() -> tasks.library().addTag(name), () -> {
            search.setText("");
            selected.add(name);
            updateChecks();
            applyNow(this::reload);
        });
    }
    /** Removes a tag itself, everywhere it was used, with a confirmation. */
    private void deleteTag(Tag tag) {
        int answer = confirm("タグを削除",
                "タグ『" + tag.name() + "』を削除します。\nすべてのフォルダから外れますが、写真自体は削除されません。");
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        List<Long> ids = List.of(tag.id());
        tasks.execute(() -> tasks.library().deleteTags(ids), () -> {
            selected.remove(tag.name());
            reload();
            onChange.run();
        });
    }
    /** Persists every change immediately, so the picker has no Apply step. */
    private void applyNow() {
        applyNow(null);
    }
    private void applyNow(Runnable onDone) {
        List<String> names = new ArrayList<>(selected);
        tasks.execute(() -> tasks.library().setFolderTags(folderId, names), () -> {
            onChange.run();
            if (onDone != null) {
                onDone.run();
            }
        });
    }
    private int confirm(String title, String message) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(owner, title);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        return pane.getValue() instanceof Integer value ? value : JOptionPane.CLOSED_OPTION;
    }
    private record TagView(Tag tag, List<String> linked) {
    }
    private record PickerData(Set<String> selected, List<TagView> views) {
    }
    private record Entry(Tag tag, JPanel row, JLabel check) {
    }
}
