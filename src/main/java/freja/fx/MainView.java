/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.controlsfx.control.PopOver.ArrowLocation;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.PhotoFilter.SortOrder;
import freja.model.Tag;
import freja.scan.LibraryScanner;
import freja.store.Library;
import freja.util.PathUtil;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import kiss.Variable;
import stylist.Style;
import stylist.StyleDSL;
import viewtify.Theme;
import viewtify.Viewtify;
import viewtify.preference.Preferences;
import viewtify.ui.UIButton;
import viewtify.ui.UIComboBox;
import viewtify.ui.UIContextMenu;
import viewtify.ui.UIHBox;
import viewtify.ui.UILabel;
import viewtify.ui.UIProgressBar;
import viewtify.ui.UISlider;
import viewtify.ui.UISplitPane;
import viewtify.ui.UIStackPane;
import viewtify.ui.UIText;
import viewtify.ui.UIVBox;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;
import viewtify.ui.view.AppearanceSetting;

/**
 * The main application window of the JavaFX edition: the Viewtify counterpart of the Swing
 * {@code MainWindow}. Catalog access, scanning and the refresh pipeline mirror the original; the
 * chrome is built from nested Views, reactive variables and {@code Viewtify.dialog} instead of
 * Swing containers.
 */
public final class MainView extends View {

    /** The active instance, used by the single-instance watcher. */
    private static volatile MainView instance;

    /** @return the running main view, or null before it is ready */
    public static MainView current() {
        return instance;
    }

    /** Which page the activity bar shows. */
    private enum Page {
        VIEWER, SETTINGS
    }

    /** Everything a background refresh hands back to the UI thread. */
    private record RefreshData(LibraryStats stats, List<Photo> photos, List<Folder> visibleFolders, SidebarView.Mode mode, long folderId, List<PhotoGridView.Section> sections, boolean fallback) {
    }

    private Library library;

    private LibraryScanner scanner;

    private ThumbnailStore thumbs;

    private final FrejaSetting setting = Preferences.of(FrejaSetting.class);

    private final AppearanceSetting appearance = Preferences.of(AppearanceSetting.class);

    private final Variable<Page> page = Variable.of(Page.VIEWER);

    private final Variable<Boolean> libraryEmpty = Variable.of(true);

    private final Variable<LibraryScanner.State> scannerState = Variable.empty();

    private final Variable<Object> refreshTick = Variable.empty();

    /** The child views; built explicitly to keep dependency injection out of the loop. */
    private SidebarView sidebar = new SidebarView();

    private PhotoGridView grid = new PhotoGridView();

    private InspectorView inspector = new InspectorView();

    private SettingsView settings = new SettingsView(this);

    private UIHBox contentRow;

    private UIStackPane pageStack;

    private UILabel photoPageButton;

    private UILabel settingsPageButton;

    private UISplitPane outerSplit;

    private UISplitPane innerSplit;

    private UIStackPane gridStack;

    private UIVBox centerColumn;

    private UIHBox toolbar;

    private UIVBox statusbar;

    private UIText<String> search;

    private UIComboBox<SortOrder> sort;

    private UISlider zoom;

    private UIButton addFolder;

    private UIButton rescan;

    private UIButton tagFilter;

    private UIButton slideshow;

    private UILabel themeToggle;

    private UILabel statusMessage;

    private UILabel statusCount;

    private UILabel selectionInfo;

    private UIProgressBar progress;

    private Node welcomeCard;

    /** Navigation state, mirroring the Swing window. */
    private SidebarView.Mode mode = SidebarView.Mode.ALL;

    private long folderId = -1;

    private final List<Long> selectedTagIds = new ArrayList<>();

    private final List<Path> pendingFolders = new ArrayList<>();

    private long refreshGeneration;

    private boolean suppressSortEvents;

    /**
     * Holder for the open viewer window. Field-typed as a plain class because Viewtify's buildUI
     * reflectively instantiates any null field whose type extends {@link View}.
     */
    private static final class Window1 {

        private PhotoViewerView value;
    }

    /** Holder for the lazily created watched-folder dialog, see {@link Window1}. */
    private static final class Window2 {

        private ScanFoldersView value;
    }

    private final Window1 viewer = new Window1();

    /** Guards against a second open triggered by the same double click. */
    private boolean viewerOpening;

    private final Window2 scanFoldersDialog = new Window2();

    private boolean scrollToSelectedFolder;

    private boolean shuttingDown;

    /** @return the open catalog, for dialogs which are instantiated reflectively */
    public Library library() {
        return library;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, style.root, () -> {
                    $(ui(menuBar()));
                    $(contentRow, style.content, () -> {
                        $(vbox, style.activityBar, () -> {
                            $(photoPageButton);
                            $(settingsPageButton);
                        });
                        $(pageStack, () -> {
                            $(outerSplit, style.splits, () -> {
                                $(sidebar);
                                $(centerColumn, style.center, () -> {
                                    $(toolbar, style.toolbar, () -> {
                                        $(addFolder);
                                        $(rescan);
                                        $(search, style.search);
                                        $(tagFilter);
                                        $(ui(grow()), style.grow);
                                        $(sort, style.sortBox);
                                        $(zoom, style.zoom);
                                        $(slideshow);
                                        $(themeToggle);
                                    });
                                    $(innerSplit, style.splits, () -> {
                                        $(gridStack, style.gridStack, () -> {
                                            $(grid);
                                            $(ui(welcome()));
                                        });
                                        $(inspector);
                                    });
                                    $(statusbar, style.statusbar, () -> {
                                        $(statusMessage);
                                        $(ui(grow()), style.grow);
                                        $(selectionInfo, style.selection);
                                        $(ui(grow()), style.grow);
                                        $(progress);
                                        $(statusCount);
                                    });
                                });
                            });
                            $(settings);
                        });
                    });
                });
            }
        };
    }

    /** The menu bar of the Swing edition, rebuilt with the native JavaFX control. */
    private MenuBar menuBar() {
        Menu file = new Menu("ファイル");
        file.getItems().add(menuItem("監視フォルダ…", new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN), this::openScanFolders));
        file.getItems().add(menuItem("再スキャン", new KeyCodeCombination(KeyCode.F5), this::rescanAll));
        file.getItems()
                .add(menuItem("設定…", new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN), () -> showPage(Page.SETTINGS)));
        file.getItems().add(new SeparatorMenuItem());
        file.getItems().add(menuItem("終了", new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), this::requestShutdown));

        Menu view = new Menu("表示");
        view.getItems()
                .add(menuItem("すべての写真", new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN), () -> selectMode(SidebarView.Mode.ALL, -1)));
        view.getItems()
                .add(menuItem("お気に入り", new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN), () -> selectMode(SidebarView.Mode.FAVORITES, -1)));
        view.getItems()
                .add(menuItem("最近の写真", new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN), () -> selectMode(SidebarView.Mode.RECENT, -1)));
        view.getItems().add(new SeparatorMenuItem());
        view.getItems().add(menuItem("スライドショー", new KeyCodeCombination(KeyCode.F11), this::openSlideshow));
        view.getItems().add(menuItem("検索に移動", new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> search.focus()));
        view.getItems().add(menuItem("タグを管理…", new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN), this::openTagManager));
        CheckMenuItem inspectorItem = new CheckMenuItem("インスペクター");
        inspectorItem.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN));
        inspectorItem.setSelected(setting.inspectorVisible.get());
        inspectorItem.setOnAction(event -> setInspectorVisible(inspectorItem.isSelected()));
        view.getItems().add(inspectorItem);
        CheckMenuItem darkItem = new CheckMenuItem("ダークモード");
        darkItem.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        darkItem.setSelected(appearance.theme.get() == Theme.Dark);
        darkItem.setOnAction(event -> appearance.theme.set(darkItem.isSelected() ? Theme.Dark : Theme.Light));
        appearance.theme.observe().on(Viewtify.UIThread).to(theme -> darkItem.setSelected(theme == Theme.Dark));
        view.getItems().add(darkItem);

        Menu help = new Menu("ヘルプ");
        help.getItems().add(menuItem("Freja について", null, this::about));
        return new MenuBar(file, view, help);
    }

    private MenuItem menuItem(String text, KeyCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.setOnAction(event -> action.run());
        return item;
    }

    /** The card shown over an empty library. */
    private Node welcome() {
        UILabel icon = new UILabel(this).text("🖼");
        icon.style(style.welcomeIcon);
        UILabel title = new UILabel(this).text("写真をまとめて、すぐに見つける");
        title.style(style.welcomeTitle);
        UILabel line1 = new UILabel(this).text("フォルダを登録すると、サムネイルとメタデータを自動で作成し、");
        UILabel line2 = new UILabel(this).text("その後の追加・変更・削除もバックグラウンドで追跡します。");
        UIButton add = new UIButton(this).text("フォルダを追加").icon(FontAwesomeSolid.FOLDER_PLUS);
        add.style(style.welcomeAdd);
        add.when(User.Action, event -> openScanFolders());
        UILabel libraryPath = new UILabel(this).text("ライブラリ: " + FrejaFX.database());
        libraryPath.style(style.welcomePath);
        UILabel note = new UILabel(this).text("写真は移動・変更されません。メタデータはこのコンピューターの SQLite に保存されます。");
        note.style(style.welcomePath);
        VBox card = new VBox(12, icon.ui(), title.ui(), line1.ui(), line2.ui(), add.ui(), libraryPath.ui(), note.ui());
        card.setAlignment(Pos.CENTER);
        welcomeCard = card;
        return card;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        instance = this;
        FrejaFX.installed(this);

        addFolder.text("フォルダを追加").icon(FontAwesomeSolid.FOLDER_PLUS);
        rescan.text("再スキャン").icon(FontAwesomeSolid.SYNC);
        rescan.tooltip("すべてのフォルダを再スキャン (F5)");
        tagFilter.text("タグ").icon(FontAwesomeSolid.TAG);
        tagFilter.tooltip("タグで絞り込む");
        slideshow.text("スライドショー").icon(FontAwesomeSolid.PLAY);
        search.placeholder("検索").clearable().prefix(FontAwesomeSolid.SEARCH);
        sort.items(SortOrder.values()).render(order -> order.label());
        sort.value(setting.order());
        sort.tooltip("並び順");
        zoom.range(120, 220).step(10).format(size -> size + " px");
        zoom.value((double) setting.thumbSize.get());
        zoom.tooltip("サムネイルサイズ");
        statusMessage.text("準備完了");
        statusCount.text("");
        selectionInfo.text("");
        progress.visible(false).managed(false);
        photoPageButton.icon(FontAwesomeSolid.IMAGE).text("").tooltip("写真ライブラリ");
        settingsPageButton.icon(FontAwesomeSolid.COG).text("").tooltip("設定");
        photoPageButton.style(style.activityButton);
        settingsPageButton.style(style.activityButton);
        themeToggle.text(appearance.theme.get() == Theme.Dark ? "☀" : "☾");
        themeToggle.style(style.themeToggle);

        // The split occupies the width; both pages fill the stack.
        HBox.setHgrow(outerSplit.ui(), Priority.ALWAYS);
        HBox.setHgrow(settings.ui(), Priority.ALWAYS);
        VBox.setVgrow(contentRow.ui(), Priority.ALWAYS);
        HBox.setHgrow(pageStack.ui(), Priority.ALWAYS);
        // UISplitPane.horizontal() only exists in newer Viewtify builds; set the JavaFX node
        // directly so the application also runs against the released 2.35.1 artifact.
        outerSplit.ui().setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        outerSplit.ui().setDividerPositions(0.17);
        innerSplit.ui().setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        innerSplit.ui().setDividerPositions(0.77);

        addFolder.when(User.Action, event -> openScanFolders());
        rescan.when(User.Action, event -> rescanAll());
        tagFilter.when(User.Action, event -> {
            if (library == null) {
                return;
            }
            Viewtify.dialog()
                    .location(tagFilter.ui(), ArrowLocation.BOTTOM_LEFT)
                    .showPopup(() -> new TagFilterView(library, selectedTagIds, MainView.this::tagsChanged));
        });
        slideshow.when(User.Action, event -> openSlideshow());
        themeToggle.when(User.LeftClick, event -> appearance.theme.set(appearance.theme.get() == Theme.Dark ? Theme.Light : Theme.Dark));
        appearance.theme.observe().on(Viewtify.UIThread).to(theme -> themeToggle.text(theme == Theme.Dark ? "☀" : "☾"));
        // Keep the Swing palette, which FxPalette mirrors, in sync with the JavaFX theme switch.
        appearance.theme.observe().on(Viewtify.UIThread).to(theme -> {
            freja.ui.Theme.setDark(theme == Theme.Dark);
            FxPalette.refresh();
        });
        photoPageButton.when(User.LeftClick, event -> showPage(Page.VIEWER));
        settingsPageButton.when(User.LeftClick, event -> showPage(Page.SETTINGS));
        photoPageButton.styleWhile(page.observe().map(p -> p == Page.VIEWER), style.activitySelected);
        settingsPageButton.styleWhile(page.observe().map(p -> p == Page.SETTINGS), style.activitySelected);

        // The progress bar and status line follow the scanner reports.
        scannerState.observe().on(Viewtify.UIThread).to(state -> applyState(state));

        search.when(User.Input).debounce(280, TimeUnit.MILLISECONDS).on(Viewtify.UIThread).to(event -> scheduleRefresh());
        sort.observe().on(Viewtify.UIThread).to(order -> {
            if (suppressSortEvents || order == null) {
                return;
            }
            if (mode == SidebarView.Mode.FOLDER && folderId >= 0) {
                library.setFolderSort(folderId, order);
            } else {
                setting.sort.set(order);
                setting.store();
            }
            refreshAll();
        });
        zoom.observe().debounce(200, TimeUnit.MILLISECONDS).on(Viewtify.UIThread).to(size -> {
            int px = (int) Math.round(size);
            setting.thumbSize.set(px);
            setting.store();
            grid.setThumbSize(px);
            zoom.tooltip("サムネイルサイズ: " + px + " px");
        });

        wireSidebar();
        grid.setHandlers(photo -> {
            inspector.show(photo);
            updateSelectionInfo();
        }, this::openViewer, this::buildPhotoMenu, this::showTagPicker, this::followStickyFolder);
        inspector.setListener(new InspectorView.Listener() {
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
                refreshAll();
            }
        });

        refreshTick.observe().debounce(320, TimeUnit.MILLISECONDS).on(Viewtify.UIThread).to(tick -> refreshAll());

        setInspectorVisible(setting.inspectorVisible.get());
        showPage(Page.VIEWER);
        pendingFolders.addAll(FrejaFX.startupFolders());
        openLibrary();
    }

    /** Opens the catalog and the scanner off the UI thread, then wires them in. */
    private void openLibrary() {
        Viewtify.inWorker(() -> {
            try {
                Library opened = new Library(FrejaFX.database());
                LibraryScanner scanner = new LibraryScanner(opened);
                Viewtify.inUI(() -> attach(opened, scanner));
            } catch (Exception e) {
                e.printStackTrace();
                Viewtify.inUI(() -> Viewtify.dialog()
                        .title("Freja")
                        .button("OK")
                        .show(AlertType.ERROR, "Freja を起動できません。\n\n" + e.getMessage() + "\n\nライブラリ: " + FrejaFX.database()));
            }
        });
    }

    private void attach(Library library, LibraryScanner scanner) {
        this.library = library;
        this.scanner = scanner;
        this.thumbs = new ThumbnailStore(library);
        grid.setThumbnails(thumbs);
        inspector.setThumbnails(thumbs);
        settings.setLibraryLocation(FrejaFX.database().toString());

        scanner.addListener(new LibraryScanner.Listener() {
            @Override
            public void onStateChanged(LibraryScanner.State state) {
                scannerState.set(state);
            }

            @Override
            public void onLibraryChanged() {
                // The scanner notifies once per folder; the 320 ms debounce coalesces those into a
                // few progressive updates per second, exactly like the Swing refreshTimer.
                Viewtify.inUI(() -> {
                    thumbs.retryMissing();
                    refreshTick.set(new Object());
                });
            }
        });

        restoreSelectedFolder();
        refreshAll();
        scanner.start(true, setting.scanOnStartup.get(), setting.sweep.get());
        if (!pendingFolders.isEmpty()) {
            addFolders(List.copyOf(pendingFolders));
            pendingFolders.clear();
        }
    }

    // ----------------------------------------------------------------------------
    // Refresh pipeline — loads on a worker thread, applies on the UI thread
    // ----------------------------------------------------------------------------

    private void scheduleRefresh() {
        refreshTick.set(new Object());
    }

    private void refreshAll() {
        if (library == null) {
            return;
        }
        long generation = ++refreshGeneration;
        SidebarView.Mode requestedMode = mode;
        long requestedFolder = folderId;
        SortOrder order = sort.value();
        String searchText = search.value();
        List<Long> tagIds = List.copyOf(selectedTagIds);
        Viewtify.inWorker(() -> {
            try {
                RefreshData data = loadView(requestedMode, requestedFolder, order, searchText, tagIds);
                Viewtify.inUI(() -> {
                    if (generation == refreshGeneration) {
                        applyRefresh(data);
                    }
                });
            } catch (RuntimeException e) {
                Viewtify.inUI(() -> {
                    if (generation == refreshGeneration) {
                        statusMessage.text("ライブラリを読み込めません: " + e.getMessage());
                    }
                });
            }
        });
    }

    /** Reads and sorts everything the current view needs. Never touches a UI node. */
    private RefreshData loadView(SidebarView.Mode requestedMode, long requestedFolder, SortOrder order, String searchText, List<Long> tagIds) {
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
            Map<Long, Boolean> matched = new HashMap<>();
            for (Photo photo : photos) {
                matched.put(photo.folderId(), true);
            }
            visibleFolders = folders.stream().filter(folder -> matched.containsKey(folder.id())).toList();
        }
        boolean fallback = requestedMode == SidebarView.Mode.FOLDER && visibleFolders.stream()
                .noneMatch(folder -> folder.id() == requestedFolder);
        SidebarView.Mode effectiveMode = fallback ? SidebarView.Mode.ALL : requestedMode;
        long effectiveFolder = fallback ? -1 : requestedFolder;

        List<PhotoGridView.Section> sections = new ArrayList<>();
        if (effectiveMode == SidebarView.Mode.ALL || effectiveMode == SidebarView.Mode.FOLDER) {
            List<Photo> ordered = orderByFolder(photos, visibleFolders, effectiveMode == SidebarView.Mode.FOLDER);
            Map<Long, List<Photo>> grouped = new HashMap<>();
            for (Photo photo : ordered) {
                grouped.computeIfAbsent(photo.folderId(), id -> new ArrayList<>()).add(photo);
            }
            for (Folder folder : visibleFolders) {
                List<Photo> list = grouped.remove(folder.id());
                if (list != null && !list.isEmpty()) {
                    sections.add(new PhotoGridView.Section(folder.id(), folder.name(), folder.tags(), list));
                }
            }
            List<Photo> rest = new ArrayList<>();
            grouped.values().forEach(rest::addAll);
            if (!rest.isEmpty()) {
                sections.add(new PhotoGridView.Section(-1, null, List.of(), rest));
            }
        } else if (filter.groupsByDate()) {
            List<Photo> current = new ArrayList<>();
            String label = null;
            for (Photo photo : photos) {
                if (label == null || !label.equals(photo.dateLabel())) {
                    if (label != null) {
                        sections.add(new PhotoGridView.Section(-1, label, List.of(), List.copyOf(current)));
                    }
                    label = photo.dateLabel();
                    current = new ArrayList<>();
                }
                current.add(photo);
            }
            if (label != null) {
                sections.add(new PhotoGridView.Section(-1, label, List.of(), List.copyOf(current)));
            }
        } else {
            sections.add(new PhotoGridView.Section(-1, null, List.of(), photos));
        }
        return new RefreshData(stats, photos, visibleFolders, effectiveMode, effectiveFolder, sections, fallback);
    }

    /** Installs a loaded view on the UI thread. Never touches the catalog. */
    private void applyRefresh(RefreshData data) {
        if (data.fallback()) {
            mode = SidebarView.Mode.ALL;
            folderId = -1;
            scrollToSelectedFolder = false;
            applySortForCurrentView();
            refreshAll();
            return;
        }
        grid.setData(data.sections(), zoom.value() == null ? setting.thumbSize.get() : (int) Math.round(zoom.value()));
        sidebar.setData(data.stats(), data.visibleFolders(), data.mode(), data.folderId());
        statusCount.text(data.stats().photos() + " 枚  ·  " + Photo.formatSize(data.stats().bytes()));
        updateSelectionInfo();
        stage().to(stage -> stage.setTitle("Freja — " + data.stats().photos() + " 枚の写真"));
        libraryEmpty.set(data.stats().folders() == 0);
        welcomeCard.setVisible(libraryEmpty.is(true));
        if (grid.selectedPhoto() != null) {
            inspector.show(grid.selectedPhoto());
        } else {
            inspector.show(null);
        }
        if (scrollToSelectedFolder && data.mode() == SidebarView.Mode.FOLDER) {
            scrollToSelectedFolder = false;
            long target = data.folderId();
            Viewtify.inUI(() -> grid.scrollToFolder(target));
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

    // ----------------------------------------------------------------------------
    // Navigation state
    // ----------------------------------------------------------------------------

    private void wireSidebar() {
        sidebar.setListener(new SidebarView.Listener() {
            @Override
            public void onSelect(SidebarView.Mode mode, long folderId) {
                selectMode(mode, folderId);
            }

            @Override
            public void onAddFolder() {
                openScanFolders();
            }

            @Override
            public void onRescanFolder(long id) {
                if (scanner != null) {
                    scanner.scanFolder(id);
                    statusMessage.text("フォルダを再スキャンしています…");
                }
            }

            @Override
            public void onSortFolder(long id, SortOrder order) {
                library.setFolderSort(id, order);
                if (mode == SidebarView.Mode.FOLDER && MainView.this.folderId == id) {
                    applySortForCurrentView();
                    refreshAll();
                }
            }

            @Override
            public void onRenameFolder(long id) {
                renameFolder(id);
            }

            @Override
            public void onRemoveFolder(long id) {
                removeFolder(id);
            }

            @Override
            public void onRevealFolder(long id) {
                library.folder(id).ifPresent(folder -> reveal(folder.path()));
            }

            @Override
            public void onEditFolderTags(long id) {
                showTagPicker(id, sidebar.ui());
            }

            @Override
            public void onManageTags() {
                openTagManager();
            }
        });
    }

    private void selectMode(SidebarView.Mode mode, long folderId) {
        if (this.mode == mode && this.folderId == folderId) {
            // Clicking the entry which is already selected means "take me back there".
            revisit();
            return;
        }
        boolean folderToFolder = this.mode == SidebarView.Mode.FOLDER && mode == SidebarView.Mode.FOLDER && folderId >= 0;
        this.mode = mode;
        this.folderId = folderId;
        this.scrollToSelectedFolder = mode == SidebarView.Mode.FOLDER && folderId >= 0;
        if (this.scrollToSelectedFolder) {
            library.folder(folderId).ifPresent(folder -> {
                setting.selectedFolder.set(folder.path().toString());
                setting.store();
            });
        }
        applySortForCurrentView();
        if (folderToFolder && grid.scrollToFolder(folderId)) {
            scrollToSelectedFolder = false;
            return;
        }
        refreshAll();
    }

    private void revisit() {
        if (mode == SidebarView.Mode.FOLDER && folderId >= 0) {
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

    /** The grid header click: follow the section without reloading. */
    private void followStickyFolder(long folderId) {
        sidebar.selectFolderSilently(folderId);
        mode = SidebarView.Mode.FOLDER;
        this.folderId = folderId;
    }

    private void applySortForCurrentView() {
        SortOrder order = mode == SidebarView.Mode.FOLDER && library != null
                ? library.folder(folderId).map(Folder::sort).orElse(setting.order())
                : setting.order();
        suppressSortEvents = true;
        try {
            sort.value(order);
        } finally {
            suppressSortEvents = false;
        }
        sort.tooltip(mode == SidebarView.Mode.FOLDER ? "このフォルダの表示順" : "表示順");
    }

    private void restoreSelectedFolder() {
        String path = setting.selectedFolder.get();
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            String key = PathUtil.key(Path.of(path));
            for (Folder folder : library.folders()) {
                if (PathUtil.key(folder.path()).equals(key)) {
                    mode = SidebarView.Mode.FOLDER;
                    folderId = folder.id();
                    scrollToSelectedFolder = true;
                    applySortForCurrentView();
                    return;
                }
            }
        } catch (RuntimeException e) {
            // A stale or malformed preference is simply ignored; the library view stays as it was.
        }
    }

    /** Called after tags or folder assignments changed in one of the tag dialogs. */
    private void tagsChanged() {
        if (library == null) {
            return;
        }
        List<Long> existing = library.tags().stream().map(Tag::id).toList();
        selectedTagIds.removeIf(id -> !existing.contains(id));
        tagFilter.text(selectedTagIds.isEmpty() ? "タグ" : "タグ (" + selectedTagIds.size() + ")");
        thumbs.retryMissing();
        refreshAll();
    }

    private void showTagPicker(long folderId, Node anchor) {
        if (library == null) {
            return;
        }
        Viewtify.dialog()
                .location(anchor, ArrowLocation.BOTTOM_LEFT)
                .showPopup(() -> new TagPickerView(library, folderId, this::tagsChanged));
    }

    // ----------------------------------------------------------------------------
    // Photo actions
    // ----------------------------------------------------------------------------

    /** Fills the per-photo context menu; called freshly on every popup. */
    private void buildPhotoMenu(UIContextMenu menu, Photo photo, Node anchor) {
        List<Photo> selected = grid.selectedPhotos();
        List<Photo> targets = selected.size() > 1 && selected.contains(photo) ? selected : List.of(photo);
        String suffix = targets.size() > 1 ? " (" + targets.size() + "枚)" : "";
        menu.menu("開く" + suffix).action(() -> openViewer(photo, targets));
        menu.menu((photo.favorite() ? "お気に入りから外す" : "お気に入りに追加") + suffix).action(() -> setFavorites(targets, !photo.favorite()));
        menu.separator();
        menu.menu("エクスプローラーで表示").action(() -> reveal(photo.path()));
        menu.menu("パスをコピー" + suffix).action(() -> copyPaths(targets));
        menu.menu("再読み込み" + suffix).action(() -> {
            if (scanner == null) {
                return;
            }
            for (Photo target : targets) {
                thumbs.clear(target);
                scanner.reimport(target);
            }
            statusMessage.text(targets.size() + " 枚を再読み込みしています…");
        });
        menu.separator();
        menu.menu("ライブラリから削除" + suffix).action(() -> removeFromLibrary(targets));
        menu.menu("ディスクから削除" + suffix).action(() -> deleteFromDisk(targets));
    }

    private void openViewer(Photo photo) {
        openViewer(photo, grid.photos());
    }

    private void openViewer(Photo photo, List<Photo> selection) {
        openViewer(photo, selection, null);
    }

    private void openViewer(Photo photo, List<Photo> selection, Runnable afterOpen) {
        if (photo == null || viewerOpening) {
            return;
        }
        viewerOpening = true;
        // A double click fires two clicks before the double-click action. Opening a new Stage from
        // inside that mouse event used to re-enter the grid's event handling; defer it to the next
        // pulse and ignore any additional open request until this one has completed.
        javafx.application.Platform.runLater(() -> {
            try {
                if (viewer.value != null) {
                    viewer.value.close();
                }
                List<Photo> photos = selection == null || selection.isEmpty() ? grid.photos() : selection;
                if (photos.isEmpty()) {
                    photos = List.of(photo);
                }
                viewer.value = PhotoViewerView.open(stage().get(), photos, photo, (updated, favorite) -> {
                    library.setFavorite(updated.id(), favorite);
                    refreshAll();
                }, null);
                if (afterOpen != null) {
                    afterOpen.run();
                }
            } catch (RuntimeException e) {
                statusMessage.text("ビューアを開けませんでした: " + e.getMessage());
            } finally {
                viewerOpening = false;
            }
        });
    }

    private void openSlideshow() {
        List<Photo> photos = grid.photos();
        if (photos.isEmpty()) {
            statusMessage.text("スライドショーにする写真がありません");
            return;
        }
        Photo start = grid.selectedPhoto() == null ? photos.get(0) : grid.selectedPhoto();
        openViewer(start, photos, () -> {
            if (viewer.value != null) {
                viewer.value.startSlideshow();
            }
        });
    }

    private void setFavorite(Photo photo, boolean favorite) {
        library.setFavorite(photo.id(), favorite);
        refreshAll();
    }

    private void setFavorites(List<Photo> photos, boolean favorite) {
        for (Photo photo : photos) {
            library.setFavorite(photo.id(), favorite);
        }
        refreshAll();
    }

    private void removeFromLibrary(List<Photo> photos) {
        if (photos.isEmpty()) {
            return;
        }
        String description = photos.size() > 1 ? photos.size() + " 枚の写真をライブラリから削除します。" : photos.get(0).fileName() + " をライブラリから削除します。";
        confirm("写真を削除", description + "\n写真ファイル自体は削除されません。").to(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
            for (Photo photo : photos) {
                library.deletePhoto(photo.id());
            }
            grid.clearSelection();
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
        confirm("ディスクから削除", description + "\n\nこの操作は取り消せません。ライブラリからも削除されます。").to(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
            Viewtify.inWorker(() -> {
                int deleted = 0;
                int failed = 0;
                for (Photo photo : photos) {
                    try {
                        if (Files.deleteIfExists(photo.path())) {
                            library.deletePhoto(photo.id());
                            deleted++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                    }
                }
                int done = deleted;
                int bad = failed;
                Viewtify.inUI(() -> {
                    grid.clearSelection();
                    inspector.show(null);
                    refreshAll();
                    statusMessage.text(bad == 0 ? done + " 枚をディスクから削除しました" : done + " 枚を削除し、" + bad + " 枚は削除できませんでした");
                });
            });
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
        ClipboardContent content = new ClipboardContent();
        content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
        statusMessage.text(photos.size() > 1 ? photos.size() + " 枚のパスをコピーしました" : "パスをコピーしました");
    }

    private void reveal(Path path) {
        Viewtify.inWorker(() -> {
            try {
                Path target = Files.isDirectory(path) ? path : path.getParent();
                if (target != null) {
                    Desktop.getDesktop().open(target.toFile());
                }
            } catch (Exception e) {
                Viewtify.inUI(() -> statusMessage.text("フォルダを開けません: " + e.getMessage()));
            }
        });
    }

    private void renameFolder(long id) {
        Folder folder = library.folder(id).orElse(null);
        if (folder == null) {
            return;
        }
        Viewtify.dialog().showInput("フォルダの表示名", folder.name()).to(name -> {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                statusMessage.text("フォルダ名を入力してください");
                return;
            }
            library.renameFolder(id, trimmed);
            refreshAll();
            statusMessage.text("フォルダ名を変更しました: " + trimmed);
        });
    }

    private void removeFolder(long id) {
        Optional<Folder> folder = library.folder(id);
        if (folder.isEmpty()) {
            return;
        }
        confirm("フォルダを削除", "「" + folder.get().name() + "」と配下のサブフォルダをライブラリから削除します。\n写真ファイル自体は削除されません。").to(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                return;
            }
            scanner.removeFolder(id);
            if (mode == SidebarView.Mode.FOLDER && MainView.this.folderId == id) {
                mode = SidebarView.Mode.ALL;
                MainView.this.folderId = -1;
                scrollToSelectedFolder = false;
                applySortForCurrentView();
            }
            refreshAll();
        });
    }

    /** @return the live "yes" signal of a confirmation dialog */
    private Variable<Boolean> confirm(String title, String message) {
        Variable<Boolean> answer = Variable.of(false);
        Viewtify.dialog()
                .title(title)
                .button("OK", "キャンセル")
                .show(AlertType.CONFIRMATION, message)
                .to(type -> answer.set(type != null && type.getButtonData() == ButtonType.OK.getButtonData()));
        return answer;
    }

    // ----------------------------------------------------------------------------
    // Scan folders / tags dialogs
    // ----------------------------------------------------------------------------

    /** Registers a folder, mirroring the Swing status feedback. */
    public void registerFolder(Path path) {
        try {
            scanner.addFolder(path);
            statusMessage.text(path + " をスキャンしています…");
            refreshAll();
        } catch (IllegalArgumentException e) {
            statusMessage.text(e.getMessage());
        }
    }

    /** Adds folders, typically from the command line. */
    public void addFolders(List<Path> folders) {
        for (Path folder : folders) {
            try {
                Folder added = scanner.addFolder(folder);
                Viewtify.inUI(() -> selectMode(SidebarView.Mode.FOLDER, added.id()));
            } catch (IllegalArgumentException e) {
                Viewtify.inUI(() -> statusMessage.text(e.getMessage()));
            }
        }
        Viewtify.inUI(this::refreshAll);
    }

    /** Opens the watched-folder manager. */
    public void openScanFolders() {
        if (library == null) {
            statusMessage.text("ライブラリを読み込み中です。少し待ってからもう一度お試しください。");
            return;
        }
        if (scanFoldersDialog.value == null) {
            scanFoldersDialog.value = new ScanFoldersView(library, setting, new ScanFoldersView.Listener() {
                @Override
                public void onRegisterFolder(Path path) {
                    registerFolder(path);
                }

                @Override
                public void onRemoveFolders(List<Long> folderIds) {
                    for (Long id : folderIds) {
                        scanner.removeFolder(id);
                    }
                    if (folderIds.contains(MainView.this.folderId)) {
                        mode = SidebarView.Mode.ALL;
                        MainView.this.folderId = -1;
                        scrollToSelectedFolder = false;
                        applySortForCurrentView();
                    }
                    refreshAll();
                    statusMessage.text("フォルダを削除しました");
                }

                @Override
                public void onScanFolders(List<Long> folderIds) {
                    for (Long id : folderIds) {
                        scanner.scanFolder(id);
                    }
                    statusMessage.text(folderIds.size() + " 件のフォルダをスキャンしています…");
                }

                @Override
                public void onClearFolders() {
                    scanner.clearFolders();
                    refreshAll();
                    statusMessage.text("監視フォルダをクリアしました");
                }

                @Override
                public void onChanged() {
                    if (scanFoldersDialog.value != null) {
                        scanFoldersDialog.value.reload();
                    }
                    refreshAll();
                }
            });
        }
        scanFoldersDialog.value.reload();
        Viewtify.dialog().title("監視フォルダの管理").button("閉じる").size(560, 520).show(scanFoldersDialog.value).to(changed -> {
            if (Boolean.TRUE.equals(changed)) {
                refreshAll();
            }
        });
    }

    /** Opens the tag manager dialog. */
    public void openTagManager() {
        if (library == null) {
            return;
        }
        Viewtify.dialog().title("タグを管理").button("閉じる").size(680, 500).show(TagManagerView.class).to(changed -> {
            if (Boolean.TRUE.equals(changed)) {
                tagsChanged();
            }
        });
    }

    // ----------------------------------------------------------------------------
    // Chrome: status bar, theme, pages, shutdown
    // ----------------------------------------------------------------------------

    private void applyState(LibraryScanner.State state) {
        if (state == null) {
            return;
        }
        progress.visible(state.scanning()).managed(state.scanning());
        if (state.scanning()) {
            if (state.total() > 0) {
                progress.value(Math.min(1.0, state.processed() / (double) Math.max(1, state.total())));
            } else {
                progress.value(-1.0);
            }
            String detail = state.message() == null || state.message().isBlank() ? "スキャン中" : state.message();
            if (state.total() > 0) {
                detail += "  (" + state.processed() + " / " + state.total() + ")";
            }
            detail += "  ·  " + formatDuration(state.elapsed());
            statusMessage.text(shorten(detail, 110));
        } else {
            progress.value(0.0);
            if (state.errors() > 0) {
                statusMessage.text(state.message() == null || state.message().isBlank() ? state.errors() + " 件のファイルを読み込めませんでした"
                        : shorten(state.message(), 110));
            } else if (state.imported() > 0) {
                statusMessage.text(state.imported() + " 枚の写真を更新しました  (" + formatDuration(state.elapsed()) + ")");
            } else if (!state.elapsed().isZero()) {
                statusMessage.text("スキャン完了  (" + formatDuration(state.elapsed()) + ")");
            } else {
                statusMessage.text("準備完了");
            }
        }
    }

    /** Rescans every folder, guarded against clicks before the catalog is open. */
    private void rescanAll() {
        if (scanner == null) {
            return;
        }
        scanner.scanAll();
        statusMessage.text("再スキャンを開始しました");
    }

    /** Writes a message into the status bar, used by the settings page. */
    public void status(Variable<String> message) {
        statusMessage.text(message);
    }

    /** Applies a thumbnail size chosen on the settings page. */
    public void setThumbSize(int size) {
        zoom.value((double) size);
        grid.setThumbSize(size);
    }

    private void showPage(Page target) {
        page.set(target);
        boolean viewerPage = target == Page.VIEWER;
        outerSplit.visible(viewerPage).managed(viewerPage);
        settings.ui().setVisible(!viewerPage);
        settings.ui().setManaged(!viewerPage);
    }

    /** Shows or hides the details panel and remembers the choice. */
    private void setInspectorVisible(boolean visible) {
        setting.inspectorVisible.set(visible);
        setting.store();
        javafx.collections.ObservableList<Node> items = innerSplit.ui().getItems();
        if (visible) {
            if (!items.contains(inspector.ui())) {
                items.add(inspector.ui());
            }
            innerSplit.ui().setDividerPositions(0.80);
        } else {
            items.setAll(gridStack.ui());
        }
    }

    private void updateSelectionInfo() {
        List<Photo> selected = grid.selectedPhotos();
        if (selected.isEmpty()) {
            selectionInfo.text("");
            return;
        }
        if (selected.size() == 1) {
            Photo photo = selected.get(0);
            selectionInfo.text(photo.fileName() + "  ·  " + photo.width() + "×" + photo.height() + "  ·  " + photo
                    .capturedLabel() + "  ·  " + Photo.formatSize(photo.byteSize()));
        } else {
            long bytes = 0;
            for (Photo photo : selected) {
                bytes += photo.byteSize();
            }
            selectionInfo.text(selected.size() + " 枚  ·  合計 " + Photo.formatSize(bytes));
        }
    }

    /** The about dialog, using the same content as the Swing edition. */
    public void about() {
        if (library == null) {
            return;
        }
        LibraryStats stats = library.stats();
        Viewtify.dialog()
                .title("Freja について")
                .button("OK")
                .show(AlertType.INFORMATION, """
                        Freja %s (JavaFX / Viewtify)
                        Picasa ライクなローカル写真ライブラリ

                        写真: %d 枚
                        お気に入り: %d 枚
                        最近の写真: %d 枚
                        フォルダ: %d
                        ライブラリ: %s

                        Java %s
                        """.formatted(freja.Freja.VERSION, stats.photos(), stats.favorites(), stats.recent(), stats.folders(), library
                        .location(), System.getProperty("java.version")));
    }

    /** Asks the window to close; the close-request hook performs the cleanup. */
    public void requestShutdown() {
        Viewtify.inUI(() -> stage().to(Stage::close, this::exitNow));
    }

    private void exitNow() {
        cleanup();
        javafx.application.Platform.exit();
        System.exit(0);
    }

    /** Close-request hook: true lets the window close. */
    public boolean confirmShutdown() {
        return true;
    }

    /** Releases catalog resources once; called on close or termination. */
    public void cleanup() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        setting.store();
        Viewtify.inWorker(() -> {
            try {
                if (scanner != null) {
                    scanner.close();
                }
            } catch (RuntimeException ignored) {
            }
            try {
                if (thumbs != null) {
                    thumbs.close();
                }
            } catch (RuntimeException ignored) {
            }
            try {
                if (library != null) {
                    library.close();
                }
            } catch (RuntimeException ignored) {
            }
        });
    }

    /** Renders a scan duration in a form that stays readable from milliseconds to hours. */
    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        }
        long seconds = millis / 1000;
        if (seconds < 60) {
            return (millis / 100) / 10.0 + "秒";
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

    /** A horizontal spacer which eats the remaining width. */
    private static Node grow() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    /** Visual language of the main window, mirroring {@code freja.ui.Theme}. */
    interface style extends StyleDSL {

        Style root = () -> background.color("-freja-background");

        Style content = () -> display.minHeight(0, px);

        Style activityBar = () -> {
            background.color("-freja-sidebar");
            padding.vertical(8, px);
            display.minWidth(48, px);
            display.maxWidth(48, px);
        };

        Style activityButton = () -> {
            background.color("transparent");
            padding.vertical(11, px).horizontal(14, px);
            font.size(17, px).color("-freja-muted");
            cursor.pointer();
            $.hover(() -> {
                background.color("-freja-hover");
                font.color("-freja-text");
            });
        };

        Style activitySelected = () -> {
            background.color("-freja-accent-soft");
            font.color("-freja-accent-dark");
        };

        Style splits = () -> background.color("-freja-background");

        Style center = () -> display.minWidth(420, px);

        Style toolbar = () -> {
            background.color("-freja-sidebar");
            padding.vertical(7, px).horizontal(12, px);
        };

        Style search = () -> display.maxWidth(300, px).minWidth(180, px);

        Style sortBox = () -> display.maxWidth(190, px);

        Style zoom = () -> display.maxWidth(130, px);

        Style gridStack = () -> background.color("-freja-background");

        Style statusbar = () -> {
            background.color("-freja-surface");
            padding.vertical(6, px).horizontal(14, px);
        };

        Style selection = () -> font.size(11, px).color("-freja-muted");

        Style themeToggle = () -> {
            font.size(15, px).color("-freja-muted");
            padding.vertical(4, px).horizontal(8, px);
            cursor.pointer();
            $.hover(() -> font.color("-freja-text"));
        };

        Style grow = () -> display.minWidth(10, px);

        Style welcomeIcon = () -> font.size(72, px).color("-freja-accent");

        Style welcomeTitle = () -> font.size(19, px).weight(700).color("-freja-text");

        Style welcomeAdd = () -> margin.vertical(6, px);

        Style welcomePath = () -> font.size(10, px).color("-freja-muted");
    }
}
