/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.file.ui;

import freja.Icons;
import freja.Theme;
import freja.file.FileBrowser;
import freja.file.FileOperations;
import freja.file.FileTab;
import freja.file.FileTabs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.ListCellRenderer;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.JComponent;
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
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.JTextComponent;
import freja.util.PathUtil;
import psychopath.Progress;
/** A file manager with pinned folder tabs and a detailed file list, without a directory tree. */
public final class FileManagerPanel extends JPanel {
    public interface Listener {
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
    private final JList<FileTab> tabList = new JList<>(tabModel) {
        @Override
        protected void processKeyEvent(KeyEvent event) {
            // A/B/N belong to the pane. Consuming alone would still reach the UI's KeyListener, so
            // the typed event must not be passed on to the list's first-letter tab search at all.
            if (event.getID() == KeyEvent.KEY_TYPED && isShortcutChar(event.getKeyChar())) {
                event.consume();
                return;
            }
            super.processKeyEvent(event);
        }
    };
    private final List<FileBrowser.Entry> entries = new ArrayList<>();
    private final FileTableModel tableModel = new FileTableModel();
    /** Header clicks cycle ascending, descending and back to the list's original order. */
    private final TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(tableModel) {
        @Override
        public void toggleSortOrder(int column) {
            List<? extends SortKey> keys = getSortKeys();
            SortOrder current = keys.isEmpty() || keys.get(0).getColumn() != column
                    ? null : keys.get(0).getSortOrder();
            if (current == null) {
                setSortKeys(List.of(new SortKey(column, SortOrder.ASCENDING)));
            } else if (current == SortOrder.ASCENDING) {
                setSortKeys(List.of(new SortKey(column, SortOrder.DESCENDING)));
            } else {
                setSortKeys(List.of());
            }
        }
    };
    private final JTable table = new JTable(tableModel);

    /** The three explorer-style presentations of the same directory. */
    private enum View {
        DETAILS, CONTENT, TILES
    }

    private View view = View.DETAILS;

    private final JScrollPane listScroll = new JScrollPane(table);

    private final DefaultListModel<FileBrowser.Entry> entryListModel = new DefaultListModel<>();

    private final JList<FileBrowser.Entry> contentList = new JList<>(entryListModel);

    private final JList<FileBrowser.Entry> tileList = new JList<>(entryListModel);

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
    public FileManagerPanel(Listener listener) {
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
        installKeys();
    }
    /** Pane shortcuts: A goes to the parent, B and N walk the active tab's history. */
    private void installKeys() {
        bind(KeyEvent.VK_A, "fileUp", this::goUp);
        bind(KeyEvent.VK_B, "fileBack", this::goBack);
        bind(KeyEvent.VK_N, "fileForward", this::goForward);
    }
    /**
     * Bound to the window so the keys work whichever child has focus, but guarded by visibility so
     * the shortcuts stay confined to the file manager card of the main window.
     */
    private void bind(int keyCode, String name, Runnable action) {
        AbstractAction bound = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (isShowing() && !(focus instanceof JTextComponent) && !table.isEditing()) {
                    action.run();
                }
            }
        };
        getActionMap().put(name, bound);
        InputMap keys = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        keys.put(KeyStroke.getKeyStroke(keyCode, 0), name);
        keys.put(KeyStroke.getKeyStroke(keyCode, KeyEvent.SHIFT_DOWN_MASK), name);
    }
    private static boolean isShortcutChar(char key) {
        return key == 'a' || key == 'A' || key == 'b' || key == 'B' || key == 'n' || key == 'N';
    }
    private void goUp() {
        FileTab tab = tabs.active();
        if (tab != null && tab.current().getParent() != null) {
            tabs.navigate(tab.current().getParent());
        }
    }
    private void goBack() {
        tabs.back();
    }
    private void goForward() {
        tabs.forward();
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
        JMenuItem duplicate = new JMenuItem("タブを複製");
        duplicate.addActionListener(e -> tabs.duplicate(index));
        JMenuItem moveUp = new JMenuItem("上へ移動");
        moveUp.addActionListener(e -> tabs.move(index, index - 1));
        JMenuItem moveDown = new JMenuItem("下へ移動");
        moveDown.addActionListener(e -> tabs.move(index, index + 1));
        JMenuItem remove = new JMenuItem("タブを削除");
        remove.addActionListener(e -> tabs.remove(index));
        menu.add(rename);
        menu.add(duplicate);
        menu.add(moveUp);
        menu.add(moveDown);
        menu.addSeparator();
        menu.add(remove);
        menu.show(tabList, event.getX(), event.getY());
    }
    /** Adds a pinned folder as a tab; also used by drag-and-drop in a later phase. */
    public void addTab(Path folder) {
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
        back.setToolTipText("戻る (B)");
        forward.setToolTipText("進む (N)");
        up.setToolTipText("上に移動 (A)");
        back.addActionListener(event -> goBack());
        forward.addActionListener(event -> goForward());
        up.addActionListener(event -> goUp());
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

        JComboBox<String> views = new JComboBox<>(new String[] {"詳細表示", "コンテンツ表示", "並べて表示"});
        views.setFont(Theme.font(Font.PLAIN, 11));
        views.setPreferredSize(new Dimension(112, 26));
        views.setFocusable(false);
        views.addActionListener(event -> switchView(View.values()[views.getSelectedIndex()]));
        bar.add(Box.createHorizontalStrut(12));
        bar.add(views);

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
        applyListSettings();
        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TEXT);
        table.setRowSorter(sorter);
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
        sorter.addRowSorterListener(event -> updateViews());
        contentList.setCellRenderer(new ContentRenderer());
        contentList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tileList.setCellRenderer(new TileRenderer());
        tileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tileList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        tileList.setVisibleRowCount(-1);
        tileList.setFixedCellWidth(132);
        tileList.setFixedCellHeight(104);
        installOpenOnDoubleClick(contentList);
        installOpenOnDoubleClick(tileList);
        status.setFont(Theme.font(Font.PLAIN, 11));
        status.setForeground(Theme.MUTED);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 10, 4, 10));
        panel.add(bar, BorderLayout.NORTH);
        panel.add(listScroll, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }
    /** Applies the separately configurable file list font and row height. */
    public void applyListSettings() {
        Font font = Theme.fileListFont();
        table.setFont(font);
        table.getTableHeader().setFont(font);
        table.setRowHeight(Theme.scaledFileListRowHeight());
        contentList.setFont(font);
        contentList.setFixedCellHeight(Math.max(48, Theme.scaledFileListRowHeight() + 20));
        tileList.setFont(font);
        table.revalidate();
        table.repaint();
        table.getTableHeader().repaint();
        contentList.repaint();
        tileList.repaint();
    }

    /** Switches the presentation while keeping the same files selected. */
    private void switchView(View next) {
        if (view == next) {
            return;
        }
        List<Path> selected = selectedPaths();
        view = next;
        listScroll.setViewportView(switch (view) {
            case DETAILS -> table;
            case CONTENT -> contentList;
            case TILES -> tileList;
        });
        updateViews();
        restoreSelection(selected);
    }

    private JList<FileBrowser.Entry> activeList() {
        return view == View.TILES ? tileList : contentList;
    }

    /** Mirrors the sorted table rows into the models used by the two icon views. */
    private void updateViews() {
        entryListModel.clear();
        for (FileBrowser.Entry entry : sortedEntries()) {
            entryListModel.addElement(entry);
        }
    }

    private List<FileBrowser.Entry> sortedEntries() {
        List<FileBrowser.Entry> sorted = new ArrayList<>(entries);
        List<? extends SortKey> keys = sorter.getSortKeys();
        if (keys.isEmpty()) {
            return sorted;
        }
        sorted.sort((left, right) -> {
            for (SortKey key : keys) {
                int comparison = compare(left, right, key.getColumn());
                if (comparison != 0) {
                    return key.getSortOrder() == SortOrder.DESCENDING ? -comparison : comparison;
                }
            }
            return 0;
        });
        return sorted;
    }

    private static int compare(FileBrowser.Entry left, FileBrowser.Entry right, int column) {
        return switch (column) {
            case 2 -> Long.compare(left.directory() ? 0 : left.size(), right.directory() ? 0 : right.size());
            case 3 -> Long.compare(left.modified(), right.modified());
            default -> left.name().compareToIgnoreCase(right.name());
        };
    }

    private void installOpenOnDoubleClick(JList<FileBrowser.Entry> list) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int index = list.locationToIndex(event.getPoint());
                    if (index >= 0) {
                        open(entryListModel.get(index));
                    }
                }
            }
        });
    }

    private void restoreSelection(List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        Set<Path> wanted = new HashSet<>(paths);
        if (view == View.DETAILS) {
            table.clearSelection();
            for (int row = 0; row < table.getRowCount(); row++) {
                int model = table.convertRowIndexToModel(row);
                if (wanted.contains(entries.get(model).path())) {
                    table.addRowSelectionInterval(row, row);
                }
            }
        } else {
            JList<FileBrowser.Entry> list = activeList();
            list.clearSelection();
            for (int index = 0; index < entryListModel.size(); index++) {
                if (wanted.contains(entryListModel.get(index).path())) {
                    list.addSelectionInterval(index, index);
                }
            }
        }
    }

    private void open(int row) {
        if (row < 0 || row >= entries.size()) {
            return;
        }
        open(entries.get(row));
    }

    private void open(FileBrowser.Entry entry) {
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
        if (view == View.DETAILS) {
            for (int row : table.getSelectedRows()) {
                paths.add(entries.get(table.convertRowIndexToModel(row)).path());
            }
        } else {
            for (int index : activeList().getSelectedIndices()) {
                paths.add(entryListModel.get(index).path());
            }
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
            updateViews();
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
            updateViews();
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
        private final String[] columns = {"", "ファイル名", "サイズ", "最終更新日"};
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
                case 0 -> iconFor(entry, 16);
                case 1 -> entry.name();
                case 2 -> entry.directory() ? "" : humanSize(entry.size());
                case 3 -> date(entry.modified());
                default -> null;
            };
        }
    }

    private static javax.swing.Icon iconFor(FileBrowser.Entry entry, int size) {
        if (entry.directory()) {
            return Icons.folder(size, Theme.MUTED);
        }
        if (entry.archive()) {
            return Icons.of("mdi:zip-box", size, Theme.MUTED);
        }
        if (PathUtil.isImage(entry.path())) {
            return Icons.image(size, Theme.MUTED);
        }
        if (PathUtil.isVideo(entry.path())) {
            return Icons.play(size, Theme.MUTED);
        }
        return Icons.of("mdi:file", size, Theme.MUTED);
    }

    private static String kindLabel(FileBrowser.Entry entry) {
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

    private static String sizeLabel(FileBrowser.Entry entry) {
        return entry.directory() ? "" : humanSize(entry.size());
    }

    /** Explorer-style content view: a large icon beside the name and its details. */
    private static final class ContentRenderer extends JPanel implements ListCellRenderer<FileBrowser.Entry> {

        private final JLabel icon = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel info = new JLabel();

        ContentRenderer() {
            setLayout(new BorderLayout(12, 0));
            JPanel text = new JPanel(new java.awt.GridLayout(2, 1));
            text.setOpaque(false);
            text.add(name);
            text.add(info);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 12, 4, 8));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FileBrowser.Entry> list, FileBrowser.Entry value,
                int index, boolean selected, boolean focused) {
            icon.setIcon(iconFor(value, 32));
            name.setText(value.name());
            info.setText(kindLabel(value) + "  ·  " + sizeLabel(value) + "  ·  " + date(value.modified()));
            name.setFont(list.getFont().deriveFont(Font.PLAIN, list.getFont().getSize2D() + 1));
            info.setFont(list.getFont());
            setBackground(selected ? Theme.SELECTION : list.getBackground());
            name.setForeground(selected ? Theme.TEXT : Theme.TEXT);
            info.setForeground(selected ? Theme.TEXT : Theme.MUTED);
            return this;
        }
    }

    /** Explorer-style tiles: a grid of large icons with a short caption. */
    private static final class TileRenderer extends JPanel implements ListCellRenderer<FileBrowser.Entry> {

        private final JLabel icon = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel info = new JLabel();

        TileRenderer() {
            setLayout(new BorderLayout(4, 4));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            info.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel caption = new JPanel(new java.awt.GridLayout(2, 1));
            caption.setOpaque(false);
            caption.add(name);
            caption.add(info);
            add(icon, BorderLayout.CENTER);
            add(caption, BorderLayout.SOUTH);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 6, 8, 6));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FileBrowser.Entry> list, FileBrowser.Entry value,
                int index, boolean selected, boolean focused) {
            icon.setIcon(iconFor(value, 40));
            name.setText(value.name());
            info.setText(kindLabel(value) + "  ·  " + sizeLabel(value));
            name.setFont(list.getFont().deriveFont(Font.PLAIN, list.getFont().getSize2D() + 1));
            info.setFont(list.getFont());
            setBackground(selected ? Theme.SELECTION : list.getBackground());
            name.setForeground(Theme.TEXT);
            info.setForeground(selected ? Theme.TEXT : Theme.MUTED);
            return this;
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
