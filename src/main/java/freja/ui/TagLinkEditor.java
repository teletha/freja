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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import freja.model.Tag;
import freja.store.Library;

/**
 * Edits one-way links between tags. Linking {@code 井上和彦 → 漫画} makes folders tagged with the
 * former show up in a search for the latter; the reverse is not implied.
 */
final class TagLinkEditor extends JDialog {

    private final Library library;

    private final Runnable onChange;

    private final DefaultListModel<Tag> sourceModel = new DefaultListModel<>();

    private final JList<Tag> sources = new JList<>(sourceModel);

    private final JPanel targetList = new JPanel();

    private final List<Entry> targets = new ArrayList<>();

    private final JLabel reverse = new JLabel();

    TagLinkEditor(Window owner, Library library, Runnable onChange) {
        super(owner, "タグの結びつけ", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.library = library;
        this.onChange = onChange;
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("タグの結びつけ");
        title.setFont(Theme.font(Font.BOLD, 18));
        title.setForeground(Theme.TEXT);
        JLabel hint = new JLabel("<html>左のタグを右のタグに結びつけます。例えば「井上和彦」→「漫画」とすると、"
                + "「漫画」で検索したときに井上和彦のフォルダも見つかります。結びつきは一方通行です。</html>");
        hint.setFont(Theme.font(Font.PLAIN, 11));
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 0, 12, 0));
        header.add(title);
        header.add(hint);
        add(header, BorderLayout.NORTH);

        sources.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sources.setFont(Theme.font(Font.PLAIN, 13));
        sources.setBackground(Theme.SURFACE);
        sources.setForeground(Theme.TEXT);
        sources.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Tag tag) {
                    setText(tag.name());
                    setIcon(Icons.tag(13, TagColors.of(tag.name())));
                }
                return this;
            }
        });
        sources.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadTargets();
            }
        });
        JScrollPane sourceScroll = new JScrollPane(sources);
        sourceScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sourceScroll.setPreferredSize(new Dimension(190, 320));

        targetList.setOpaque(false);
        targetList.setLayout(new BoxLayout(targetList, BoxLayout.Y_AXIS));
        JScrollPane targetScroll = new JScrollPane(targetList);
        targetScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        targetScroll.setPreferredSize(new Dimension(260, 320));

        JPanel center = new JPanel(new java.awt.GridLayout(1, 2, 14, 0));
        center.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(new JLabel("元になるタグ"), BorderLayout.NORTH);
        left.add(sourceScroll, BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.add(new JLabel("結びつける先"), BorderLayout.NORTH);
        right.add(targetScroll, BorderLayout.CENTER);
        center.add(left);
        center.add(right);
        add(center, BorderLayout.CENTER);

        reverse.setFont(Theme.font(Font.PLAIN, 11));
        reverse.setForeground(Theme.MUTED);
        JButton save = new JButton("保存", Icons.check(14, Color.WHITE));
        save.setFont(Theme.font(Font.BOLD, 12));
        save.setForeground(Color.WHITE);
        save.setBackground(Theme.ACCENT);
        save.setOpaque(true);
        save.setContentAreaFilled(true);
        save.setBorderPainted(false);
        save.setFocusPainted(false);
        save.addActionListener(event -> save());
        JButton close = new JButton("閉じる");
        close.setFont(Theme.font(Font.PLAIN, 12));
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.addActionListener(event -> dispose());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(reverse, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(close);
        actions.add(save);
        footer.add(actions, BorderLayout.EAST);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        add(footer, BorderLayout.SOUTH);

        loadSources();
        setSize(new Dimension(520, 500));
        setLocationRelativeTo(owner);
    }

    private void loadSources() {
        Tag selected = sources.getSelectedValue();
        sourceModel.clear();
        for (Tag tag : library.tags()) {
            sourceModel.addElement(tag);
        }
        if (!sourceModel.isEmpty()) {
            sources.setSelectedIndex(0);
        }
        if (selected != null) {
            for (int i = 0; i < sourceModel.size(); i++) {
                if (sourceModel.get(i).id() == selected.id()) {
                    sources.setSelectedIndex(i);
                    break;
                }
            }
        }
        loadTargets();
    }

    private void loadTargets() {
        Tag source = sources.getSelectedValue();
        targets.clear();
        targetList.removeAll();
        reverse.setText("");
        if (source == null) {
            return;
        }
        List<Tag> linked = library.linkedTargets(source.id());
        for (Tag tag : library.tags()) {
            if (tag.id() == source.id()) {
                continue;
            }
            JCheckBox box = new JCheckBox(tag.name(), linked.stream().anyMatch(item -> item.id() == tag.id()));
            box.setOpaque(false);
            box.setFont(Theme.font(Font.PLAIN, 12));
            box.setForeground(Theme.TEXT);
            box.setAlignmentX(LEFT_ALIGNMENT);
            targets.add(new Entry(tag, box));
            targetList.add(box);
        }
        List<Tag> incoming = library.linkedSources(source.id());
        if (!incoming.isEmpty()) {
            List<String> names = incoming.stream().map(Tag::name).toList();
            reverse.setText("このタグを指すタグ: " + String.join(", ", names));
        }
        targetList.revalidate();
        targetList.repaint();
    }

    private void save() {
        Tag source = sources.getSelectedValue();
        if (source == null) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (Entry entry : targets) {
            if (entry.box().isSelected()) {
                ids.add(entry.tag().id());
            }
        }
        try {
            library.setTagLinks(source.id(), ids);
            onChange.run();
            dispose();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "結びつけを保存できません", JOptionPane.WARNING_MESSAGE);
        }
    }

    private record Entry(Tag tag, JCheckBox box) {
    }
}
