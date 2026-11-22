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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.PhotoFilter.SortOrder;
import freja.scan.LibraryScanner;
import freja.store.Library;

/**
 * The main application window.
 */
public final class MainWindow extends JFrame {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(MainWindow.class);

    private static final String SORT_PREFERENCE = "view.sortOrder";

    private static final String LAST_FOLDER_PREFERENCE = "folder.lastDirectory";

    private final Library library;

    private final LibraryScanner scanner;

    private final ThumbnailCache cache;

    private final PhotoGrid grid;

    private final Sidebar sidebar = new Sidebar();

    private final PhotoInspector inspector;

    private final JTextField search = new JTextField();

    private final JComboBox<SortOrder> sort = new JComboBox<>(SortOrder.values());

    private final JSlider zoom = new JSlider(120, 300, 190);

    private final JLabel statusMessage = new JLabel("準備完了");

    private final JLabel statusCount = new JLabel();

    private final JProgressBar progress = new JProgressBar();

    private final CardLayout centerCards = new CardLayout();

    private final JPanel center = new JPanel(centerCards);

    private final JLabel welcomePath = new JLabel();

    private final JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("ダークモード", Theme.isDark());

    private JButton themeButton;

    private javax.swing.JSplitPane split;

    private final Timer refreshTimer;

    private final Timer searchTimer;

    private Sidebar.Mode mode = Sidebar.Mode.ALL;

    private long folderId = -1;

    private Photo inspectorPhoto;

    private PhotoViewer viewer;

    private boolean refreshScheduled;

    private boolean updatingSort;

    private boolean scrollToSelectedFolder;

    public MainWindow(Library library, LibraryScanner scanner) {
        super("Freja");
        this.library = library;
        this.scanner = scanner;
        this.cache = new ThumbnailCache(library);
        this.inspector = new PhotoInspector(cache);
        this.grid = new PhotoGrid(cache);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));

        refreshTimer = new Timer(320, event -> {
            refreshScheduled = false;
            refreshAll();
        });
        refreshTimer.setRepeats(false);
        searchTimer = new Timer(280, event -> refreshAll());
        searchTimer.setRepeats(false);

        buildUi();
        restorePreferences();
        wireActions();

        scanner.addListener(new LibraryScanner.Listener() {
            @Override
            public void onStateChanged(LibraryScanner.State state) {
                SwingUtilities.invokeLater(() -> applyState(state));
            }

            @Override
            public void onLibraryChanged() {
                cache.retryMissing();
                SwingUtilities.invokeLater(MainWindow.this::scheduleRefresh);
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });

        applyState(scanner.state());
        refreshAll();
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        add(sidebar, BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        main.add(toolbar(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.setBackground(Theme.BACKGROUND);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(42);

        JPanel content = new JPanel(new BorderLayout());
        content.add(scroll, BorderLayout.CENTER);
        center.add(content, "grid");
        center.add(welcome(), "welcome");

        inspector.setMinimumSize(new Dimension(280, 320));
        split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, center, inspector);
        split.setBorder(null);
        split.setDividerSize(1);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        main.add(split, BorderLayout.CENTER);
        main.add(statusBar(), BorderLayout.SOUTH);
        add(main, BorderLayout.CENTER);

        setJMenuBar(menuBar());
    }

    private JComponent toolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), BorderFactory.createEmptyBorder(7, 12, 7, 14)));
        bar.setBackground(Theme.SURFACE);

        JButton add = new JButton("フォルダを追加", Icons.folder(16, Theme.ACCENT));
        add.setFont(Theme.font(Font.BOLD, 12));
        add.addActionListener(event -> chooseFolder());
        bar.add(add);
        bar.add(Box.createHorizontalStrut(6));

        JButton rescan = new JButton("再スキャン", Icons.refresh(15, Theme.MUTED));
        rescan.setFont(Theme.font(Font.PLAIN, 12));
        rescan.setToolTipText("すべてのフォルダを再スキャン (F5)");
        rescan.addActionListener(event -> {
            scanner.scanAll();
            statusMessage.setText("再スキャンを開始しました");
        });
        bar.add(rescan);
        bar.add(Box.createHorizontalStrut(14));

        JPanel searchBox = new JPanel(new BorderLayout(6, 0));
        searchBox.setOpaque(false);
        searchBox.add(new JLabel(Icons.search(15, Theme.MUTED)), BorderLayout.WEST);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ファイル名・フォルダ名・カメラ・キャプションを検索");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.setPreferredSize(new Dimension(300, 31));
        search.setMaximumSize(new Dimension(360, 31));
        searchBox.add(search, BorderLayout.CENTER);
        bar.add(searchBox);
        bar.add(Box.createHorizontalGlue());

        sort.setRenderer(sortRenderer());
        sort.setPreferredSize(new Dimension(168, 31));
        sort.setMaximumSize(new Dimension(190, 31));
        sort.setToolTipText("並び順");
        bar.add(sort);
        bar.add(Box.createHorizontalStrut(14));

        zoom.setOpaque(false);
        zoom.setPreferredSize(new Dimension(118, 31));
        zoom.setMaximumSize(new Dimension(130, 31));
        zoom.setToolTipText("サムネイルサイズ");
        zoom.addChangeListener(event -> {
            grid.setThumbSize(zoom.getValue());
            zoom.setToolTipText("サムネイルサイズ: " + zoom.getValue() + " px");
        });
        bar.add(zoom);
        bar.add(Box.createHorizontalStrut(8));

        JButton slideshow = new JButton("スライドショー", Icons.play(13, Theme.MUTED));
        slideshow.setFont(Theme.font(Font.PLAIN, 12));
        slideshow.addActionListener(event -> openSlideshow());
        bar.add(slideshow);

        themeButton = new JButton(Theme.isDark() ? Icons.sun(16, Theme.MUTED) : Icons.moon(16, Theme.MUTED));
        themeButton.setToolTipText(Theme.isDark() ? "ライトモードに切り替え" : "ダークモードに切り替え");
        themeButton.setFocusable(false);
        themeButton.addActionListener(event -> setDarkMode(!Theme.isDark()));
        bar.add(Box.createHorizontalStrut(8));
        bar.add(themeButton);
        return bar;
    }

    private ListCellRenderer<? super SortOrder> sortRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof SortOrder order) {
                    setText(order.label());
                }
                return this;
            }
        };
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        statusMessage.setFont(Theme.font(Font.PLAIN, 11));
        statusMessage.setForeground(Theme.MUTED);
        bar.add(statusMessage, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        progress.setPreferredSize(new Dimension(140, 10));
        progress.setVisible(false);
        progress.setBorderPainted(false);
        right.add(progress);
        statusCount.setFont(Theme.font(Font.PLAIN, 11));
        statusCount.setForeground(Theme.MUTED);
        right.add(statusCount);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent welcome() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.BACKGROUND);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel icon = new JLabel(Icons.image(72, Theme.ACCENT));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        card.add(icon);
        card.add(Box.createVerticalStrut(26));

        JLabel title = new JLabel("写真をまとめて、すぐに見つける");
        title.setFont(Theme.font(Font.BOLD, 26));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(10));

        JLabel description = new JLabel("フォルダを登録すると、サムネイルとメタデータを自動で作成し、");
        description.setFont(Theme.font(Font.PLAIN, 13));
        description.setForeground(Theme.MUTED);
        description.setAlignmentX(CENTER_ALIGNMENT);
        card.add(description);
        JLabel description2 = new JLabel("その後の追加・変更・削除もバックグラウンドで追跡します。");
        description2.setFont(Theme.font(Font.PLAIN, 13));
        description2.setForeground(Theme.MUTED);
        description2.setAlignmentX(CENTER_ALIGNMENT);
        card.add(description2);
        card.add(Box.createVerticalStrut(26));

        JButton add = new JButton("フォルダを追加", Icons.folder(17, Color.WHITE));
        add.setFont(Theme.font(Font.BOLD, 13));
        add.putClientProperty(FlatClientProperties.STYLE, "background:#2E6BE6; foreground:#FFFFFF; borderWidth:0; arc:11; hoverBackground:#1F52B8; pressedBackground:#1A459B");
        add.setAlignmentX(CENTER_ALIGNMENT);
        add.addActionListener(event -> chooseFolder());
        card.add(add);
        card.add(Box.createVerticalStrut(22));

        welcomePath.setFont(Theme.font(Font.PLAIN, 10));
        welcomePath.setForeground(Theme.FAINT);
        welcomePath.setAlignmentX(CENTER_ALIGNMENT);
        welcomePath.setText("ライブラリ: " + shortenPath(library.location().toString(), 78));
        card.add(welcomePath);
        card.add(Box.createVerticalStrut(8));
        JLabel local = new JLabel("写真は移動・変更されません。メタデータはこのコンピューターの SQLite に保存されます。");
        local.setFont(Theme.font(Font.PLAIN, 10));
        local.setForeground(Theme.FAINT);
        local.setAlignmentX(CENTER_ALIGNMENT);
        card.add(local);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        outer.add(card, constraints);
        return outer;
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("ファイル");
        file.add(menuItem("フォルダを追加…", "control O", this::chooseFolder));
        file.add(menuItem("再スキャン", "F5", () -> scanner.scanAll()));
        file.addSeparator();
        file.add(menuItem("終了", "control Q", this::shutdown));
        bar.add(file);

        JMenu view = new JMenu("表示");
        view.add(menuItem("すべての写真", "control 1", () -> selectMode(Sidebar.Mode.ALL, -1)));
        view.add(menuItem("お気に入り", "control 2", () -> selectMode(Sidebar.Mode.FAVORITES, -1)));
        view.add(menuItem("最近の写真", "control 3", () -> selectMode(Sidebar.Mode.RECENT, -1)));
        view.addSeparator();
        view.add(menuItem("スライドショー", "F11", this::openSlideshow));
        view.add(menuItem("検索に移動", "control F", () -> {
            search.requestFocusInWindow();
            search.selectAll();
        }));
        view.addSeparator();
        darkModeItem.setAccelerator(KeyStroke.getKeyStroke("control shift D"));
        darkModeItem.addActionListener(event -> setDarkMode(darkModeItem.isSelected()));
        view.add(darkModeItem);
        bar.add(view);

        JMenu help = new JMenu("ヘルプ");
        help.add(menuItem("Freja について", null, this::about));
        bar.add(help);
        return bar;
    }

    private JMenuItem menuItem(String text, String accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(event -> action.run());
        return item;
    }

    private void wireActions() {
        sidebar.setListener(new Sidebar.Listener() {
            @Override
            public void onSelect(Sidebar.Mode mode, long folderId) {
                selectMode(mode, folderId);
            }

            @Override
            public void onAddFolder() {
                chooseFolder();
            }

            @Override
            public void onRescanFolder(long folderId) {
                scanner.scanFolder(folderId);
                statusMessage.setText("フォルダを再スキャンしています…");
            }

            @Override
            public void onSortFolder(long folderId, SortOrder sort) {
                library.setFolderSort(folderId, sort);
                if (mode == Sidebar.Mode.FOLDER && MainWindow.this.folderId == folderId) {
                    applySortForCurrentView();
                    refreshAll();
                }
            }

            @Override
            public void onRemoveFolder(long folderId) {
                removeFolder(folderId);
            }

            @Override
            public void onRevealFolder(long folderId) {
                library.folder(folderId).ifPresent(folder -> reveal(folder.path()));
            }
        });

        grid.setOnSelection(this::showInspector);
        grid.setOnOpen(this::openViewer);
        grid.setOnContextMenu(this::showPhotoMenu);

        inspector.setListener(new PhotoInspector.Listener() {
            @Override
            public void onOpen(Photo photo) {
                openViewer(photo);
            }

            @Override
            public void onReveal(Photo photo) {
                reveal(photo.path());
            }

            @Override
            public void onFavorite(Photo photo, boolean favorite) {
                setFavorite(photo, favorite);
            }

            @Override
            public void onRating(Photo photo, int rating) {
                library.setRating(photo.id(), rating);
                refreshAll();
            }

            @Override
            public void onCaption(Photo photo, String caption) {
                library.setCaption(photo.id(), caption);
                inspectorPhoto = photo;
                refreshAll();
            }
        });

        sort.addActionListener(event -> {
            if (updatingSort) {
                return;
            }
            SortOrder order = sort.getSelectedItem() instanceof SortOrder value ? value : SortOrder.NAME;
            if (mode == Sidebar.Mode.FOLDER && folderId >= 0) {
                library.setFolderSort(folderId, order);
            } else {
                PREFERENCES.put(SORT_PREFERENCE, order.name());
            }
            refreshAll();
            savePreferences();
        });
        zoom.addChangeListener(event -> savePreferences());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                searchTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                searchTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                searchTimer.restart();
            }
        });
    }

    /**
     * Starts the background scanner.
     */
    public void start() {
        scanner.start();
        SwingUtilities.invokeLater(() -> {
            if (split != null && split.getWidth() > 0) {
                split.setDividerLocation(0.78);
            }
        });
    }

    /**
     * Adds folders, typically from the command line.
     *
     * @param folders the folders
     */
    public void addFolders(List<Path> folders) {
        for (Path folder : folders) {
            try {
                Folder added = scanner.addFolder(folder);
                selectMode(Sidebar.Mode.FOLDER, added.id());
            } catch (IllegalArgumentException e) {
                statusMessage.setText(e.getMessage());
            }
        }
        refreshAll();
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("写真フォルダを選択");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setCurrentDirectory(lastFolderDirectory());
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected == null) {
                return;
            }
            rememberFolderLocation(selected);
            try {
                Folder folder = scanner.addFolder(selected.toPath());
                selectMode(Sidebar.Mode.FOLDER, folder.id());
                refreshAll();
                statusMessage.setText(folder.path() + " をスキャンしています…");
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "フォルダを追加できません", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            // Remember where the user browsed even when they close the dialog without choosing.
            rememberFolderLocation(chooser.getCurrentDirectory());
        }
    }

    private File lastFolderDirectory() {
        String remembered = PREFERENCES.get(LAST_FOLDER_PREFERENCE, "");
        if (!remembered.isBlank()) {
            File directory = new File(remembered);
            while (directory != null && !directory.isDirectory()) {
                directory = directory.getParentFile();
            }
            if (directory != null) {
                return directory;
            }
        }
        return new File(System.getProperty("user.home"));
    }

    private void rememberFolderLocation(File location) {
        if (location == null) {
            return;
        }
        File directory = location.isDirectory() ? location : location.getParentFile();
        if (directory != null) {
            PREFERENCES.put(LAST_FOLDER_PREFERENCE, directory.getAbsolutePath());
            try {
                PREFERENCES.flush();
            } catch (Exception ignored) {
            }
        }
    }

    private void removeFolder(long folderId) {
        Optional<Folder> folder = library.folder(folderId);
        if (folder.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "「" + folder.get().name() + "」と配下のサブフォルダをライブラリから削除します。\n写真ファイル自体は削除されません。", "フォルダを削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            scanner.removeFolder(folderId);
            if (mode == Sidebar.Mode.FOLDER && this.folderId == folderId) {
                mode = Sidebar.Mode.ALL;
                this.folderId = -1;
                scrollToSelectedFolder = false;
                applySortForCurrentView();
            }
            refreshAll();
        }
    }

    private void selectMode(Sidebar.Mode mode, long folderId) {
        this.mode = mode;
        this.folderId = folderId;
        this.scrollToSelectedFolder = mode == Sidebar.Mode.FOLDER && folderId >= 0;
        applySortForCurrentView();
        refreshAll();
    }

    private void applySortForCurrentView() {
        SortOrder order = mode == Sidebar.Mode.FOLDER ? library.folder(folderId).map(Folder::sort).orElse(SortOrder.NAME) : preferredSort();
        updatingSort = true;
        try {
            sort.setSelectedItem(order);
        } finally {
            updatingSort = false;
        }
        sort.setToolTipText(mode == Sidebar.Mode.FOLDER ? "このフォルダの表示順" : "表示順");
    }

    private SortOrder preferredSort() {
        try {
            return SortOrder.valueOf(PREFERENCES.get(SORT_PREFERENCE, SortOrder.NAME.name()));
        } catch (IllegalArgumentException e) {
            return SortOrder.NAME;
        }
    }

    private void showPhotoMenu(Photo photo, java.awt.Point point) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("開く");
        open.addActionListener(event -> openViewer(photo));
        menu.add(open);
        JMenuItem favorite = new JMenuItem(photo.favorite() ? "お気に入りから外す" : "お気に入りに追加");
        favorite.addActionListener(event -> setFavorite(photo, !photo.favorite()));
        menu.add(favorite);
        menu.addSeparator();
        JMenuItem reveal = new JMenuItem("エクスプローラーで表示");
        reveal.addActionListener(event -> reveal(photo.path()));
        menu.add(reveal);
        JMenuItem reimport = new JMenuItem("再読み込み");
        reimport.addActionListener(event -> {
            cache.clear();
            scanner.reimport(photo);
            statusMessage.setText(photo.fileName() + " を再読み込みしています…");
        });
        menu.add(reimport);
        menu.addSeparator();
        JMenuItem remove = new JMenuItem("ライブラリから削除");
        remove.addActionListener(event -> {
            int answer = JOptionPane.showConfirmDialog(this, photo.fileName() + " をライブラリから削除します。\n写真ファイル自体は削除されません。", "写真を削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                library.deletePhoto(photo.id());
                inspectorPhoto = null;
                inspector.show(null);
                refreshAll();
            }
        });
        menu.add(remove);
        menu.show(grid, point.x, point.y);
    }

    private void openViewer(Photo photo) {
        if (photo == null) {
            return;
        }
        if (viewer != null && viewer.isDisplayable()) {
            viewer.dispose();
        }
        List<Photo> photos = grid.photos();
        if (photos.isEmpty()) {
            photos = List.of(photo);
        }
        viewer = PhotoViewer.open(this, photos, photo, (updated, favorite) -> {
            library.setFavorite(updated.id(), favorite);
            refreshAll();
        });
    }

    private void openSlideshow() {
        List<Photo> photos = grid.photos();
        if (photos.isEmpty()) {
            statusMessage.setText("スライドショーにする写真がありません");
            return;
        }
        Photo start = grid.selectedPhoto() == null ? photos.get(0) : grid.selectedPhoto();
        openViewer(start);
        if (viewer != null) {
            viewer.startSlideshow();
        }
    }

    private void setFavorite(Photo photo, boolean favorite) {
        library.setFavorite(photo.id(), favorite);
        Photo updated = photo.withFavorite(favorite);
        inspectorPhoto = updated;
        inspector.show(updated);
        refreshAll();
    }

    private void reveal(Path path) {
        try {
            Path target = java.nio.file.Files.isDirectory(path) ? path : path.getParent();
            if (target != null) {
                Desktop.getDesktop().open(target.toFile());
            }
        } catch (Exception e) {
            statusMessage.setText("フォルダを開けません: " + e.getMessage());
        }
    }

    private void about() {
        LibraryStats stats = library.stats();
        JOptionPane.showMessageDialog(this, """
                Freja 1.0
                Picasa ライクなローカル写真ライブラリ

                写真: %d 枚
                お気に入り: %d 枚
                最近の写真: %d 枚
                フォルダ: %d
                ライブラリ: %s

                Java %s
                """.formatted(stats.photos(), stats.favorites(), stats.recent(), stats.folders(), library.location(), System.getProperty("java.version")), "Freja について", JOptionPane.INFORMATION_MESSAGE);
    }

    private void setDarkMode(boolean dark) {
        Theme.setDark(dark);
        if (darkModeItem.isSelected() != dark) {
            darkModeItem.setSelected(dark);
        }
        if (themeButton != null) {
            themeButton.setIcon(dark ? Icons.sun(16, Theme.MUTED) : Icons.moon(16, Theme.MUTED));
            themeButton.setToolTipText(dark ? "ライトモードに切り替え" : "ダークモードに切り替え");
        }
        SwingUtilities.updateComponentTreeUI(this);
        grid.repaint();
        sidebar.repaint();
        inspector.repaint();
        repaint();
    }

    /**
     * Switches between light and dark mode.
     */
    public void toggleDarkMode() {
        setDarkMode(!Theme.isDark());
    }

    private void scheduleRefresh() {
        if (!refreshScheduled) {
            refreshScheduled = true;
        }
        refreshTimer.restart();
    }

    private void refreshAll() {
        try {
            List<Folder> folders = library.folders();
            LibraryStats stats = library.stats();
            if (mode == Sidebar.Mode.FOLDER && folders.stream().noneMatch(folder -> folder.id() == folderId)) {
                mode = Sidebar.Mode.ALL;
                folderId = -1;
                scrollToSelectedFolder = false;
                applySortForCurrentView();
            }
            PhotoFilter filter = switch (mode) {
            case ALL, FOLDER -> PhotoFilter.all();
            case FAVORITES -> PhotoFilter.favorites();
            case RECENT -> PhotoFilter.recent();
            };
            SortOrder order = sort.getSelectedItem() instanceof SortOrder value ? value : SortOrder.NAME;
            filter = filter.withSort(order).withSearch(search.getText());

            List<Photo> photos = library.photos(filter);
            PhotoGrid.GroupMode grouping;
            List<Folder> sections = List.of();
            if (mode == Sidebar.Mode.ALL || mode == Sidebar.Mode.FOLDER) {
                if (filter.search() == null) {
                    sections = folders;
                } else {
                    java.util.Set<Long> matched = new java.util.HashSet<>();
                    for (Photo photo : photos) {
                        matched.add(photo.folderId());
                    }
                    sections = folders.stream().filter(folder -> matched.contains(folder.id())).toList();
                }
                photos = orderByFolder(photos, sections, mode == Sidebar.Mode.FOLDER);
                grouping = PhotoGrid.GroupMode.FOLDER;
            } else {
                grouping = filter.groupsByDate() ? PhotoGrid.GroupMode.DATE : PhotoGrid.GroupMode.NONE;
            }
            grid.setPhotos(photos, grouping, sections);
            sidebar.setData(stats, folders, mode, folderId);
            statusCount.setText(stats.photos() + " 枚  ·  " + Photo.formatSize(stats.bytes()));
            setTitle("Freja — " + stats.photos() + " 枚の写真");

            Photo selected = grid.selectedPhoto();
            if (selected == null) {
                showInspector(null);
            } else {
                showInspector(selected);
            }

            if (stats.folders() == 0 && mode == Sidebar.Mode.ALL) {
                centerCards.show(center, "welcome");
            } else {
                centerCards.show(center, "grid");
            }

            if (scrollToSelectedFolder && mode == Sidebar.Mode.FOLDER) {
                scrollToSelectedFolder = false;
                long target = folderId;
                SwingUtilities.invokeLater(() -> grid.scrollToFolder(target));
            }
        } catch (RuntimeException e) {
            statusMessage.setText("ライブラリを読み込めません: " + e.getMessage());
        }
    }

    private List<Photo> orderByFolder(List<Photo> photos, List<Folder> folders, boolean perFolderSort) {
        if (photos.isEmpty()) {
            return photos;
        }
        Map<Long, List<Photo>> grouped = new HashMap<>();
        for (Photo photo : photos) {
            grouped.computeIfAbsent(photo.folderId(), id -> new ArrayList<>()).add(photo);
        }
        List<Photo> ordered = new ArrayList<>(photos.size());
        for (Folder folder : folders) {
            List<Photo> list = grouped.remove(folder.id());
            if (list != null) {
                if (perFolderSort) {
                    list.sort(comparator(folder.sort()));
                }
                ordered.addAll(list);
            }
        }
        for (List<Photo> list : grouped.values()) {
            ordered.addAll(list);
        }
        return ordered;
    }

    private Comparator<Photo> comparator(SortOrder order) {
        Comparator<Photo> name = Comparator.comparing(Photo::fileName, String.CASE_INSENSITIVE_ORDER);
        return switch (order) {
        case NAME -> name.thenComparing(Comparator.comparingLong(Photo::capturedAt).reversed());
        case DATE_OLDEST -> Comparator.comparingLong(Photo::capturedAt).thenComparing(name);
        case SIZE -> Comparator.comparingLong(Photo::byteSize).reversed().thenComparing(name);
        default -> Comparator.comparingLong(Photo::capturedAt).reversed().thenComparing(name);
        };
    }

    private void showInspector(Photo photo) {
        if (photo == null) {
            if (inspectorPhoto != null) {
                inspectorPhoto = null;
                inspector.show(null);
            }
            return;
        }
        if (inspectorPhoto != null && inspectorPhoto.id() == photo.id() && inspectorPhoto.equals(photo)) {
            return;
        }
        inspectorPhoto = photo;
        inspector.show(photo);
    }

    private void applyState(LibraryScanner.State state) {
        progress.setVisible(state.scanning());
        if (state.scanning()) {
            if (state.total() > 0) {
                progress.setIndeterminate(false);
                progress.setMaximum(Math.max(1, state.total()));
                progress.setValue(Math.min(state.total(), state.processed()));
            } else {
                progress.setIndeterminate(true);
            }
            String detail = state.message() == null || state.message().isBlank() ? "スキャン中" : state.message();
            if (state.total() > 0) {
                detail += "  (" + state.processed() + " / " + state.total() + ")";
            }
            statusMessage.setText(shorten(detail, 110));
        } else {
            progress.setIndeterminate(false);
            progress.setVisible(false);
            if (state.errors() > 0) {
                statusMessage.setText(state.message() == null || state.message().isBlank() ? state.errors() + " 件のファイルを読み込めませんでした" : shorten(state.message(), 110));
            } else if (state.imported() > 0) {
                statusMessage.setText(state.imported() + " 枚の写真を更新しました");
            } else {
                statusMessage.setText("準備完了");
            }
        }
    }

    private static String shorten(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String shortenPath(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int side = Math.max(8, (max - 1) / 2);
        return text.substring(0, side) + "…" + text.substring(text.length() - side);
    }

    private void restorePreferences() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(PREFERENCES.getInt("window.width", 1420), Math.max(980, screen.width - 40));
        int height = Math.min(PREFERENCES.getInt("window.height", 880), Math.max(640, screen.height - 60));
        int x = PREFERENCES.getInt("window.x", Integer.MIN_VALUE);
        int y = PREFERENCES.getInt("window.y", Integer.MIN_VALUE);
        Rectangle bounds = new Rectangle(x, y, width, height);
        if (x == Integer.MIN_VALUE || !screen.intersects(bounds)) {
            setSize(width, height);
            setLocationRelativeTo(null);
        } else {
            setBounds(bounds);
        }
        if (PREFERENCES.getBoolean("window.maximized", false)) {
            setExtendedState(MAXIMIZED_BOTH);
        }
        zoom.setValue(PREFERENCES.getInt("view.thumbSize", 190));
        grid.setThumbSize(zoom.getValue());
        try {
            sort.setSelectedItem(SortOrder.valueOf(PREFERENCES.get(SORT_PREFERENCE, SortOrder.NAME.name())));
        } catch (IllegalArgumentException e) {
            sort.setSelectedItem(SortOrder.NAME);
        }
        sort.setToolTipText("表示順");
    }

    private void savePreferences() {
        PREFERENCES.putInt("view.thumbSize", zoom.getValue());
        if (mode != Sidebar.Mode.FOLDER) {
            PREFERENCES.put(SORT_PREFERENCE, (sort.getSelectedItem() instanceof SortOrder order ? order : SortOrder.NAME).name());
        }
        PREFERENCES.putBoolean("window.maximized", (getExtendedState() & MAXIMIZED_BOTH) != 0);
        if ((getExtendedState() & MAXIMIZED_BOTH) == 0) {
            PREFERENCES.putInt("window.x", getX());
            PREFERENCES.putInt("window.y", getY());
            PREFERENCES.putInt("window.width", getWidth());
            PREFERENCES.putInt("window.height", getHeight());
        }
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }

    private void shutdown() {
        savePreferences();
        if (viewer != null) {
            viewer.dispose();
        }
        scanner.close();
        cache.close();
        library.close();
        dispose();
        System.exit(0);
    }
}
