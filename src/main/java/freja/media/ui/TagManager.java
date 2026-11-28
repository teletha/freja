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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import com.formdev.flatlaf.FlatClientProperties;

import freja.Icons;
import freja.Theme;
import freja.media.Tag;
import freja.media.store.LibraryTasks;
import laevatain.component.ARXLabel;
import laevatain.component.ARXPanel;
import laevatain.component.ARXScrollPane;
import laevatain.component.ARXTable;
import laevatain.component.ARXTextField;
/**
 * A dialog for creating, renaming and removing tags. Tags are colour-coded and searchable, names
 * can be edited in place, and selected rows can be renamed with a preview or removed together.
 */
public final class TagManager extends JDialog {
    private final LibraryTasks tasks;
    private final Runnable onChange;
    private final TagTableModel model = new TagTableModel();
    private final JTable table = new ARXTable(model);
    private final JTextField search = new ARXTextField();
    private final JTextField newTag = new ARXTextField();
    private final JLabel status = new ARXLabel();
    public TagManager(Frame owner, LibraryTasks tasks, Runnable onChange) {
        super(owner, "タグを管理", true);
        this.tasks = tasks;
        this.onChange = onChange;
        setBackground(Theme.SURFACE);
        ((JPanel) getContentPane()).setBackground(Theme.SURFACE);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(20, 20, 16, 20));
        JPanel header = new ARXPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new ARXLabel("タグ");
        title.setFont(Theme.font(Font.BOLD, 20));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new ARXLabel("フォルダに付けるラベルを作成・改名・削除できます。");
        subtitle.setFont(Theme.font(Font.PLAIN, 12));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        header.add(title);
        header.add(subtitle);
        add(header, BorderLayout.NORTH);
        JPanel body = new ARXPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.search(14, Theme.MUTED));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "タグを検索");
        search.setFont(Theme.font(Font.PLAIN, 13));
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                model.setQuery(search.getText());
                updateStatus();
            }
            @Override
            public void removeUpdate(DocumentEvent event) {
                model.setQuery(search.getText());
                updateStatus();
            }
            @Override
            public void changedUpdate(DocumentEvent event) {
                model.setQuery(search.getText());
                updateStatus();
            }
        });
        JPanel create = new ARXPanel(new BorderLayout(8, 0));
        create.setOpaque(false);
        newTag.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "新しいタグ名");
        newTag.setFont(Theme.font(Font.PLAIN, 13));
        newTag.addActionListener(event -> createTag());
        JButton add = primary("追加", Icons.plus(14, Color.WHITE), this::createTag);
        create.add(newTag, BorderLayout.CENTER);
        create.add(add, BorderLayout.EAST);
        JPanel tools = new ARXPanel(new BorderLayout(12, 0));
        tools.setOpaque(false);
        tools.add(search, BorderLayout.CENTER);
        tools.add(create, BorderLayout.EAST);
        tools.setPreferredSize(new Dimension(0, 36));
        // Both fields share the same row, so give the creation field a modest fixed width.
        create.setPreferredSize(new Dimension(230, 34));
        body.add(tools, BorderLayout.NORTH);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFont(Theme.font(Font.PLAIN, 13));
        table.setBackground(Theme.SURFACE);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Color.WHITE);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        TagRenderer renderer = new TagRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Integer.class, renderer);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteTags");
        table.getActionMap().put("deleteTags", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                deleteSelected();
            }
        });
        JScrollPane scroll = new ARXScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE);
        body.add(scroll, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        JPanel footer = new ARXPanel(new BorderLayout());
        footer.setOpaque(false);
        status.setFont(Theme.font(Font.PLAIN, 11));
        status.setForeground(Theme.MUTED);
        JPanel buttons = new ARXPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton links = secondary("タグの結びつけ", Icons.tag(14, Theme.MUTED),
                () -> new TagLinkEditor(this, tasks, this::changed).setVisible(true));
        JButton smart = secondary("スマートタグを適用", Icons.refresh(14, Theme.MUTED), this::applySmartTags);
        JButton replace = secondary("一括改名", Icons.tag(14, Theme.MUTED), this::bulkRename);
        JButton delete = secondary("削除", Icons.close(13, Theme.DANGER), this::deleteSelected);
        JButton close = secondary("閉じる", null, this::dispose);
        buttons.add(links);
        buttons.add(smart);
        buttons.add(replace);
        buttons.add(delete);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(close);
        footer.add(status, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        add(footer, BorderLayout.SOUTH);
        reload();
        setSize(new Dimension(680, 500));
        setMinimumSize(new Dimension(520, 380));
        setLocationRelativeTo(owner);
    }
    private void updateStatus() {
        int shown = model.getRowCount();
        int total = model.size();
        status.setText(total == 0 ? "タグはまだありません。上の欄から追加できます。"
                : shown == total ? total + " 件のタグ" : shown + " / " + total + " 件を表示");
    }
    private void reload() {
        Set<Long> keep = new HashSet<>();
        for (int row : table.getSelectedRows()) {
            keep.add(model.tagAt(row).id());
        }
        tasks.query(() -> tasks.library().tags(), tags -> {
            if (tags == null) {
                return;
            }
            model.setTags(tags);
            for (int row = 0; row < model.getRowCount(); row++) {
                if (keep.contains(model.tagAt(row).id())) {
                    table.setRowSelectionInterval(row, row);
                }
            }
            updateStatus();
        });
    }
    private void changed() {
        reload();
        onChange.run();
    }
    private void applySmartTags() {
        int answer = JOptionPane.showConfirmDialog(this,
                "フォルダ名からタグを自動設定します。\n手動で付けたタグはそのまま残ります。",
                "スマートタグを適用", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        AtomicInteger count = new AtomicInteger();
        tasks.execute(() -> count.set(tasks.library().applySmartTagsToAllFolders()), () -> {
            changed();
            JOptionPane.showMessageDialog(this, count.get() + " 件のフォルダにタグを付与しました",
                    "スマートタグ", JOptionPane.INFORMATION_MESSAGE);
        });
    }
    private void createTag() {
        String name = newTag.getText();
        if (name == null || name.isBlank()) {
            return;
        }
        tasks.execute(() -> tasks.library().addTag(name), () -> {
            newTag.setText("");
            changed();
        });
    }
    private void bulkRename() {
        List<Tag> selected = selectedTags();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "対象のタグをテーブルから選択してください", "一括改名", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextField from = new ARXTextField();
        JTextField to = new ARXTextField();
        JTextArea preview = new JTextArea(6, 34);
        preview.setEditable(false);
        preview.setFont(Theme.font(Font.PLAIN, 12));
        preview.setBackground(Theme.SURFACE);
        preview.setForeground(Theme.MUTED);
        DocumentListener update = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                preview.setText(buildPreview(selected, from.getText(), to.getText()));
            }
            @Override
            public void removeUpdate(DocumentEvent event) {
                preview.setText(buildPreview(selected, from.getText(), to.getText()));
            }
            @Override
            public void changedUpdate(DocumentEvent event) {
                preview.setText(buildPreview(selected, from.getText(), to.getText()));
            }
        };
        from.getDocument().addDocumentListener(update);
        to.getDocument().addDocumentListener(update);
        preview.setText(buildPreview(selected, "", ""));
        JPanel panel = new ARXPanel(new GridLayout(3, 2, 10, 8));
        panel.add(label("置換する文字列"));
        panel.add(from);
        panel.add(label("置換後の文字列"));
        panel.add(to);
        panel.add(label("変更後の名前"));
        JScrollPane previewScroll = new ARXScrollPane(preview);
        previewScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        panel.add(previewScroll);
        int answer = JOptionPane.showConfirmDialog(this, panel,
                "選択した " + selected.size() + " 件を一括改名", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        List<Long> ids = selected.stream().map(Tag::id).toList();
        String fromText = from.getText();
        String toText = to.getText();
        AtomicInteger count = new AtomicInteger();
        tasks.execute(() -> count.set(tasks.library().bulkRenameTags(ids, fromText, toText)), () -> {
            changed();
            JOptionPane.showMessageDialog(this, count.get() + " 件のタグ名を変更しました", "一括改名",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }
    private String buildPreview(List<Tag> selected, String from, String to) {
        if (from == null || from.isEmpty()) {
            return "置換する文字列を入力すると、ここに変更後の名前が表示されます。";
        }
        StringBuilder text = new StringBuilder();
        for (Tag tag : selected) {
            String next = tag.name().replace(from, to == null ? "" : to).trim();
            text.append(tag.name()).append("   →   ").append(next.isEmpty() ? "(空の名前)" : next).append('\n');
        }
        return text.toString();
    }
    private void deleteSelected() {
        List<Tag> selected = selectedTags();
        if (selected.isEmpty()) {
            return;
        }
        int folders = selected.stream().mapToInt(Tag::folderCount).sum();
        int answer = JOptionPane.showConfirmDialog(this,
                "選択した " + selected.size() + " 件のタグを削除します。\n"
                        + folders + " 件のフォルダから外れますが、写真自体は削除されません。",
                "タグを削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        List<Long> ids = selected.stream().map(Tag::id).toList();
        tasks.execute(() -> tasks.library().deleteTags(ids), this::changed);
    }
    private List<Tag> selectedTags() {
        List<Tag> selected = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            selected.add(model.tagAt(row));
        }
        return selected;
    }
    private JLabel label(String text) {
        JLabel label = new ARXLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.MUTED);
        return label;
    }
    private JButton primary(String text, javax.swing.Icon icon, Runnable action) {
        JButton button = new JButton(text, icon);
        button.setFont(Theme.font(Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(Theme.ACCENT);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        button.addActionListener(event -> action.run());
        return button;
    }
    private JButton secondary(String text, javax.swing.Icon icon, Runnable action) {
        JButton button = new JButton(text, icon);
        button.setFont(Theme.font(Font.PLAIN, 12));
        button.setForeground(Theme.TEXT);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        button.addActionListener(event -> action.run());
        return button;
    }
    private final class TagRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            Tag tag = model.tagAt(row);
            label.setFont(Theme.font(Font.PLAIN, 13));
            label.setForeground(selected ? Color.WHITE : Theme.TEXT);
            label.setBackground(selected ? Theme.ACCENT : (row % 2 == 0 ? Theme.SURFACE : Theme.BACKGROUND));
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
            if (column == 0) {
                label.setText(tag.name());
                label.setIcon(Icons.tag(14, selected ? Color.WHITE : TagColors.of(tag.name())));
                label.setHorizontalAlignment(SwingConstants.LEFT);
            } else {
                label.setText(tag.folderCount() + " フォルダ");
                label.setIcon(null);
                label.setForeground(selected ? Color.WHITE : Theme.MUTED);
                label.setHorizontalAlignment(SwingConstants.RIGHT);
            }
            return label;
        }
    }
    private final class TagTableModel extends AbstractTableModel {
        private List<Tag> all = List.of();
        private List<Tag> visible = List.of();
        private String query = "";
        void setTags(List<Tag> tags) {
            all = tags;
            apply();
        }
        void setQuery(String query) {
            this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            apply();
        }
        private void apply() {
            visible = all.stream().filter(tag -> tag.name().toLowerCase(Locale.ROOT).contains(query)).toList();
            fireTableDataChanged();
        }
        int size() {
            return all.size();
        }
        Tag tagAt(int row) {
            return visible.get(row);
        }
        @Override
        public int getRowCount() {
            return visible.size();
        }
        @Override
        public int getColumnCount() {
            return 2;
        }
        @Override
        public String getColumnName(int column) {
            return column == 0 ? "タグ名" : "使用中のフォルダ";
        }
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }
        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? String.class : Integer.class;
        }
        @Override
        public Object getValueAt(int row, int column) {
            Tag tag = visible.get(row);
            return column == 0 ? tag.name() : tag.folderCount();
        }
        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0 || value == null) {
                return;
            }
            String name = value.toString();
            Tag tag = visible.get(row);
            if (name.equals(tag.name())) {
                return;
            }
            long id = tag.id();
            tasks.execute(() -> tasks.library().renameTag(id, name), TagManager.this::changed);
        }
    }
}
