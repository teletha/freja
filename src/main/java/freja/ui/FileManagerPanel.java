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
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import freja.util.PathUtil;
import psychopath.Progress;

/** A file manager with pinned folder tabs and a detailed file list, without a directory tree. */
final class FileManagerPanel extends JPanel {

    interface Listener {
        /** Opens a supported image or video in Freja's own viewer. */
        void openMedia(Path path);

        /** Called after files were added, removed or renamed so the catalog can be refreshed. */
        default void filesChanged() {
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final Listener listener;

    private final FileTabs tabs;

    private final FileBrowser browser = new FileBrowser();

    private final FileOperations operations = new FileOperations();

    private final DefaultListModel<FileTab> tabModel = new DefaultListModel<>();

    private final JList<FileTab> tabList = new JList<>(tabModel);

    private final FileTableModel tableModel = new FileTableModel();

    private final JTable table = new JTable(tableModel);

    private final List<FileBrowser.Entry> entries = new ArrayList<>();

    private final JButton back = new JButton(Icons.chevron(15, Theme.MUTED, true));

    private final JButton forward = new JButton(Icons.chevron(15, Theme.MUTED, false));

    private final JButton up = new JButton(Icons.of("mdi:arrow-up", 15, Theme.MUTED));

    private final JButton reload = new JButton(Icons.refresh(15, Theme.MUTED));

    private final JLabel pathLabel = new JLabel();

    private final JLabel status = new JLabel();

    private long request;

    private JDialog progressDialog;

    private JProgressBar progressBar;

    private JLabel progressLabel;

    FileManagerPanel(Listener listener) {
        this.listener = listener;
        this.tabs = new FileTabs(this::tabsChanged);
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabPane(), filePane());
        split.setDividerSize(5);
        split.setResizeWeight(0.0);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        tabsChanged();
    }

    /** The vertical tab strip on the left: pinned folders, not a directory tree. */
    private JPanel tabPane() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.SIDEBAR);
        panel.setPreferredSize(new Dimension(190, 0));

        tabList.setBackground(Theme.SIDEBAR);
        tabList.setFixedCellHeight(36);
        tabList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                FileTab tab = (FileTab) value;
                setText(tab.displayName());
                setIcon(Icons.folder(16, selected ? Color.WHITE : Theme.MUTED));
                setBackground(selected ? UIManager.getColor("ARX.selection") : Theme.SIDEBAR);
                setForeground(selected ? Theme.TEXT : Theme.MUTED);
                setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 8));
                return this;
            }
        });
        tabList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                tabs.select(tabList.getSelectedIndex());
            }
        });
        tabList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showTabMenu(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showTabMenu(event);
                }
            }
        });

        JButton add = new JButton(Icons.plus(15, Theme.MUTED));
        add.setToolTipText("フォルダをタブに追加");
        add.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        add.addActionListener(event -> addTab());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        footer.setOpaque(false);
        footer.add(add);

        panel.add(new JScrollPane(tabList), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void showTabMenu(MouseEvent event) {
        int index = tabList.locationToIndex(event.getPoint());
        if (index < 0) {
            return;
        }
        tabList.setSelectedIndex(index);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rename = new JMenuItem("タブ名を変更");
        rename.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "タブ名", tabs.tabs().get(index).displayName());
            if (name != null) {
                tabs.rename(index, name);
            }
        });
        JMenuItem moveUp = new JMenuItem("上へ移動");
        moveUp.addActionListener(e -> tabs.move(index, index - 1));
        JMenuItem moveDown = new JMenuItem("下へ移動");
        moveDown.addActionListener(e -> tabs.move(index, index + 1));
        JMenuItem remove = new JMenuItem("タブを削除");
        remove.addActionListener(e -> tabs.remove(index));
        menu.add(rename);
        menu.add(moveUp);
        menu.add(moveDown);
        menu.addSeparator();
        menu.add(remove);
        menu.show(tabList, event.getX(), event.getY());
    }

    /** Adds a pinned folder as a tab; also used by drag-and-drop in a later phase. */
    void addTab(Path folder) {
        tabs.add(folder);
    }

    private void addTab() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("フォルダを選択");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            tabs.add(chooser.getSelectedFile().toPath());
        }
    }

    /** The right side: navigation bar and the detailed file table. */
    private JPanel filePane() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setBackground(Theme.SURFACE);
        back.addActionListener(event -> tabs.back());
        forward.addActionListener(event -> tabs.forward());
        up.addActionListener(event -> {
            FileTab tab = tabs.active();
            if (tab != null && tab.current().getParent() != null) {
                tabs.navigate(tab.current().getParent());
            }
        });
        reload.addActionListener(event -> refresh());
        pathLabel.setFont(Theme.font(Font.PLAIN, 12));
        pathLabel.setForeground(Theme.MUTED);
        for (JButton button : List.of(back, forward, up, reload)) {
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
            button.setContentAreaFilled(false);
            button.setFocusable(false);
            bar.add(button);
        }
        bar.add(pathLabel);

        JButton copy = new JButton(Icons.of("mdi:content-copy", 15, Theme.MUTED));
        copy.setToolTipText("選択した項目をコピー");
        copy.addActionListener(event -> transfer(false));
        JButton move = new JButton(Icons.of("mdi:export", 15, Theme.MUTED));
        move.setToolTipText("選択した項目を移動");
        move.addActionListener(event -> transfer(true));
        JButton rename = new JButton(Icons.pencil(15, Theme.MUTED));
        rename.setToolTipText("名前を変更");
        rename.addActionListener(event -> renameSelected());
        JButton delete = new JButton(Icons.trash(15, Theme.MUTED));
        delete.setToolTipText("削除");
        delete.addActionListener(event -> deleteSelected());
        JButton pack = new JButton(Icons.of("mdi:zip-box", 15, Theme.MUTED));
        pack.setToolTipText("ZIPに圧縮");
        pack.addActionListener(event -> packSelected());
        JButton unpack = new JButton(Icons.of("mdi:import", 15, Theme.MUTED));
        unpack.setToolTipText("ここに解凍");
        unpack.addActionListener(event -> extractSelected());
        bar.add(Box.createHorizontalStrut(12));
        for (JButton button : List.of(copy, move, rename, delete, pack, unpack)) {
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
            button.setContentAreaFilled(false);
            button.setFocusable(false);
            bar.add(button);
        }

        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXT);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(42);
        table.getColumnModel().getColumn(0).setCellRenderer(new IconRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = table.rowAtPoint(event.getPoint());
                    if (row >= 0) {
                        open(table.convertRowIndexToModel(row));
                    }
                }
            }
        });

        status.setFont(Theme.font(Font.PLAIN, 11));
        status.setForeground(Theme.MUTED);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));

        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void open(int row) {
        if (row < 0 || row >= entries.size()) {
            return;
        }
        FileBrowser.Entry entry = entries.get(row);
        if (entry.directory() || entry.archive()) {
            tabs.navigate(entry.path());
            return;
        }
        if (PathUtil.isImage(entry.path()) || PathUtil.isVideo(entry.path())) {
            listener.openMedia(entry.path());
            return;
        }
        try {
            Desktop.getDesktop().open(entry.path().toFile());
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, "ファイルを開けませんでした: " + e.getMessage(), "ファイル", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void tabsChanged() {
        tabModel.clear();
        for (FileTab tab : tabs.tabs()) {
            tabModel.addElement(tab);
        }
        if (tabs.selectedIndex() >= 0 && tabs.selectedIndex() < tabModel.size()) {
            tabList.setSelectedIndex(tabs.selectedIndex());
        }
        FileTab active = tabs.active();
        back.setEnabled(active != null && active.canBack());
        forward.setEnabled(active != null && active.canForward());
        up.setEnabled(active != null && active.current().getParent() != null);
        refresh();
    }

    private List<Path> selectedPaths() {
        List<Path> paths = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            FileBrowser.Entry entry = entries.get(table.convertRowIndexToModel(row));
            paths.add(entry.path());
        }
        return paths;
    }

    private Path chooseTarget() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("移動先フォルダを選択");
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private boolean confirmReplace(Path target) {
        if (!Files.exists(target)) {
            return false;
        }
        return JOptionPane.showConfirmDialog(this, "同名の項目が既にあります。置換しますか？", "ファイル操作",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void transfer(boolean move) {
        List<Path> sources = selectedPaths();
        if (sources.isEmpty()) {
            return;
        }
        Path target = chooseTarget();
        if (target == null) {
            return;
        }
        boolean replace = false;
        for (Path source : sources) {
            if (confirmReplace(target.resolve(source.getFileName()))) {
                replace = true;
                break;
            }
        }
        begin(move ? "移動" : "コピー");
        FileOperations.Listener listener = operationListener();
        if (move) {
            operations.move(sources, target, replace, listener);
        } else {
            operations.copy(sources, target, replace, listener);
        }
    }

    private void renameSelected() {
        List<Path> paths = selectedPaths();
        if (paths.size() != 1) {
            JOptionPane.showMessageDialog(this, "名前を変更する項目を1つ選んでください。", "ファイル", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path source = paths.get(0);
        String name = JOptionPane.showInputDialog(this, "新しい名前", source.getFileName().toString());
        if (name == null || name.isBlank()) {
            return;
        }
        boolean replace = confirmReplace(source.resolveSibling(name));
        begin("名前を変更");
        operations.rename(source, name, replace, operationListener());
    }

    private void deleteSelected() {
        List<Path> paths = selectedPaths();
        if (paths.isEmpty()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, paths.size() + " 件を削除しますか？", "削除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        begin("削除");
        operations.delete(paths, operationListener());
    }

    private void packSelected() {
        List<Path> sources = selectedPaths();
        if (sources.isEmpty()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("ZIPファイルを保存");
        chooser.setFileFilter(new FileNameExtensionFilter("ZIPアーカイブ", "zip"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path archive = chooser.getSelectedFile().toPath();
        if (!archive.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            archive = Path.of(archive + ".zip");
        }
        begin("圧縮");
        operations.pack(sources, archive, operationListener());
    }

    private void extractSelected() {
        List<Path> paths = selectedPaths();
        if (paths.size() != 1 || !PathUtil.isArchive(paths.get(0))) {
            JOptionPane.showMessageDialog(this, "アーカイブを1つ選んでください。", "ファイル", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path target = chooseTarget();
        if (target == null) {
            return;
        }
        begin("解凍");
        operations.extract(paths.get(0), target, operationListener());
    }

    private void begin(String title) {
        progressDialog = new JDialog(SwingUtilities.getWindowAncestor(this), title, java.awt.Dialog.ModalityType.MODELESS);
        progressLabel = new JLabel("準備中...");
        progressBar = new JProgressBar(0, 100);
        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        body.add(progressLabel, BorderLayout.NORTH);
        body.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(body);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setVisible(true);
    }

    private FileOperations.Listener operationListener() {
        return new FileOperations.Listener() {
            @Override
            public void progress(Progress progress) {
                if (progressBar == null || progressLabel == null) {
                    return;
                }
                if (progress == null) {
                    progressBar.setIndeterminate(true);
                    progressLabel.setText("処理中...");
                    return;
                }
                progressBar.setIndeterminate(false);
                long size = progress.totalSize;
                int percent = size > 0
                        ? (int) (progress.completedSize() * 100 / size)
                        : (progress.totalFiles > 0 ? progress.completedFiles() * 100 / progress.totalFiles : 0);
                progressBar.setValue(Math.max(0, Math.min(100, percent)));
                progressLabel.setText(String.format(Locale.ROOT, "%1$d / %2$d  ·  %3$s / %4$s",
                        progress.completedFiles(), progress.totalFiles, humanSize(progress.completedSize()), humanSize(size)));
            }

            @Override
            public void done(Exception error) {
                if (progressDialog != null) {
                    progressDialog.dispose();
                    progressDialog = null;
                }
                progressBar = null;
                progressLabel = null;
                if (error != null) {
                    JOptionPane.showMessageDialog(FileManagerPanel.this, "操作に失敗しました: " + error.getMessage(),
                            "ファイル", JOptionPane.ERROR_MESSAGE);
                }
                refresh();
                listener.filesChanged();
            }
        };
    }

    private void refresh() {
        FileTab active = tabs.active();
        if (active == null) {
            entries.clear();
            tableModel.fireTableDataChanged();
            pathLabel.setText("フォルダを追加してください");
            status.setText("");
            return;
        }
        pathLabel.setText(active.current().toString());
        long id = ++request;
        browser.list(active.current(), (found, error) -> {
            if (id != request) {
                return;
            }
            entries.clear();
            if (found != null) {
                entries.addAll(found);
            }
            tableModel.fireTableDataChanged();
            long bytes = 0;
            for (FileBrowser.Entry entry : entries) {
                bytes += entry.size();
            }
            status.setText(String.format(Locale.ROOT, "%1$d 項目  ·  %2$s", entries.size(), humanSize(bytes))
                    + (error == null ? "" : "  ·  読み込みに失敗しました"));
        });
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String date(long millis) {
        if (millis <= 0) {
            return "";
        }
        return DATE.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private final class FileTableModel extends AbstractTableModel {

        private final String[] columns = {"", "ファイル名", "サイズ", "最終更新日", "種別"};

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? javax.swing.Icon.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            FileBrowser.Entry entry = entries.get(row);
            return switch (column) {
                case 0 -> icon(entry);
                case 1 -> entry.name();
                case 2 -> entry.directory() ? "" : humanSize(entry.size());
                case 3 -> date(entry.modified());
                default -> kind(entry);
            };
        }

        private javax.swing.Icon icon(FileBrowser.Entry entry) {
            if (entry.directory()) {
                return Icons.folder(16, Theme.MUTED);
            }
            if (entry.archive()) {
                return Icons.of("mdi:zip-box", 16, Theme.MUTED);
            }
            if (PathUtil.isImage(entry.path())) {
                return Icons.image(16, Theme.MUTED);
            }
            if (PathUtil.isVideo(entry.path())) {
                return Icons.play(16, Theme.MUTED);
            }
            return Icons.of("mdi:file", 16, Theme.MUTED);
        }

        private String kind(FileBrowser.Entry entry) {
            if (entry.directory()) {
                return "フォルダ";
            }
            if (entry.archive()) {
                return "アーカイブ";
            }
            if (PathUtil.isImage(entry.path())) {
                return "画像";
            }
            if (PathUtil.isVideo(entry.path())) {
                return "動画";
            }
            return "ファイル";
        }
    }

    private static final class IconRenderer extends DefaultTableCellRenderer {

        @Override
        protected void setValue(Object value) {
            setIcon(value instanceof javax.swing.Icon icon ? icon : null);
            setText(null);
            setHorizontalAlignment(SwingConstants.CENTER);
        }
    }
}
