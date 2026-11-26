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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.PhotoFilter.SortOrder;
import freja.scan.LibraryScanner;
import freja.store.Library;
import freja.store.LibraryTasks;
import freja.util.PathUtil;
import imagify.Imagify;
import laevatain.ARXButton;
import laevatain.ARXSearchField;

/**
 * The main application window.
 */
public final class MainWindow extends JFrame {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(MainWindow.class);

    private static final String SORT_PREFERENCE = "view.sortOrder";

    private static final String SELECTED_FOLDER_PREFERENCE = "view.selectedFolderPath";

    private static final String VIEW_MODE_PREFERENCE = "view.mode";

    private static final String SIDEBAR_DIVIDER_PREFERENCE = "view.sidebarDivider";

    private static final String INSPECTOR_VISIBLE_PREFERENCE = "view.inspectorVisible";

    private static final String SCAN_ON_STARTUP_PREFERENCE = "scan.onStartup";

    private static final String SCAN_SWEEP_PREFERENCE = "scan.sweep";

    /** How many thumbnails of the visible page are decoded before its first paint. */
    private static final int THUMBNAIL_WARMUP = 36;

    private final Library library;

    /** Serialises UI-originated catalog work off the EDT and delivers results back to it. */
    private final LibraryTasks tasks;

    private final LibraryScanner scanner;

    private final ThumbnailCache cache;

    private final PhotoGrid grid;

    private final Sidebar sidebar = new Sidebar();

    private final PhotoInspector inspector;

    private final ARXSearchField search;

    /**
     * The order applied to the whole view. In the folder view each folder header shows - and
     * changes - its own saved sort, and that order also becomes this value for the selected folder.
     */
    private SortOrder viewSort = preferredSort();

    private final JSlider zoom = new JSlider(120, 220, 170);

    private final JLabel statusMessage = new JLabel("準備完了");

    private final JLabel statusCount = new JLabel();

    private final JLabel selectionInfo = new JLabel();

    private final JProgressBar progress = new JProgressBar();

    private final CardLayout centerCards = new CardLayout();

    private final JPanel center = new JPanel(centerCards);

    private final JLabel welcomePath = new JLabel();

    private final JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("ダークモード", Theme.isDark());

    private final JCheckBoxMenuItem inspectorItem = new JCheckBoxMenuItem("インスペクター", true);

    private ScanFoldersDialog scanFoldersDialog;

    private JPanel panes;

    private SettingsPanel settingsPanel;

    private javax.swing.JSplitPane split;

    private javax.swing.JSplitPane sidebarSplit;

    /**
     * Read before the split pane fires initial layout events that would overwrite the preference.
     */
    private int savedSidebarDivider;

    private final Timer refreshTimer;

    /**
     * Loads and sorts the catalog off the event dispatch thread. Two threads let a search refresh
     * start while an unrelated full refresh (a finished scan, a sort change) is still running; the
     * generation counter below discards whichever result the user has already superseded.
     */
    private final ExecutorService refresher = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "freja-refresh");
        thread.setDaemon(true);
        return thread;
    });

    private long refreshGeneration;

    /** Everything a background refresh needs to hand back to the event dispatch thread. */
    private record RefreshData(LibraryStats stats, List<Photo> photos, List<Folder> visibleFolders, Sidebar.Mode mode, long folderId, PhotoGrid.GroupMode grouping, List<Folder> sections, SortOrder order, boolean fallback, boolean hasFolders) {
    }

    private Sidebar.Mode mode = Sidebar.Mode.ALL;

    private long folderId = -1;

    private Photo inspectorPhoto;

    private PhotoViewer viewer;

    private boolean refreshScheduled;

    private boolean shuttingDown;

    private volatile long uiHeartbeat = System.nanoTime();

    private volatile boolean uiWatchdogRunning = true;

    private boolean scrollToSelectedFolder;

    /** Tag identifiers selected in the toolbar filter; empty means no tag restriction. */
    private final List<Long> selectedTagIds = new ArrayList<>();

    private ARXButton tagFilterButton;

    public MainWindow(Library library, LibraryScanner scanner) {
        super("Freja");
        setIconImage(Icons.applicationImage());
        this.library = library;
        this.tasks = new LibraryTasks(library, SwingUtilities::invokeLater);
        tasks.setErrorHandler(error -> JOptionPane.showMessageDialog(this,
                "ライブラリ操作に失敗しました: " + error.getMessage(), "Freja", JOptionPane.ERROR_MESSAGE));
        this.scanner = scanner;
        this.cache = new ThumbnailCache(library);
        this.inspector = new PhotoInspector();
        this.grid = new PhotoGrid(cache);
        // Use the field's default search glyph so the toolbar and the settings panel match in size.
        this.search = new ARXSearchField("検索", null);
        search.setPreferredSize(new Dimension(300, 31));
        search.setMaximumSize(new Dimension(360, 31));
        search.onTextChanged(this::scheduleRefresh);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));

        refreshTimer = new Timer(320, event -> {
            refreshScheduled = false;
            refreshAll();
        });
        refreshTimer.setRepeats(false);
        Timer heartbeat = new Timer(500, event -> uiHeartbeat = System.nanoTime());
        heartbeat.start();
        Thread watchdog = new Thread(this::watchUiResponsiveness, "freja-ui-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

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
                // The scanner notifies once per folder. The refresh runs on a background thread and
                // the 320 ms debounce coalesces those notifications into a few updates per second,
                // so the grid grows progressively without overloading the UI.
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
        // The first refresh is started by restoreViewSelection() once the saved mode and folder
        // are known, so the grid never shows the first folder before jumping to the last one.
        restoreViewSelection();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel main = new JPanel(new BorderLayout());
        main.add(toolbar(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
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
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(1);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        main.add(split, BorderLayout.CENTER);
        main.add(statusBar(), BorderLayout.SOUTH);

        sidebar.setMinimumSize(new Dimension(180, 0));
        main.setMinimumSize(new Dimension(420, 0));
        sidebarSplit = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, sidebar, main);
        sidebarSplit.setBorder(BorderFactory.createEmptyBorder());
        sidebarSplit.setDividerSize(5);
        sidebarSplit.setResizeWeight(0.0);
        sidebarSplit.setContinuousLayout(true);
        sidebarSplit.setBackground(Theme.BORDER);
        savedSidebarDivider = PREFERENCES.getInt(SIDEBAR_DIVIDER_PREFERENCE, 246);
        sidebarSplit.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            int location = sidebarSplit.getDividerLocation();
            // The initial layout reports 0. Persisting that would destroy the saved position.
            if (location > 0 && sidebarSplit.isShowing()) {
                PREFERENCES.putInt(SIDEBAR_DIVIDER_PREFERENCE, location);
            }
        });
        panes = new JPanel(new java.awt.CardLayout());
        panes.add(sidebarSplit, "viewer");
        SettingsPanel[] holder = new SettingsPanel[1];
        settingsPanel = new SettingsPanel(new SettingsPanel.Listener() {
            @Override
            public void onFontChanged(float ratio) {
                Theme.scaleFonts(MainWindow.this, ratio);
                if (holder[0] != null) {
                    Theme.scaleFonts(holder[0], ratio);
                }
                repaint();
            }

            @Override
            public void onThemeChanged() {
                applyThemeUi();
            }

            @Override
            public void onManageFolders() {
                openScanFolders();
            }
        });
        holder[0] = settingsPanel;
        panes.add(settingsPanel, "settings");
        add(panes, BorderLayout.CENTER);
        add(activityBar(), BorderLayout.WEST);
        switchPane("viewer");

        setJMenuBar(menuBar());
        // Apply the saved visibility before the window is shown. Doing it in start() let a hidden
        // inspector appear for one frame and then disappear once the scanner had started.
        setInspectorVisible(PREFERENCES.getBoolean(INSPECTOR_VISIBLE_PREFERENCE, true));
    }

    private final List<ActivityButton> activityButtons = new ArrayList<>();

    /** The VS Code-style strip on the far left which switches between the main panes. */
    private JComponent activityBar() {
        JPanel bar = new JPanel();
        bar.setBackground(Theme.SIDEBAR);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setPreferredSize(new Dimension(48, 0));
        bar.add(Box.createVerticalStrut(8));
        activityButtons.add(new ActivityButton("viewer", Icons.grid(20, Theme.MUTED), "写真ライブラリ", () -> switchPane("viewer")));
        activityButtons.add(new ActivityButton("settings", Icons.settings(20, Theme.MUTED), "設定", () -> switchPane("settings")));
        for (ActivityButton button : activityButtons) {
            bar.add(button);
        }
        bar.add(Box.createVerticalGlue());
        return bar;
    }

    private void switchPane(String id) {
        ((java.awt.CardLayout) panes.getLayout()).show(panes, id);
        for (ActivityButton button : activityButtons) {
            button.setSelected(id.equals(button.id));
        }
        panes.revalidate();
        panes.repaint();
    }

    private final class ActivityButton extends JPanel {

        private final String id;

        private boolean selected;

        private boolean hovered;

        ActivityButton(String id, javax.swing.Icon icon, String tooltip, Runnable action) {
            this.id = id;
            setOpaque(true);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
            setMaximumSize(new Dimension(48, 48));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tooltip);
            add(new JLabel(icon), BorderLayout.CENTER);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    action.run();
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    hovered = true;
                    paintState();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    hovered = false;
                    paintState();
                }
            });
        }

        void setSelected(boolean value) {
            selected = value;
            paintState();
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            if (selected) {
                graphics.setColor(Theme.ACCENT);
                graphics.fillRect(0, 0, 3, getHeight());
            }
        }

        private void paintState() {
            setBackground(selected ? Theme.ACCENT_SOFT : hovered ? Theme.HOVER : Theme.SIDEBAR);
            repaint();
        }
    }

    private JComponent toolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), BorderFactory
                .createEmptyBorder(7, 12, 7, 14)));
        bar.setBackground(Theme.SIDEBAR);

        ARXButton add = new ARXButton("フォルダを追加", Icons.folder(16, Theme.MUTED), ARXButton.Variant.SECONDARY, this::openScanFolders);
        add.setFont(Theme.font(Font.PLAIN, 12));
        bar.add(add);
        bar.add(Box.createHorizontalStrut(6));

        ARXButton rescan = new ARXButton("再スキャン", Icons.refresh(15, Theme.MUTED), ARXButton.Variant.SECONDARY, () -> {
            scanner.scanAll();
            statusMessage.setText("再スキャンを開始しました");
        });
        rescan.setFont(Theme.font(Font.PLAIN, 12));
        rescan.setToolTipText("すべてのフォルダを再スキャン (F5)");
        bar.add(rescan);
        bar.add(Box.createHorizontalStrut(14));

        bar.add(search);
        bar.add(Box.createHorizontalStrut(6));

        tagFilterButton = new ARXButton("タグ", Icons.tag(14, Theme.MUTED), ARXButton.Variant.SECONDARY, this::showTagFilter);
        tagFilterButton.setFont(Theme.font(Font.PLAIN, 12));
        tagFilterButton.setToolTipText("タグで絞り込む");
        bar.add(tagFilterButton);
        bar.add(Box.createHorizontalGlue());
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

        ARXButton slideshow = new ARXButton("スライドショー", Icons.play(13, Theme.MUTED), ARXButton.Variant.SECONDARY, this::openSlideshow);
        slideshow.setFont(Theme.font(Font.PLAIN, 12));
        bar.add(slideshow);
        return bar;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), BorderFactory
                .createEmptyBorder(6, 14, 6, 14)));
        statusMessage.setFont(Theme.font(Font.PLAIN, 11));
        statusMessage.setForeground(Theme.MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(statusMessage);
        bar.add(left, BorderLayout.WEST);

        selectionInfo.setFont(Theme.font(Font.PLAIN, 11));
        selectionInfo.setForeground(Theme.MUTED);
        selectionInfo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        bar.add(selectionInfo, BorderLayout.CENTER);

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

        ARXButton add = new ARXButton("フォルダを追加", Icons.folder(17, Theme.MUTED), ARXButton.Variant.SECONDARY, this::openScanFolders);
        add.setFont(Theme.font(Font.PLAIN, 13));
        add.setAlignmentX(CENTER_ALIGNMENT);
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
        file.add(menuItem("監視フォルダ…", "control O", this::openScanFolders));
        file.add(menuItem("再スキャン", "F5", () -> scanner.scanAll()));
        file.add(menuItem("設定…", null, () -> switchPane("settings")));
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
        view.add(menuItem("タグを管理…", "control T", () -> new TagManager(this, tasks, this::tagsChanged).setVisible(true)));
        view.addSeparator();
        inspectorItem.setAccelerator(KeyStroke.getKeyStroke("control I"));
        inspectorItem.addActionListener(event -> setInspectorVisible(inspectorItem.isSelected()));
        view.add(inspectorItem);
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
                openScanFolders();
            }

            @Override
            public void onRescanFolder(long folderId) {
                scanner.scanFolder(folderId);
                statusMessage.setText("フォルダを再スキャンしています…");
            }

            @Override
            public void onSortFolder(long folderId, SortOrder order) {
                boolean applies = mode == Sidebar.Mode.FOLDER && MainWindow.this.folderId == folderId;
                tasks.setFolderSort(folderId, order, () -> {
                    if (applies) {
                        setViewSort(order);
                        refreshAll();
                    }
                });
            }

            @Override
            public void onRenameFolder(long folderId) {
                renameFolder(folderId);
            }

            @Override
            public void onRemoveFolder(long folderId) {
                removeFolder(folderId);
            }

            @Override
            public void onRemoveFolders(List<Long> folderIds) {
                removeFolders(folderIds);
            }

            @Override
            public void onDeleteFolderFromDisk(List<Long> folderIds) {
                deleteFolderFromDisk(folderIds);
            }

            @Override
            public void onRevealFolder(long folderId) {
                tasks.query(() -> library.folder(folderId), found -> {
                    if (found != null) {
                        found.ifPresent(folder -> reveal(folder.path()));
                    }
                });
            }

            @Override
            public void onEditFolderTags(long folderId) {
                java.awt.Point point = sidebar.getMousePosition();
                int x = point == null ? 24 : point.x;
                int y = point == null ? 24 : point.y;
                TagPicker.show(sidebar, x, y, tasks, folderId, MainWindow.this::tagsChanged);
            }

            @Override
            public void onManageTags() {
                new TagManager(MainWindow.this, tasks, MainWindow.this::tagsChanged).setVisible(true);
            }
        });

        grid.setOnSelection(photo -> {
            showInspector(photo);
            updateSelectionInfo();
        });
        grid.setOnStickyFolder(folderId -> {
            // Following the sticky header keeps the sidebar in sync while the grid is scrolled.
            // The preference is written by explicit selections and at exit, not here: this also
            // fires for every section a folder jump passes through on its way.
            sidebar.selectFolderSilently(folderId);
            mode = Sidebar.Mode.FOLDER;
            this.folderId = folderId;
        });
        grid.setOnTagFolder((folderId, point) -> tasks.query(() -> library.folder(folderId), found -> {
            if (found != null && found.isPresent()) {
                TagPicker.show(grid, point.x, point.y + 8, tasks, folderId, this::tagsChanged);
            }
        }));
        grid.setOnSortOrder(this::changeSortOrder);
        grid.setOnRemove(this::removeFromLibrary);
        grid.setOnDeleteFromDisk(this::deleteFromDisk);
        grid.setOnOpen(this::openViewer);
        grid.setOnContextMenu(this::showPhotoMenu);

        inspector.setListener(new PhotoInspector.Listener() {
            @Override
            public void onOpen(Photo photo) {
                openViewer(photo);
            }

            @Override
            public void onReveal(Photo photo) {
                reveal(photo.origin());
            }

            @Override
            public void onFavorite(Photo photo, boolean favorite) {
                setFavorite(photo, favorite);
            }

            @Override
            public void onRating(Photo photo, int rating) {
                tasks.setRating(photo.id(), rating, MainWindow.this::refreshAll);
            }

            @Override
            public void onCaption(Photo photo, String caption) {
                tasks.setCaption(photo.id(), caption, () -> {
                    inspectorPhoto = photo;
                    refreshAll();
                });
            }
        });

        zoom.addChangeListener(event -> savePreferences());
    }

    /**
     * Applies a sort order to one folder when the folder view is active, and to the whole view
     * otherwise. The folder header select is the only caller.
     *
     * @param targetFolder the folder to change, or -1 for the view-wide order
     * @param order the order to apply
     */
    private void changeSortOrder(long targetFolder, SortOrder order) {
        if (mode == Sidebar.Mode.FOLDER && targetFolder >= 0) {
            tasks.setFolderSort(targetFolder, order, () -> {
                if (mode == Sidebar.Mode.FOLDER && folderId == targetFolder) {
                    setViewSort(order);
                }
                refreshAll();
                savePreferences();
            });
        } else {
            PREFERENCES.put(SORT_PREFERENCE, order.name());
            setViewSort(order);
            refreshAll();
            savePreferences();
        }
    }

    /** Shows or hides the details panel and remembers the choice. */
    private void setInspectorVisible(boolean visible) {
        inspectorItem.setSelected(visible);
        inspector.setVisible(visible);
        if (visible) {
            if (split.getRightComponent() != inspector) {
                split.setRightComponent(inspector);
            }
            split.setDividerSize(1);
            if (split.getWidth() > 0) {
                split.setDividerLocation(Math.max(0, split.getWidth() - 308));
            }
        } else {
            // Moving the divider to the edge is not enough: JSplitPane clamps it to the right
            // component's minimum width, which leaves the hidden inspector's space reserved.
            split.setRightComponent(null);
            split.setDividerSize(0);
        }
        split.revalidate();
        split.repaint();
        PREFERENCES.putBoolean(INSPECTOR_VISIBLE_PREFERENCE, visible);
    }

    /**
     * Starts the background scanner.
     */
    public void start() {
        // scanner.start() reads the folder catalog, so it has to run on the task thread; the
        // onDone callback is already delivered on the EDT by LibraryTasks.
        boolean scanOnStartup = PREFERENCES.getBoolean(SCAN_ON_STARTUP_PREFERENCE, false);
        boolean sweep = PREFERENCES.getBoolean(SCAN_SWEEP_PREFERENCE, false);
        tasks.execute(() -> scanner.start(true, scanOnStartup, sweep), () -> {
            if (sidebarSplit != null && sidebarSplit.getWidth() > 0 && savedSidebarDivider > 0) {
                sidebarSplit.setDividerLocation(Math.max(180, savedSidebarDivider));
            }
            if (split != null && split.getWidth() > 0) {
                boolean visible = PREFERENCES.getBoolean(INSPECTOR_VISIBLE_PREFERENCE, true);
                if (visible != inspector.isVisible()) {
                    setInspectorVisible(visible);
                } else if (visible) {
                    // The divider can only be placed once the window has a width.
                    split.setDividerLocation(Math.max(0, split.getWidth() - 308));
                }
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
            // scanner.addFolder() writes to the catalog, so keep it on the task thread and carry
            // the result (or the validation error) back to the EDT.
            Folder[] added = new Folder[1];
            String[] error = new String[1];
            tasks.execute(() -> {
                try {
                    added[0] = scanner.addFolder(folder);
                } catch (IllegalArgumentException e) {
                    error[0] = e.getMessage();
                }
            }, () -> {
                if (added[0] != null) {
                    selectMode(Sidebar.Mode.FOLDER, added[0].id());
                } else if (error[0] != null) {
                    statusMessage.setText(error[0]);
                }
            });
        }
        refreshAll();
    }

    /** Renames the sidebar label only; the directory on disk is never touched. */
    private void renameFolder(long id) {
        tasks.query(() -> library.folder(id), found -> {
            if (found == null || found.isEmpty()) {
                return;
            }
            String name = (String) JOptionPane.showInputDialog(this, "フォルダの表示名", "名前を変更", JOptionPane
                    .QUESTION_MESSAGE, null, null, found.get().name());
            if (name == null) {
                return;
            }
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                statusMessage.setText("フォルダ名を入力してください");
                return;
            }
            tasks.renameFolder(id, trimmed, () -> {
                refreshAll();
                statusMessage.setText("フォルダ名を変更しました: " + trimmed);
            });
        });
    }

    private void openScanFolders() {
        if (scanFoldersDialog == null) {
            scanFoldersDialog = new ScanFoldersDialog(this);
            scanFoldersDialog.setListener(new ScanFoldersDialog.Listener() {
                @Override
                public void onRegisterFolder(Path path) {
                    registerFolder(path);
                }

                @Override
                public void onClearFolders() {
                    int answer = JOptionPane
                            .showConfirmDialog(MainWindow.this, "登録されているすべてのフォルダと、ライブラリに記録された写真データを削除します。\n" + "ディスク上の写真ファイル自体は削除されません。", "監視フォルダをクリア", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer != JOptionPane.OK_OPTION) {
                        return;
                    }
                    // scanner.clearFolders() calls library.clearFolders() internally.
                    tasks.execute(scanner::clearFolders, () -> {
                        refreshScanFolders();
                        refreshAll();
                        statusMessage.setText("監視フォルダをクリアしました");
                    });
                }

                @Override
                public void onRemoveFolders(List<Long> folderIds) {
                    removeFolders(folderIds);
                }

                @Override
                public void onScanFolders(List<Long> folderIds) {
                    for (Long id : folderIds) {
                        scanner.scanFolder(id);
                    }
                    statusMessage.setText(folderIds.size() + " 件のフォルダをスキャンしています…");
                }

                @Override
                public void onScanEnabled(long folderId, boolean enabled) {
                    tasks.setFolderScanEnabled(folderId, enabled, () -> {
                        refreshScanFolders();
                        refreshAll();
                    });
                }

                @Override
                public void onStartupScanChanged(boolean enabled) {
                    PREFERENCES.putBoolean(SCAN_ON_STARTUP_PREFERENCE, enabled);
                    try {
                        PREFERENCES.flush();
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onSweepChanged(boolean enabled) {
                    PREFERENCES.putBoolean(SCAN_SWEEP_PREFERENCE, enabled);
                    try {
                        PREFERENCES.flush();
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        refreshScanFolders();
        scanFoldersDialog.setVisible(true);
    }

    private void refreshScanFolders() {
        if (scanFoldersDialog == null) {
            return;
        }
        boolean scanOnStartup = PREFERENCES.getBoolean(SCAN_ON_STARTUP_PREFERENCE, false);
        boolean sweep = PREFERENCES.getBoolean(SCAN_SWEEP_PREFERENCE, false);
        tasks.query(library::folders, folders -> {
            if (scanFoldersDialog != null && folders != null) {
                scanFoldersDialog.setFolders(folders, scanOnStartup, sweep);
            }
        });
    }

    private void registerFolder(Path path) {
        // scanner.addFolder() writes to the catalog; carry the result back to the EDT.
        Folder[] added = new Folder[1];
        String[] error = new String[1];
        tasks.execute(() -> {
            try {
                added[0] = scanner.addFolder(path);
            } catch (IllegalArgumentException e) {
                error[0] = e.getMessage();
            }
        }, () -> {
            if (added[0] == null) {
                if (error[0] != null) {
                    statusMessage.setText(error[0]);
                }
                return;
            }
            statusMessage.setText(path + " をスキャンしています…");
            refreshScanFolders();
            refreshAll();
        });
    }

    private void removeFolder(long folderId) {
        tasks.query(() -> library.folder(folderId), found -> {
            if (found == null || found.isEmpty()) {
                return;
            }
            int answer = JOptionPane.showConfirmDialog(this, "「" + found.get()
                    .name() + "」と配下のサブフォルダをライブラリから削除します。\n写真ファイル自体は削除されません。", "フォルダを削除", JOptionPane
                    .OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                // User-initiated removal deletes the whole tree in one transaction and unregisters its watches.
                tasks.execute(() -> scanner.removeFolders(List.of(folderId)), () -> {
                    if (mode == Sidebar.Mode.FOLDER && this.folderId == folderId) {
                        mode = Sidebar.Mode.ALL;
                        this.folderId = -1;
                        scrollToSelectedFolder = false;
                        applySortForCurrentView();
                    }
                    refreshAll();
                });
            }
        });
    }

    /** Removes one or more folders (and their subfolders) from the catalog, keeping files on disk. */
    private void removeFolders(List<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }
        tasks.query(() -> folderIds.stream().map(id -> library.folder(id).map(Folder::name).orElse("ID " + id)).toList(), names -> {
            if (names == null || names.isEmpty()) {
                return;
            }
            String message = folderIds.size() == 1 ? "「" + names.get(0) + "」と配下のサブフォルダをライブラリから削除します。\n写真ファイル自体は削除されません。"
                    : "選択した " + folderIds.size() + " 件のフォルダと配下のサブフォルダをライブラリから削除します。\n写真ファイル自体は削除されません。";
            int answer = JOptionPane.showConfirmDialog(this, message, "フォルダを削除", JOptionPane
                    .OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
            // One transaction for roots and their descendants; never deleteFolders(), which is only
            // for pruning auto-folders. The scanner batch also drops the watch registrations.
            tasks.execute(() -> scanner.removeFolders(folderIds), () -> {
                if (folderIds.contains(this.folderId)) {
                    mode = Sidebar.Mode.ALL;
                    this.folderId = -1;
                    scrollToSelectedFolder = false;
                    applySortForCurrentView();
                }
                refreshScanFolders();
                refreshAll();
            });
        });
    }

    /** Deletes the file or directory behind each selected folder, including everything below it. */
    private void deleteFolderFromDisk(List<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }
        tasks.query(() -> {
            List<Folder> found = new ArrayList<>();
            for (long id : folderIds) {
                library.folder(id).ifPresent(found::add);
            }
            return found;
        }, folders -> {
            if (folders == null || folders.isEmpty()) {
                return;
            }
            String message;
            if (folders.size() == 1) {
                Folder folder = folders.getFirst();
                message = Files.isDirectory(folder.path())
                        ? "「" + folder.name() + "」とその中身をディスクから完全に削除します。元に戻せません。"
                        : "「" + folder.name() + "」をディスクから完全に削除します。元に戻せません。";
            } else {
                message = "選択した " + folders.size() + " 件のフォルダとその中身をディスクから完全に削除します。元に戻せません。";
            }
            int answer = JOptionPane.showConfirmDialog(this, message, "ディスクから削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }
            tasks.execute(() -> {
                List<Long> removed = new ArrayList<>();
                for (Folder folder : folders) {
                    try {
                        if (Files.isDirectory(folder.path())) {
                            deleteTree(folder.path());
                        } else {
                            Files.deleteIfExists(folder.path());
                        }
                        removed.add(folder.id());
                    } catch (Exception ignored) {
                        // A locked or already missing path stays in the catalog for now.
                    }
                }
                scanner.removeFolders(removed);
            }, () -> {
                if (folderIds.contains(this.folderId)) {
                    mode = Sidebar.Mode.ALL;
                    this.folderId = -1;
                    scrollToSelectedFolder = false;
                    applySortForCurrentView();
                }
                refreshScanFolders();
                refreshAll();
            });
        });
    }

    /** Removes a directory and everything inside it, files first so every directory ends up empty. */
    private void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void selectMode(Sidebar.Mode mode, long folderId) {
        if (this.mode == mode && this.folderId == folderId) {
            // Clicking the entry which is already selected is not a no-op: the grid has been
            // scrolled elsewhere in the meantime, and the click means "take me back there".
            // Ignoring it made the sidebar look unresponsive on most repeats.
            revisit();
            return;
        }
        boolean folderToFolder = this.mode == Sidebar.Mode.FOLDER && mode == Sidebar.Mode.FOLDER && folderId >= 0;
        this.mode = mode;
        this.folderId = folderId;
        this.scrollToSelectedFolder = mode == Sidebar.Mode.FOLDER && folderId >= 0;
        if (mode != Sidebar.Mode.FOLDER) {
            grid.clearPinnedFolder();
        }
        rememberSelection();
        long targetFolder = folderId;
        applySortForCurrentView(() -> {
            if (folderToFolder && grid.scrollToFolder(targetFolder)) {
                // The grid already contains every folder section. Moving between them only needs a
                // scroll; reloading and re-sorting the whole library would land one step behind the
                // click.
                scrollToSelectedFolder = false;
            } else {
                refreshAll();
            }
        });
    }

    /**
     * Returns the grid to what the sidebar already has selected: the folder's section, or the first
     * photo for a whole-library view. A folder whose section is missing - nothing scanned yet - is
     * reloaded instead, and the jump then follows the data, as it does for a new selection.
     */
    private void revisit() {
        if (mode == Sidebar.Mode.FOLDER && folderId >= 0) {
            scrollToSelectedFolder = true;
            if (grid.scrollToFolder(folderId)) {
                scrollToSelectedFolder = false;
            } else {
                refreshAll();
            }
            return;
        }
        grid.scrollToTop();
    }

    /**
     * Remembers the sidebar selection - the view mode and, for a folder, its path - so the next
     * launch opens the same place even when the catalog is rebuilt with fresh identifiers.
     */
    private void rememberSelection() {
        Sidebar.Mode requestedMode = mode;
        long requestedFolder = folderId;
        PREFERENCES.put(VIEW_MODE_PREFERENCE, requestedMode.name());
        if (requestedMode != Sidebar.Mode.FOLDER || requestedFolder < 0) {
            // The whole-library views win over an older folder: opening the previous folder after
            // quitting from "All photos" would not be what the window was showing.
            PREFERENCES.remove(SELECTED_FOLDER_PREFERENCE);
            return;
        }
        // The folder lookup is SQL, so it runs on the task thread and only the preference write
        // comes back to the EDT.
        tasks.query(() -> library.folder(requestedFolder), found -> {
            if (found != null && found.isPresent()) {
                PREFERENCES.put(SELECTED_FOLDER_PREFERENCE, found.get().path().toString());
            } else {
                PREFERENCES.remove(SELECTED_FOLDER_PREFERENCE);
            }
        });
    }

    /** Restores the selection saved by the previous launch, when it still exists. */
    private void restoreViewSelection() {
        Sidebar.Mode saved = Sidebar.Mode.ALL;
        String modeName = PREFERENCES.get(VIEW_MODE_PREFERENCE, "");
        if (modeName.isBlank()) {
            // A library saved before the mode existed always reopened the remembered folder.
            if (!PREFERENCES.get(SELECTED_FOLDER_PREFERENCE, "").isBlank()) {
                saved = Sidebar.Mode.FOLDER;
            }
        } else {
            try {
                saved = Sidebar.Mode.valueOf(modeName);
            } catch (IllegalArgumentException e) {
                saved = Sidebar.Mode.ALL;
            }
        }
        if (saved != Sidebar.Mode.FOLDER) {
            mode = saved;
            folderId = -1;
            refreshAll();
            return;
        }
        String path = PREFERENCES.get(SELECTED_FOLDER_PREFERENCE, "");
        if (path.isBlank()) {
            mode = Sidebar.Mode.ALL;
            folderId = -1;
            refreshAll();
            return;
        }
        // Catalog lookup happens on the task thread; the sidebar state changes back on the EDT.
        tasks.query(library::folders, folders -> {
            if (folders != null) {
                try {
                    String key = PathUtil.key(Path.of(path));
                    for (Folder folder : folders) {
                        if (PathUtil.key(folder.path()).equals(key)) {
                            mode = Sidebar.Mode.FOLDER;
                            folderId = folder.id();
                            scrollToSelectedFolder = true;
                            // The folder list already carries its sort, so there is no need for a
                            // second catalog round-trip before starting the first refresh.
                            setViewSort(folder.sort() == null ? SortOrder.NAME : folder.sort());
                            refreshAll();
                            return;
                        }
                    }
                } catch (RuntimeException e) {
                    // A stale or malformed preference is simply ignored.
                }
            }
            // The folder was removed from the library in the meantime: show the whole library.
            mode = Sidebar.Mode.ALL;
            folderId = -1;
            refreshAll();
        });
    }

    private void applySortForCurrentView() {
        applySortForCurrentView(null);
    }

    /** Reads the folder's sort off the EDT, then optionally continues on the EDT once it is set. */
    private void applySortForCurrentView(Runnable onApplied) {
        Sidebar.Mode requestedMode = mode;
        long requestedFolder = folderId;
        if (requestedMode != Sidebar.Mode.FOLDER) {
            setViewSort(preferredSort());
            if (onApplied != null) {
                onApplied.run();
            }
            return;
        }
        tasks.query(() -> library.folder(requestedFolder), found -> {
            // A newer selection may have arrived while the catalog was being read.
            if (requestedMode == mode && requestedFolder == folderId) {
                SortOrder order = found == null ? SortOrder.NAME : found.map(Folder::sort).orElse(SortOrder.NAME);
                setViewSort(order);
            }
            if (onApplied != null) {
                onApplied.run();
            }
        });
    }

    /** Records the order {@link #refreshAll()} should load the view with. */
    private void setViewSort(SortOrder order) {
        viewSort = order;
    }

    private SortOrder preferredSort() {
        try {
            return SortOrder.valueOf(PREFERENCES.get(SORT_PREFERENCE, SortOrder.NAME.name()));
        } catch (IllegalArgumentException e) {
            return SortOrder.NAME;
        }
    }

    private void showPhotoMenu(Photo photo, java.awt.Point point) {
        List<Photo> selected = grid.selectedPhotos();
        List<Photo> targets = selected.size() > 1 && selected.contains(photo) ? selected : List.of(photo);
        String suffix = targets.size() > 1 ? " (" + targets.size() + "枚)" : "";
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("開く" + suffix, Icons.image(14, Theme.MUTED));
        open.addActionListener(event -> openViewer(photo, targets));
        menu.add(open);
        JMenuItem favorite = new JMenuItem((photo.favorite() ? "お気に入りから外す" : "お気に入りに追加") + suffix,
                Icons.star(14, Theme.GOLD, photo.favorite()));
        favorite.addActionListener(event -> setFavorites(targets, !photo.favorite()));
        menu.add(favorite);
        menu.addSeparator();
        JMenuItem reveal = new JMenuItem("エクスプローラーで表示", Icons.folder(14, Theme.MUTED));
        reveal.addActionListener(event -> reveal(photo.origin()));
        menu.add(reveal);
        JMenuItem copyPath = new JMenuItem("パスをコピー" + suffix, Icons.clipboard(14, Theme.MUTED));
        copyPath.addActionListener(event -> copyPaths(targets));
        menu.add(copyPath);
        JMenuItem reimport = new JMenuItem("再読み込み" + suffix, Icons.refresh(14, Theme.MUTED));
        reimport.addActionListener(event -> {
            cache.clear();
            // scanner.reimport() writes to the catalog (invalidate/ensureFolder).
            tasks.execute(() -> {
                for (Photo target : targets) {
                    scanner.reimport(target);
                }
            }, () -> statusMessage.setText(targets.size() + " 枚を再読み込みしています…"));
        });
        menu.add(reimport);
        menu.addSeparator();
        JMenuItem remove = new JMenuItem("ライブラリから削除" + suffix, Icons.close(14, Theme.MUTED));
        remove.addActionListener(event -> removeFromLibrary(targets));
        menu.add(remove);
        JMenuItem deleteFromDisk = new JMenuItem("ディスクから削除" + suffix, Icons.trash(14, Theme.DANGER));
        deleteFromDisk.setEnabled(targets.stream().noneMatch(Photo::archived));
        deleteFromDisk.addActionListener(event -> deleteFromDisk(targets));
        menu.add(deleteFromDisk);
        menu.show(grid, point.x, point.y);
    }

    /** Removes catalog entries only; the files on disk are left alone. */
    private void removeFromLibrary(List<Photo> photos) {
        if (photos.isEmpty()) {
            return;
        }
        String description = photos.size() > 1 ? photos.size() + " 枚の写真をライブラリから削除します。" : photos.get(0).fileName() + " をライブラリから削除します。";
        int answer = JOptionPane
                .showConfirmDialog(this, description + "\n写真ファイル自体は削除されません。", "写真を削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        List<Long> ids = photos.stream().map(Photo::id).toList();
        tasks.deletePhotos(ids, () -> {
            inspectorPhoto = null;
            inspector.show(null);
            refreshAll();
        });
    }

    /** Removes the original files as well as their catalog entries. Cannot be undone. */
    private void deleteFromDisk(List<Photo> photos) {
        if (photos.isEmpty()) {
            return;
        }
        String description = photos.size() > 1 ? photos.size() + " 枚の画像ファイルをディスクから完全に削除します。"
                : photos.get(0).fileName() + " をディスクから完全に削除します。";
        int answer = JOptionPane
                .showConfirmDialog(this, description + "\n\nこの操作は取り消せません。ライブラリからも削除されます。", "ディスクから削除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        // File deletion and the batch catalog delete both run on the task thread.
        tasks.deleteFromDisk(photos, result -> {
            int deleted = result == null ? 0 : result.deleted();
            int failed = result == null ? photos.size() : result.failed();
            inspectorPhoto = null;
            inspector.show(null);
            refreshAll();
            statusMessage.setText(failed == 0 ? deleted + " 枚をディスクから削除しました" : deleted + " 枚を削除し、" + failed + " 枚は削除できませんでした");
        });
    }

    private void setFavorites(List<Photo> photos, boolean favorite) {
        List<Long> ids = photos.stream().map(Photo::id).toList();
        tasks.setFavorites(ids, favorite, () -> {
            Photo lead = photos.isEmpty() ? null : photos.get(0).withFavorite(favorite);
            inspectorPhoto = lead;
            inspector.show(lead);
            refreshAll();
        });
    }

    private void openViewer(Photo photo) {
        openViewer(photo, grid.photos());
    }

    private void openViewer(Photo photo, List<Photo> selection) {
        if (photo == null) {
            return;
        }
        if (viewer != null) {
            // A viewer waiting on its first decode is not displayable yet; it still has to go.
            viewer.dispose();
        }
        List<Photo> photos = selection == null || selection.isEmpty() ? grid.photos() : selection;
        if (photos.isEmpty()) {
            photos = List.of(photo);
        }
        viewer = PhotoViewer.open(this, photos, photo, new PhotoViewer.Listener() {
            @Override
            public void onFavorite(Photo updated, boolean favorite) {
                tasks.setFavorites(List.of(updated.id()), favorite, MainWindow.this::refreshAll);
            }

            @Override
            public void onClose(Photo lastViewed) {
                // Leave the grid focused on, and scrolled to, the photo that was on screen when the
                // viewer closed rather than the one it was opened with.
                grid.select(lastViewed);
            }
        }, this::viewerPlaceholder);
    }

    /**
     * Loads the placeholder image stored for an unreadable photo, so the viewer can show the same
     * picture as the grid instead of an error. Runs on the viewer's loader thread.
     *
     * @param photo a photo whose original could not be decoded
     * @return the decoded placeholder, or null when the catalog has none
     */
    private BufferedImage viewerPlaceholder(Photo photo) {
        try {
            return tasks.library().thumbnail(photo.id())
                    .map(thumbnail -> Imagify.read(thumbnail.data()).toBufferedImage())
                    .orElse(null);
        } catch (RuntimeException | IOError | LinkageError e) {
            return null;
        }
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
        tasks.setFavorites(List.of(photo.id()), favorite, () -> {
            Photo updated = photo.withFavorite(favorite);
            inspectorPhoto = updated;
            inspector.show(updated);
            refreshAll();
        });
    }

    private void copyPaths(List<Photo> photos) {
        if (photos.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (Photo photo : photos) {
            if (text.length() > 0) {
                text.append(System.lineSeparator());
            }
            text.append(photo.path());
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
            statusMessage.setText(photos.size() > 1 ? photos.size() + " 枚のパスをコピーしました" : "パスをコピーしました");
        } catch (Exception e) {
            statusMessage.setText("パスをコピーできませんでした: " + e.getMessage());
        }
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
        tasks.query(library::stats, stats -> {
            if (stats == null) {
                return;
            }
            JOptionPane.showMessageDialog(this, """
                    Freja 1.0
                    Picasa ライクなローカル写真ライブラリ

                    写真: %d 枚
                    お気に入り: %d 枚
                    最近の写真: %d 枚
                    フォルダ: %d
                    ライブラリ: %s

                    Java %s
                    """.formatted(stats.photos(), stats.favorites(), stats.recent(), stats.folders(), library.location(), System
                    .getProperty("java.version")), "Freja について", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void setDarkMode(boolean dark) {
        Theme.setDark(dark);
        applyThemeUi();
    }

    /** Refreshes the UI after the theme was changed elsewhere (e.g. the settings dialog). */
    private void applyThemeUi() {
        boolean dark = Theme.isDark();
        if (darkModeItem.isSelected() != dark) {
            darkModeItem.setSelected(dark);
        }
        // The settings page keeps its own view of the active theme: badges, the visible picks and
        // the current theme row.
        if (settingsPanel != null) {
            settingsPanel.refreshTheme();
        }
        // Theme.applyLookAndFeel() already calls FlatLaf.updateUI() for every window; doing it
        // again here double-installs UIs on custom renderers (the folder tree lost its rows).
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

    /** Opens the toolbar popup for choosing which tags a photo's folder must carry. */
    private void showTagFilter() {
        tasks.query(library::tags, tags -> {
            if (tags != null) {
                showTagFilterPopup(tags);
            }
        });
    }

    private void showTagFilterPopup(List<freja.model.Tag> tags) {
        JPopupMenu popup = new JPopupMenu();
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        panel.setPreferredSize(new Dimension(300, 360));

        ARXSearchField filter = new ARXSearchField("タグを検索", Icons.search(14, Theme.MUTED));
        panel.add(filter, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        List<JPanel> rows = new ArrayList<>();
        List<freja.model.Tag> rowTags = new ArrayList<>();
        for (freja.model.Tag tag : tags) {
            JCheckBox box = new JCheckBox(tag.name(), selectedTagIds.contains(tag.id()));
            box.setOpaque(false);
            box.setFont(Theme.font(Font.PLAIN, 13));
            box.setForeground(Theme.TEXT);
            box.addActionListener(event -> {
                if (box.isSelected()) {
                    selectedTagIds.add(tag.id());
                } else {
                    selectedTagIds.remove(tag.id());
                }
                updateTagFilterButton();
                refreshAll();
            });
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            row.add(box, BorderLayout.CENTER);
            JLabel count = new JLabel(tag.folderCount() + " フォルダ");
            count.setFont(Theme.font(Font.PLAIN, 11));
            count.setForeground(Theme.MUTED);
            row.add(count, BorderLayout.EAST);
            rows.add(row);
            rowTags.add(tag);
            list.add(row);
        }
        if (tags.isEmpty()) {
            JLabel empty = new JLabel("タグはまだありません");
            empty.setFont(Theme.font(Font.PLAIN, 12));
            empty.setForeground(Theme.MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
            list.add(empty);
        }
        Runnable applyFilter = () -> {
            String query = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setVisible(rowTags.get(i).name().toLowerCase(java.util.Locale.ROOT).contains(query));
            }
            list.revalidate();
            list.repaint();
        };
        filter.onTextChanged(applyFilter);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);

        ARXButton clear = new ARXButton("選択を解除", null, ARXButton.Variant.DANGER, () -> {
            selectedTagIds.clear();
            updateTagFilterButton();
            refreshAll();
            popup.setVisible(false);
        });
        clear.setFont(Theme.font(Font.PLAIN, 12));
        clear.setEnabled(!selectedTagIds.isEmpty());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(clear);
        panel.add(footer, BorderLayout.SOUTH);

        popup.add(panel);
        popup.show(tagFilterButton, 0, tagFilterButton.getHeight());
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
    }

    private void updateTagFilterButton() {
        tagFilterButton.setText(selectedTagIds.isEmpty() ? "タグ" : "タグ (" + selectedTagIds.size() + ")");
    }

    /** Called after tags or folder assignments changed in one of the tag dialogs. */
    private void tagsChanged() {
        tasks.query(library::tags, tags -> {
            List<Long> existing = tags == null ? List.of() : tags.stream().map(freja.model.Tag::id).toList();
            selectedTagIds.removeIf(id -> !existing.contains(id));
            updateTagFilterButton();
            cache.retryMissing();
            refreshAll();
        });
    }

    private void scheduleRefresh() {
        if (!refreshScheduled) {
            refreshScheduled = true;
        }
        refreshTimer.restart();
    }

    /**
     * Starts a refresh. Called on the event dispatch thread, so every piece of UI state is captured
     * here; the catalog is then read and sorted on the refresh thread and only the result comes
     * back
     * to the event dispatch thread.
     */
    private void refreshAll() {
        long generation = ++refreshGeneration;
        grid.setLoading(true);
        Sidebar.Mode requestedMode = mode;
        long requestedFolder = folderId;
        SortOrder order = viewSort;
        String searchText = search.getText();
        List<Long> tagIds = List.copyOf(selectedTagIds);
        refresher.execute(() -> {
            try {
                RefreshData data = loadView(requestedMode, requestedFolder, order, searchText, tagIds);
                SwingUtilities.invokeLater(() -> {
                    if (generation == refreshGeneration) {
                        grid.setLoading(false);
                        applyRefresh(data);
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == refreshGeneration) {
                        grid.setLoading(false);
                        statusMessage.setText("ライブラリを読み込めません: " + e.getMessage());
                    }
                });
            }
        });
    }

    /** Reads and sorts everything the current view needs. Never touches a Swing component. */
    private RefreshData loadView(Sidebar.Mode requestedMode, long requestedFolder, SortOrder order, String searchText, List<Long> tagIds) {
        List<Folder> folders = library.folders();
        LibraryStats stats = library.stats();
        PhotoFilter filter = switch (requestedMode) {
        case ALL, FOLDER -> PhotoFilter.all();
        case FAVORITES -> PhotoFilter.favorites();
        case RECENT -> PhotoFilter.recent();
        };
        filter = filter.withSort(order).withSearch(searchText).withTags(tagIds);

        List<Photo> photos = library.photos(filter);
        List<Folder> visibleFolders = folders.stream().filter(folder -> folder.photoCount() > 0).toList();
        if (filter.search() != null || !filter.tagIds().isEmpty()) {
            java.util.Set<Long> matched = new java.util.HashSet<>();
            for (Photo photo : photos) {
                matched.add(photo.folderId());
            }
            visibleFolders = folders.stream().filter(folder -> matched.contains(folder.id())).toList();
        }
        // A folder whose photos were removed falls back to the whole library. The view's sort is
        // reapplied on the event dispatch thread, which triggers another refresh with that order.
        boolean fallback = requestedMode == Sidebar.Mode.FOLDER && visibleFolders.stream()
                .noneMatch(folder -> folder.id() == requestedFolder);
        Sidebar.Mode effectiveMode = fallback ? Sidebar.Mode.ALL : requestedMode;
        long effectiveFolder = fallback ? -1 : requestedFolder;

        PhotoGrid.GroupMode grouping;
        List<Folder> sections = List.of();
        if (effectiveMode == Sidebar.Mode.ALL || effectiveMode == Sidebar.Mode.FOLDER) {
            sections = visibleFolders;
            photos = orderByFolder(photos, sections, effectiveMode == Sidebar.Mode.FOLDER);
            grouping = PhotoGrid.GroupMode.FOLDER;
        } else {
            grouping = filter.groupsByDate() ? PhotoGrid.GroupMode.DATE : PhotoGrid.GroupMode.NONE;
        }
        // Decode the screen which will actually be visible before the grid is installed on the EDT.
        // At startup that is the restored folder, which may be far below the first row.
        int warmFrom = 0;
        if (effectiveMode == Sidebar.Mode.FOLDER && effectiveFolder >= 0) {
            for (int i = 0; i < photos.size(); i++) {
                if (photos.get(i).folderId() == effectiveFolder) {
                    warmFrom = i;
                    break;
                }
            }
        }
        cache.warm(photos, warmFrom, THUMBNAIL_WARMUP);
        return new RefreshData(stats, photos, visibleFolders, effectiveMode, effectiveFolder, grouping, sections, order, fallback,
                !folders.isEmpty());
    }

    /** Installs a loaded view on the event dispatch thread. Never touches the catalog. */
    private void applyRefresh(RefreshData data) {
        if (data.fallback()) {
            mode = Sidebar.Mode.ALL;
            folderId = -1;
            scrollToSelectedFolder = false;
            applySortForCurrentView();
            refreshAll();
            return;
        }
        if (scrollToSelectedFolder) {
            // The folder which triggered this load wins over a sticky header reported while the
            // previous rows were still on screen; otherwise a background paint would overwrite the
            // selection before the scroll below runs.
            mode = data.mode();
            folderId = data.folderId();
            if (mode == Sidebar.Mode.FOLDER && folderId >= 0) {
                grid.pinFolder(folderId);
            }
        } else if (data.mode() != Sidebar.Mode.FOLDER) {
            grid.clearPinnedFolder();
        }
        grid.setHasFolders(data.hasFolders());
        grid.setPhotos(data.photos(), data.grouping(), data.sections(), data.order(), data.mode() == Sidebar.Mode.FOLDER);
        // Only folders with a matching photo are shown while searching.
        sidebar.setData(data.stats(), data.visibleFolders(), data.mode(), data.folderId());
        statusCount.setText(data.stats().photos() + " 枚  ·  " + Photo.formatSize(data.stats().bytes()));
        updateSelectionInfo();
        setTitle("Freja — " + data.stats().photos() + " 枚の写真");
        showInspector(grid.selectedPhoto());
        if (data.stats().folders() == 0 && data.mode() == Sidebar.Mode.ALL) {
            centerCards.show(center, "welcome");
        } else {
            centerCards.show(center, "grid");
        }
        if (scrollToSelectedFolder && data.mode() == Sidebar.Mode.FOLDER) {
            scrollToSelectedFolder = false;
            // Scroll before the first paint of this view: deferring it made the user see the top of
            // the library for one frame before the jump to the restored folder.
            grid.scrollToFolder(data.folderId());
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

    /** Shows the selected file's details, or the aggregate of a multi-selection. */
    private void updateSelectionInfo() {
        List<Photo> selected = grid.selectedPhotos();
        if (selected.isEmpty()) {
            selectionInfo.setText("");
            return;
        }
        if (selected.size() == 1) {
            Photo photo = selected.get(0);
            selectionInfo.setText(photo.fileName() + "  ·  " + photo.dimensionsLabel() + "  ·  " + photo
                    .capturedLabel() + "  ·  " + Photo.formatSize(photo.byteSize()));
        } else {
            long bytes = 0;
            for (Photo photo : selected) {
                bytes += photo.byteSize();
            }
            selectionInfo.setText(selected.size() + " 枚  ·  合計 " + Photo.formatSize(bytes));
        }
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
            detail += "  ·  " + formatDuration(state.elapsed());
            statusMessage.setText(shorten(detail, 110));
        } else {
            progress.setIndeterminate(false);
            progress.setVisible(false);
            if (state.errors() > 0) {
                statusMessage.setText(state.message() == null || state.message().isBlank() ? state.errors() + " 件のファイルはプレースホルダーとして取り込みました"
                        : shorten(state.message(), 110));
            } else if (state.imported() > 0) {
                statusMessage.setText(state.imported() + " 枚の写真を更新しました  (" + formatDuration(state.elapsed()) + ")");
            } else if (!state.elapsed().isZero()) {
                statusMessage.setText("スキャン完了  (" + formatDuration(state.elapsed()) + ")");
            } else {
                statusMessage.setText("準備完了");
            }
        }
    }

    /** Renders a scan duration in a form that stays readable from milliseconds to hours. */
    private static String formatDuration(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return (millis / 100) / 10 + "." + (millis / 100) % 10 + "秒";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) {
            return minutes + "分" + rest + "秒";
        }
        return minutes / 60 + "時間" + minutes % 60 + "分";
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
        zoom.setValue(PREFERENCES.getInt("view.thumbSize", 170));
        grid.setThumbSize(zoom.getValue());
        viewSort = preferredSort();
    }

    private void savePreferences() {
        // A folder jump animates through the sections between the old and the new selection. When
        // the window closes mid-glide the sticky header still names a folder on the way, so persist
        // the animation's destination instead: that is the folder the user picked.
        if (mode == Sidebar.Mode.FOLDER) {
            // A pinned selection survives the glide, even when the folder is too close to the end
            // of the library to reach the top of the viewport.
            long target = grid.scrollTargetFolder();
            if (target < 0) {
                target = grid.visibleFolderId();
            }
            if (target >= 0) {
                folderId = target;
            }
        }
        // Save the sidebar selection at exit: the folder the window was showing, or the library
        // view, so the next launch resumes exactly there.
        rememberSelection();
        PREFERENCES.putInt("view.thumbSize", zoom.getValue());
        if (mode != Sidebar.Mode.FOLDER) {
            PREFERENCES.put(SORT_PREFERENCE, viewSort.name());
        }
        if (sidebarSplit != null && sidebarSplit.getWidth() > 0 && sidebarSplit.getDividerLocation() > 0) {
            PREFERENCES.putInt(SIDEBAR_DIVIDER_PREFERENCE, sidebarSplit.getDividerLocation());
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

    /** Called when a newer instance takes over; shuts down on the event dispatch thread. */
    public void requestShutdown() {
        SwingUtilities.invokeLater(this::shutdown);
    }

    /** Logs every thread when the event dispatch thread stops responding, for hang diagnosis. */
    private void watchUiResponsiveness() {
        boolean dumped = false;
        while (uiWatchdogRunning) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long stalled = System.nanoTime() - uiHeartbeat;
            if (stalled > TimeUnit.SECONDS.toNanos(3)) {
                if (!dumped) {
                    dumped = true;
                    System.err.println("=== Freja UI stalled for " + TimeUnit.NANOSECONDS.toMillis(stalled) + " ms ===");
                    Thread.getAllStackTraces().forEach((thread, stack) -> {
                        System.err.println("\"" + thread.getName() + "\" " + thread.getState());
                        for (StackTraceElement frame : stack) {
                            System.err.println("    at " + frame);
                        }
                    });
                    System.err.println("=== end of thread dump ===");
                }
            } else {
                dumped = false;
            }
        }
    }

    private void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        uiWatchdogRunning = false;
        savePreferences();
        refresher.shutdownNow();
        if (viewer != null) {
            viewer.dispose();
        }
        // Close the UI on the event dispatch thread first: a scanner thread stuck in filesystem
        // I/O must never keep the window on screen.
        dispose();

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // halt() bypasses shutdown hooks; a blocked hook must not keep the process alive.
            Runtime.getRuntime().halt(0);
        }, "freja-exit-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        Thread cleanup = new Thread(() -> {
            try {
                tasks.close();
            } catch (RuntimeException ignored) {
            }
            try {
                scanner.close();
            } catch (RuntimeException ignored) {
            }
            try {
                cache.close();
            } catch (RuntimeException ignored) {
            }
            try {
                library.close();
            } catch (RuntimeException ignored) {
            }
            System.exit(0);
        }, "freja-cleanup");
        cleanup.setDaemon(false);
        cleanup.start();
    }
}
