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
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import freja.model.Folder;
import freja.util.PathUtil;

/** Picasa-style folder manager backed by the real filesystem tree. */
public final class ScanFoldersDialog extends JDialog {

    /** Actions requested by this dialog. */
    public interface Listener {

        void onRegisterFolder(Path path);

        void onRemoveFolder(long folderId);

        void onScanFolder(long folderId);

        void onScanEnabled(long folderId, boolean enabled);

        void onStartupScanChanged(boolean enabled);

        void onSweepChanged(boolean enabled);
    }

    private record Node(Path path, String label) {
    }

    private static final class FileNode extends DefaultMutableTreeNode {

        final Path path;

        boolean loaded;

        boolean loading;

        FileNode(Path path, String label) {
            super(new Node(path, label));
            this.path = path;
        }

        /** Directories are expandable even before their children have been loaded. */
        @Override
        public boolean isLeaf() {
            return false;
        }
    }

    private final ExecutorService treeLoader = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "freja-tree-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final JTree tree = new JTree();

    private final JLabel currentName = new JLabel("フォルダを選択してください");

    private final JLabel currentPath = new JLabel(" ");

    private final JCheckBox startupScan = new JCheckBox("起動時にスキャンする");

    private final JCheckBox sweepScan = new JCheckBox("定期スキャンする（5分ごと）");

    private final JButton scanButton = new JButton("今すぐスキャン");

    private final JButton toggleButton = new JButton("スキャンを無効化");

    private final JButton registerButton = new JButton("フォルダを登録");

    private final JButton removeButton = new JButton("ライブラリから削除");

    private final Map<String, Folder> registered = new HashMap<>();

    private Listener listener = new Listener() {
        @Override
        public void onRegisterFolder(Path path) {
        }

        @Override
        public void onRemoveFolder(long folderId) {
        }

        @Override
        public void onScanFolder(long folderId) {
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
        super(owner, "フォルダ マネージャ", ModalityType.DOCUMENT_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(Theme.SURFACE);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        buildRoots();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(26);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new FolderRenderer());
        tree.addTreeSelectionListener(event -> updateSelection());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                if (event.getPath().getLastPathComponent() instanceof FileNode node) {
                    loadChildren(node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 1) {
                    toggleAt(event);
                }
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(Theme.SURFACE);
        details.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        currentName.setFont(Theme.font(Font.BOLD, 14));
        currentName.setForeground(Theme.TEXT);
        currentName.setAlignmentX(LEFT_ALIGNMENT);
        currentPath.setFont(Theme.font(Font.PLAIN, 11));
        currentPath.setForeground(Theme.MUTED);
        currentPath.setAlignmentX(LEFT_ALIGNMENT);
        details.add(currentName);
        details.add(Box.createVerticalStrut(3));
        details.add(currentPath);
        details.add(Box.createVerticalStrut(12));
        for (JButton button : new JButton[] {registerButton, scanButton, toggleButton, removeButton}) {
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setFont(Theme.font(Font.PLAIN, 12));
            button.setEnabled(false);
            details.add(button);
            details.add(Box.createVerticalStrut(6));
        }
        registerButton.addActionListener(event -> {
            FileNode node = selectedNode();
            if (node != null) {
                listener.onRegisterFolder(node.path);
            }
        });
        scanButton.addActionListener(event -> {
            Folder folder = selectedFolder();
            if (folder != null) {
                listener.onScanFolder(folder.id());
            }
        });
        toggleButton.addActionListener(event -> toggleSelected());
        removeButton.addActionListener(event -> {
            Folder folder = selectedFolder();
            if (folder != null) {
                listener.onRemoveFolder(folder.id());
            }
        });
        details.add(Box.createVerticalGlue());

        add(details, BorderLayout.EAST);

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
        JButton close = new JButton("閉じる");
        close.addActionListener(event -> dispose());
        buttons.add(close);
        south.add(scanChecks, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        setSize(new Dimension(980, 620));
        setLocationRelativeTo(owner);
    }

    private void buildRoots() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new Node(null, ""));
        for (File drive : File.listRoots()) {
            Path path = Path.of(drive.getPath());
            root.add(new FileNode(path, path.toString()));
        }
        tree.setModel(new DefaultTreeModel(root));
    }

    private void loadChildren(FileNode node) {
        if (node.loaded || node.loading || node.path == null) {
            return;
        }
        node.loading = true;
        treeLoader.execute(() -> {
            List<Path> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(node.path)) {
                for (Path child : stream) {
                    if (!Files.isDirectory(child)) {
                        continue;
                    }
                    String name = child.getFileName().toString();
                    if (name.startsWith(".") || Files.isHidden(child)) {
                        continue;
                    }
                    children.add(child);
                }
            } catch (IOException ignored) {
            }
            children.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
            SwingUtilities.invokeLater(() -> {
                node.loading = false;
                node.loaded = true;
                for (Path child : children) {
                    node.add(new FileNode(child, child.getFileName().toString()));
                }
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
                tree.expandPath(new TreePath(node.getPath()));
                tree.repaint();
            });
        });
    }

    @Override
    public void dispose() {
        treeLoader.shutdownNow();
        super.dispose();
    }

    public void setListener(Listener listener) {
        if (listener != null) {
            this.listener = listener;
        }
    }

    public void setFolders(List<Folder> folders, boolean startup, boolean sweep) {
        registered.clear();
        for (Folder folder : folders) {
            registered.put(PathUtil.key(folder.path()), folder);
        }
        startupScan.setSelected(startup);
        sweepScan.setSelected(sweep);
        updateSelection();
        tree.repaint();
    }

    private void updateSelection() {
        FileNode node = selectedNode();
        Folder folder = node == null ? null : registered.get(PathUtil.key(node.path));
        currentName.setText(node == null ? "フォルダを選択してください" : ((Node) node.getUserObject()).label());
        currentPath.setText(node == null ? " " : node.path.toString());
        boolean selected = node != null;
        registerButton.setEnabled(selected && folder == null);
        scanButton.setEnabled(folder != null);
        removeButton.setEnabled(folder != null && folder.explicit());
        toggleButton.setEnabled(folder != null);
        toggleButton.setText(folder != null && !folder.scanEnabled() ? "スキャンを有効化" : "スキャンを無効化");
    }

    /** Clicking the state icon (or just left of the label) toggles that folder. */
    private void toggleAt(java.awt.event.MouseEvent event) {
        int row = tree.getRowForLocation(event.getX(), event.getY());
        if (row < 0) {
            return;
        }
        TreePath path = tree.getPathForRow(row);
        java.awt.Rectangle bounds = tree.getRowBounds(row);
        if (path == null || bounds == null || !(path.getLastPathComponent() instanceof FileNode node)) {
            return;
        }
        int depth = Math.max(0, path.getPathCount() - 2);
        int iconLeft = bounds.x + depth * 20;
        if (event.getX() < iconLeft || event.getX() > iconLeft + 26) {
            return;
        }
        tree.setSelectionRow(row);
        Folder folder = node.path == null ? null : registered.get(PathUtil.key(node.path));
        if (folder == null) {
            listener.onRegisterFolder(node.path);
        } else {
            listener.onScanEnabled(folder.id(), !folder.scanEnabled());
        }
    }

    private void toggleSelected() {
        Folder folder = selectedFolder();
        if (folder != null) {
            listener.onScanEnabled(folder.id(), !folder.scanEnabled());
        }
    }

    private FileNode selectedNode() {
        Object value = tree.getLastSelectedPathComponent();
        return value instanceof FileNode node ? node : null;
    }

    private Folder selectedFolder() {
        FileNode node = selectedNode();
        return node == null ? null : registered.get(PathUtil.key(node.path));
    }

    private final class FolderRenderer extends DefaultTreeCellRenderer {

        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            FileNode node = (FileNode) value;
            setText(((Node) node.getUserObject()).label());
            Folder folder = node.path == null ? null : registered.get(PathUtil.key(node.path));
            if (folder == null) {
                setIcon(Icons.folder(14, Theme.MUTED));
            } else {
                setIcon(folder.scanEnabled() ? Icons.check(14, Theme.ACCENT) : Icons.close(14, Theme.DANGER));
            }
            setBackgroundSelectionColor(Theme.ACCENT_SOFT);
            setTextSelectionColor(Theme.TEXT);
            return this;
        }
    }
}
