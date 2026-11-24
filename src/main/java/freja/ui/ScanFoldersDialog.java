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
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import com.formdev.flatlaf.util.SystemFileChooser;

import freja.model.Folder;
import laevatain.ARXFileChooser;
import freja.util.PathUtil;

/** Dialog for managing the folders the library watches and scans. */
public final class ScanFoldersDialog extends JDialog {

    private static final String LOCATION_KEY = "scanFolders";

    /** Actions requested by this dialog. */
    public interface Listener {

        void onRegisterFolder(Path path);

        void onClearFolders();

        void onRemoveFolders(List<Long> folderIds);

        void onScanFolders(List<Long> folderIds);

        void onScanEnabled(long folderId, boolean enabled);

        void onStartupScanChanged(boolean enabled);

        void onSweepChanged(boolean enabled);
    }

    private final JCheckBox startupScan = new JCheckBox("起動時にスキャンする");

    private final JCheckBox sweepScan = new JCheckBox("定期スキャンする（5分ごと）");

    private final DefaultListModel<Folder> registeredModel = new DefaultListModel<>();

    private final JList<Folder> registeredList = new JList<>(registeredModel);

    private final JLabel emptyHint = new JLabel("登録されているフォルダはありません");

    private final Map<String, Folder> registered = new HashMap<>();

    private Listener listener = new Listener() {
        @Override
        public void onRegisterFolder(Path path) {
        }

        @Override
        public void onClearFolders() {
        }

        @Override
        public void onRemoveFolders(List<Long> folderIds) {
        }

        @Override
        public void onScanFolders(List<Long> folderIds) {
        }

        @Override
        public void onScanEnabled(long folderId, boolean enabled) {
        }

        @Override
        public void onStartupScanChanged(boolean enabled) {
        }

        @Override
        public void onSweepChanged(boolean enabled) {
        }
    };

    public ScanFoldersDialog(JFrame owner) {
        super(owner, "監視フォルダの管理", ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(Theme.SURFACE);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        registeredList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        registeredList.setFont(Theme.font(Font.PLAIN, 13));
        registeredList.setBackground(Theme.SURFACE);
        registeredList.setForeground(Theme.TEXT);
        registeredList.setCellRenderer(new RegisteredRenderer());
        registeredList.setFixedCellHeight(30);
        JPopupMenu actions = new JPopupMenu();
        JMenuItem scanNow = new JMenuItem("今すぐスキャン");
        scanNow.addActionListener(event -> {
            List<Long> ids = selectedFolderIds();
            if (!ids.isEmpty()) {
                listener.onScanFolders(ids);
            }
        });
        JMenuItem toggle = new JMenuItem("監視を切り替え");
        toggle.addActionListener(event -> {
            for (int index : registeredList.getSelectedIndices()) {
                Folder folder = registeredModel.get(index);
                if (folder != null) {
                    listener.onScanEnabled(folder.id(), !folder.scanEnabled());
                }
            }
        });
        JMenuItem remove = new JMenuItem("ライブラリから削除");
        remove.addActionListener(event -> {
            List<Long> ids = selectedFolderIds();
            if (!ids.isEmpty()) {
                listener.onRemoveFolders(ids);
            }
        });
        actions.add(scanNow);
        actions.add(toggle);
        actions.add(remove);
        registeredList.setComponentPopupMenu(actions);
        registeredList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                selectPopupRow(event);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
                selectPopupRow(event);
            }

            private void selectPopupRow(java.awt.event.MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    return;
                }
                int index = registeredList.locationToIndex(event.getPoint());
                if (index >= 0 && !registeredList.isSelectedIndex(index)) {
                    registeredList.setSelectedIndex(index);
                }
            }
        });
        JScrollPane registeredScroll = new JScrollPane(registeredList);
        registeredScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JPanel registeredPanel = new JPanel(new BorderLayout(0, 6));
        registeredPanel.setOpaque(false);
        JLabel registeredTitle = new JLabel("監視フォルダ");
        registeredTitle.setToolTipText("✓ は監視中、× は監視から除外されたフォルダです");
        registeredTitle.setFont(Theme.font(Font.BOLD, 13));
        registeredTitle.setForeground(Theme.TEXT);
        emptyHint.setFont(Theme.font(Font.PLAIN, 11));
        emptyHint.setForeground(Theme.MUTED);
        emptyHint.setVisible(false);
        JPanel registeredHeader = new JPanel(new BorderLayout(0, 4));
        registeredHeader.setOpaque(false);
        registeredHeader.add(registeredTitle, BorderLayout.NORTH);
        registeredHeader.add(emptyHint, BorderLayout.SOUTH);
        registeredPanel.add(registeredHeader, BorderLayout.NORTH);
        registeredPanel.add(registeredScroll, BorderLayout.CENTER);

        add(registeredPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        startupScan.setOpaque(false);
        startupScan.addActionListener(event -> listener.onStartupScanChanged(startupScan.isSelected()));
        sweepScan.setOpaque(false);
        sweepScan.addActionListener(event -> listener.onSweepChanged(sweepScan.isSelected()));
        JPanel scanChecks = new JPanel();
        scanChecks.setOpaque(false);
        scanChecks.setLayout(new BoxLayout(scanChecks, BoxLayout.Y_AXIS));
        scanChecks.add(startupScan);
        scanChecks.add(sweepScan);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton add = new JButton("フォルダを追加…", Icons.plus(14, Theme.MUTED));
        add.setFont(Theme.font(Font.PLAIN, 12));
        add.setToolTipText("システムのダイアログで複数のフォルダを選んで監視対象に追加します");
        add.addActionListener(event -> addFolders());
        JButton close = new JButton("閉じる");
        close.addActionListener(event -> dispose());
        JButton clear = new JButton("すべてクリア");
        clear.setFont(Theme.font(Font.PLAIN, 12));
        clear.setForeground(Theme.DANGER);
        clear.setOpaque(false);
        clear.setContentAreaFilled(false);
        clear.setFocusPainted(false);
        clear.setToolTipText("監視フォルダとライブラリの写真データをすべて削除します");
        clear.addActionListener(event -> listener.onClearFolders());
        buttons.add(clear);
        buttons.add(add);
        buttons.add(close);
        south.add(scanChecks, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        setSize(new Dimension(560, 520));
        setLocationRelativeTo(owner);
    }

    public void setListener(Listener listener) {
        if (listener != null) {
            this.listener = listener;
        }
    }

    public void setFolders(List<Folder> folders, boolean startup, boolean sweep) {
        // Only the roots the user chose are watched-folder entries; directories discovered below
        // them are managed catalog rows and must not clutter this list.
        List<Folder> roots = folders.stream().filter(Folder::explicit).toList();
        Folder selected = selectedRegisteredFolder();
        long selectedId = selected == null ? -1 : selected.id();
        registered.clear();
        for (Folder folder : roots) {
            registered.put(PathUtil.key(folder.path()), folder);
        }
        startupScan.setSelected(startup);
        sweepScan.setSelected(sweep);

        List<Folder> sorted = new ArrayList<>(roots);
        sorted.sort(Comparator.comparing(folder -> folder.name() == null || folder.name().isBlank()
                ? folder.path().toString() : folder.name(), String.CASE_INSENSITIVE_ORDER));
        registeredModel.clear();
        for (Folder folder : sorted) {
            registeredModel.addElement(folder);
        }
        emptyHint.setVisible(sorted.isEmpty());
        if (selectedId == -1 || !selectRegisteredFolder(selectedId)) {
            registeredList.clearSelection();
        }
    }

    private boolean selectRegisteredFolder(long folderId) {
        for (int i = 0; i < registeredModel.size(); i++) {
            if (registeredModel.get(i).id() == folderId) {
                registeredList.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private Folder selectedRegisteredFolder() {
        return registeredList.getSelectedValue();
    }

    private List<Long> selectedFolderIds() {
        List<Long> ids = new ArrayList<>();
        for (int index : registeredList.getSelectedIndices()) {
            Folder folder = registeredModel.get(index);
            if (folder != null) {
                ids.add(folder.id());
            }
        }
        return ids;
    }

    /** Picks one or more folders with the platform's native chooser and registers all of them. */
    private void addFolders() {
        File fallback = registered.values().stream()
                .map(folder -> folder.path().toFile())
                .filter(File::isDirectory)
                .findFirst()
                .orElse(null);
        ARXFileChooser chooser = new ARXFileChooser(LOCATION_KEY, fallback);
        chooser.setDialogTitle("監視するフォルダを選択");
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setApproveButtonText("追加");
        if (chooser.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selected = chooser.getSelectedFiles();
        if (selected == null || selected.length == 0) {
            File single = chooser.getSelectedFile();
            selected = single == null ? new File[0] : new File[] {single};
        }
        for (File file : selected) {
            if (file != null && file.isDirectory()) {
                listener.onRegisterFolder(file.toPath().toAbsolutePath().normalize());
            }
        }
    }

    /** One line per folder: its display name, with the scan state as an icon. */
    private static final class RegisteredRenderer extends DefaultListCellRenderer {

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            setIcon(null);
            setToolTipText(null);
            if (value instanceof Folder folder) {
                setText(folder.name() == null || folder.name().isBlank()
                        ? String.valueOf(folder.path().getFileName()) : folder.name());
                setIcon(folder.scanEnabled() ? Icons.check(14, Theme.ACCENT) : Icons.close(14, Theme.DANGER));
                setToolTipText(folder.path().toString());
            }
            return this;
        }
    }

}
