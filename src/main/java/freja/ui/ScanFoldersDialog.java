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
import java.awt.Insets;
import java.awt.Rectangle;
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
import javax.swing.JSplitPane;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
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

    private final FolderRenderer folderRenderer = new FolderRenderer();

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
        tree.setCellRenderer(folderRenderer);
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
        JPanel treePanel = new JPanel(new BorderLayout(0, 6));
        treePanel.setOpaque(false);
        JLabel treeTitle = new JLabel("フォルダ");
        treeTitle.setFont(Theme.font(Font.BOLD, 13));
        treeTitle.setForeground(Theme.TEXT);
        treePanel.add(treeTitle, BorderLayout.NORTH);
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        treePanel.add(treeScroll, BorderLayout.CENTER);

        registeredList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        registeredList.setFont(Theme.font(Font.PLAIN, 13));
        registeredList.setBackground(Theme.SURFACE);
        registeredList.setForeground(Theme.TEXT);
        registeredList.setCellRenderer(new RegisteredRenderer());
        JPopupMenu actions = new JPopupMenu();
        JMenuItem scanNow = new JMenuItem("今すぐスキャン");
        scanNow.addActionListener(event -> {
            Folder folder = selectedRegisteredFolder();
            if (folder != null) {
                listener.onScanFolder(folder.id());
            }
        });
        JMenuItem remove = new JMenuItem("ライブラリから削除");
        remove.addActionListener(event -> {
            Folder folder = selectedRegisteredFolder();
            if (folder != null) {
                listener.onRemoveFolder(folder.id());
            }
        });
        actions.add(scanNow);
        actions.add(remove);
        registeredList.setComponentPopupMenu(actions);
        JScrollPane registeredScroll = new JScrollPane(registeredList);
        registeredScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JPanel registeredPanel = new JPanel(new BorderLayout(0, 6));
        registeredPanel.setOpaque(false);
        JLabel registeredTitle = new JLabel("登録済みフォルダ");
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

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, registeredPanel);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        split.setOpaque(false);
        add(split, BorderLayout.CENTER);

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
        Folder selected = selectedRegisteredFolder();
        long selectedId = selected == null ? -1 : selected.id();
        registered.clear();
        for (Folder folder : folders) {
            registered.put(PathUtil.key(folder.path()), folder);
        }
        startupScan.setSelected(startup);
        sweepScan.setSelected(sweep);

        List<Folder> sorted = new ArrayList<>(folders);
        sorted.sort(Comparator.comparing(folder -> folder.name() == null || folder.name().isBlank()
                ? folder.path().toString() : folder.name(), String.CASE_INSENSITIVE_ORDER));
        registeredModel.clear();
        for (Folder folder : sorted) {
            registeredModel.addElement(folder);
        }
        emptyHint.setVisible(sorted.isEmpty());
        if (selectedId == -1 || !selectRegisteredFolder(selectedId)) {
            updateSelection();
        }
        tree.repaint();
    }

    private void updateSelection() {
        FileNode node = selectedNode();
        Folder folder = node == null ? null : registered.get(PathUtil.key(node.path));
        if (folder == null) {
            registeredList.clearSelection();
        } else {
            selectRegisteredFolder(folder.id());
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

    /** Clicking the state icon toggles that folder. */
    private void toggleAt(java.awt.event.MouseEvent event) {
        int row = tree.getRowForLocation(event.getX(), event.getY());
        if (row < 0) {
            return;
        }
        TreePath path = tree.getPathForRow(row);
        if (path == null || !(path.getLastPathComponent() instanceof FileNode node)) {
            return;
        }
        if (!folderRenderer.isIconHit(path, event.getX())) {
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

    private FileNode selectedNode() {
        Object value = tree.getLastSelectedPathComponent();
        return value instanceof FileNode node ? node : null;
    }

    /** Renders a registered folder with its scan state and full path. */
    private static final class RegisteredRenderer extends DefaultListCellRenderer {

        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof Folder folder) {
                String name = folder.name() == null || folder.name().isBlank()
                        ? String.valueOf(folder.path().getFileName()) : folder.name();
                setText("<html><body style='width:230px'><b>" + escape(name) + "</b><br>"
                        + "<font color='#" + hex(Theme.MUTED) + "' size='-2'>"
                        + escape(folder.path().toString()) + "</font></body></html>");
                setIcon(folder.scanEnabled() ? Icons.check(14, Theme.ACCENT) : Icons.close(14, Theme.DANGER));
                setToolTipText(folder.path().toString());
            }
            return this;
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static String hex(Color color) {
            return String.format("%06x", color.getRGB() & 0xffffff);
        }
    }

    private final class FolderRenderer extends DefaultTreeCellRenderer {

        /** Small tolerance so the 14px icon stays easy to click. */
        private static final int HIT_PADDING = 3;

        private final Map<TreePath, Rectangle> iconBounds = new HashMap<>();

        private TreePath renderingPath;

        FolderRenderer() {
            // The icon must sit at the renderer's left inset for the hit box below to be valid.
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            FileNode node = (FileNode) value;
            renderingPath = new TreePath(node.getPath());
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

        /**
         * The tree positions this renderer (including indentation and expand/collapse handles) right
         * before painting it, so capture the real icon box there instead of guessing an offset.
         */
        @Override
        public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            // CellRendererPane resets the renderer to (-w, -h, 0, 0) after painting; ignore that.
            if (width <= 0 || height <= 0 || renderingPath == null) {
                return;
            }
            Icon icon = getIcon();
            if (icon == null) {
                iconBounds.remove(renderingPath);
                return;
            }
            Insets insets = getInsets();
            if (insets == null) {
                insets = new Insets(0, 0, 0, 0);
            }
            int contentHeight = Math.max(0, height - insets.top - insets.bottom);
            int iconY = y + insets.top + Math.max(0, (contentHeight - icon.getIconHeight()) / 2);
            iconBounds.put(renderingPath,
                    new Rectangle(x + insets.left, iconY, icon.getIconWidth(), icon.getIconHeight()));
        }

        boolean isIconHit(TreePath path, int mouseX) {
            Rectangle bounds = iconBounds.get(path);
            return bounds != null
                    && mouseX >= bounds.x - HIT_PADDING
                    && mouseX <= bounds.x + bounds.width + HIT_PADDING;
        }
    }
}
