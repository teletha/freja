/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.web;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import apocrypha.IpcChannel;
import apocrypha.Window;
import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.PhotoFilter.SortOrder;
import freja.model.Tag;
import freja.scan.LibraryScanner;
import freja.store.Library;
import kiss.I;

/**
 * The IPC handlers of the Web edition.
 * <p>
 * These methods are the Apocrypha counterpart of the Swing edition's MainWindow: every call the
 * browser makes crosses the {@code freja:*} channel, reads or mutates the shared catalog, and
 * returns a typed model which Sinobu maps back to JSON. The handlers fetch the catalog lazily
 * through {@link FrejaWeb#library()} so registration can happen before the database finishes
 * opening; a call which arrives too early is the renderer's bug, not the backend's.
 * </p>
 */
public final class WebHandlers {

    private final FrejaWeb app;

    private final WebFullscreen fullscreen = new WebFullscreen();

    /** The query signature of one cached browse snapshot. The revision makes every catalog change
     * start a fresh generation of entries without having to clear the map under a catalog lock. */
    private record BrowseKey(long revision, String mode, String folderId, SortOrder sort, String search,
            List<Long> tagIds) {
    }

    /** A fully ordered result. Page requests only slice {@code photos} and {@code photoSections}. */
    private record Snapshot(String mode, Long folderId, LibraryStats stats, List<Folder> folders,
            List<WebModels.Section> sections, List<Photo> photos, List<Integer> photoSections) {
    }

    /** Cached ordered results, keyed by query signature. {@code computeIfAbsent} makes the first
     * computation single-flight, so concurrent pages never walk the catalog more than once. */
    private final ConcurrentHashMap<BrowseKey, Snapshot> snapshots = new ConcurrentHashMap<>();

    /** Bumped on every catalog change so a snapshot computed during an invalidation is discarded. */
    private volatile long revision;

    /**
     * Creates the handlers.
     *
     * @param app the running application, or null before it is ready
     */
    public WebHandlers(FrejaWeb app) {
        this.app = app;
    }

    private Library library() {
        return app == null ? null : app.library();
    }

    private LibraryScanner scanner() {
        return app == null ? null : app.scanner();
    }

    private WebSettings settings() {
        return app == null ? null : app.settings();
    }

    /**
     * Replies to the renderer handshake with the bootstrap payload once the catalog is open.
     */
    @IpcChannel("freja:hello")
    public void hello() {
        if (library() != null) {
            app.refreshBootstrap();
        }
    }

    /**
     * Browse the catalog. Querying, ordering and grouping the whole matching catalog is done once
     * per query signature and cached; each page request only slices that snapshot. Scroll jumps to
     * a distant section therefore stay cheap no matter how many photos precede it.
     *
     * @param request the browse request, or null for none
     * @return the browse result
     */
    @IpcChannel("freja:browse")
    public WebModels.BrowseResult browse(WebModels.BrowseRequest request) {
        Library library = library();
        if (library == null || request == null) {
            return new WebModels.BrowseResult("ALL", null, null, List.of(), List.of(), List.of(), List.of(), 0, 0, false);
        }
        long current = revision;
        SortOrder order = resolveOrder(request.sort());
        BrowseKey key = new BrowseKey(current, request.mode(), request.folderId(), order, request.search(),
                request.tagIds());
        if (snapshots.size() > 8) {
            snapshots.clear();
        }
        Snapshot snapshot = snapshots.computeIfAbsent(key, this::snapshot);
        if (current != revision) {
            BrowseKey fresh = new BrowseKey(revision, request.mode(), request.folderId(), order, request.search(),
                    request.tagIds());
            snapshot = snapshots.computeIfAbsent(fresh, this::snapshot);
        }
        int total = snapshot.photos().size();
        int from = Math.min(request.offset(), total);
        int to = Math.min(total, from + request.limit());
        return new WebModels.BrowseResult(snapshot.mode(), snapshot.folderId(), snapshot.stats(), snapshot.folders(),
                snapshot.sections(), from >= to ? List.of() : snapshot.photos().subList(from, to),
                from >= to ? List.of() : snapshot.photoSections().subList(from, to), from, total, to < total);
    }

    /**
     * Computes the full ordered result for one query signature. This is the only place which walks
     * the whole catalog; {@link #browse(WebModels.BrowseRequest)} reuses it for every page.
     *
     * @param key the normalized query signature
     * @return the ordered snapshot
     */
    private Snapshot snapshot(BrowseKey key) {
        Library library = library();
        if (library == null) {
            return new Snapshot("ALL", null, new LibraryStats(0, 0, 0, 0, 0), List.of(), List.of(), List.of(), List.of());
        }
        Long requestedFolder = parseFolderId(key.folderId());
        PhotoFilter filter = switch (key.mode()) {
        case "FAVORITES" -> PhotoFilter.favorites();
        case "RECENT" -> PhotoFilter.recent();
        default -> PhotoFilter.all();
        };
        filter = filter.withSort(key.sort()).withSearch(key.search()).withTags(key.tagIds());

        List<Folder> folders = library.folders();
        List<Photo> photos = library.photos(filter);
        List<Folder> visibleFolders = folders.stream().filter(folder -> folder.photoCount() > 0).toList();
        if (filter.search() != null || !filter.tagIds().isEmpty()) {
            List<Long> folderIds = new ArrayList<>();
            for (Photo photo : photos) {
                folderIds.add(photo.folderId());
            }
            final List<Long> matched = folderIds;
            visibleFolders = folders.stream().filter(folder -> matched.contains(folder.id())).toList();
        }
        boolean fallback = "FOLDER".equals(key.mode()) && requestedFolder != null
                && visibleFolders.stream().noneMatch(folder -> folder.id() == requestedFolder);
        String effectiveMode = fallback ? "ALL" : key.mode();
        Long effectiveFolder = fallback ? null : requestedFolder;

        // Groups are collected first, then flattened into one ordered result. That keeps the
        // section starts and their photo pages in exactly the same order as the grid renders.
        List<Long> sectionFolders = new ArrayList<>();
        List<String> sectionLabels = new ArrayList<>();
        List<List<String>> sectionTags = new ArrayList<>();
        List<List<Photo>> sectionPhotos = new ArrayList<>();

        if ("ALL".equals(effectiveMode) || "FOLDER".equals(effectiveMode)) {
            List<Photo> ordered = orderByFolder(photos, visibleFolders, "FOLDER".equals(effectiveMode));
            Map<Long, List<Photo>> grouped = new HashMap<>();
            for (Photo photo : ordered) {
                grouped.computeIfAbsent(photo.folderId(), id -> new ArrayList<>()).add(photo);
            }
            for (Folder folder : visibleFolders) {
                List<Photo> list = grouped.remove(folder.id());
                if (list != null && !list.isEmpty()) {
                    sectionFolders.add(folder.id());
                    sectionLabels.add(folder.name());
                    sectionTags.add(folder.tags());
                    sectionPhotos.add(list);
                }
            }
            List<Photo> rest = new ArrayList<>();
            grouped.values().forEach(rest::addAll);
            if (!rest.isEmpty()) {
                sectionFolders.add(-1L);
                sectionLabels.add(null);
                sectionTags.add(List.of());
                sectionPhotos.add(rest);
            }
        } else if (filter.groupsByDate()) {
            List<Photo> current = new ArrayList<>();
            String label = null;
            for (Photo photo : photos) {
                if (label == null || !label.equals(photo.dateLabel())) {
                    if (label != null) {
                        sectionFolders.add(-1L);
                        sectionLabels.add(label);
                        sectionTags.add(List.of());
                        sectionPhotos.add(List.copyOf(current));
                    }
                    label = photo.dateLabel();
                    current = new ArrayList<>();
                }
                current.add(photo);
            }
            if (label != null) {
                sectionFolders.add(-1L);
                sectionLabels.add(label);
                sectionTags.add(List.of());
                sectionPhotos.add(List.copyOf(current));
            }
        } else {
            sectionFolders.add(-1L);
            sectionLabels.add(null);
            sectionTags.add(List.of());
            sectionPhotos.add(photos);
        }

        List<WebModels.Section> sections = new ArrayList<>(sectionPhotos.size());
        List<Photo> orderedPhotos = new ArrayList<>(photos.size());
        List<Integer> sectionIndexes = new ArrayList<>(photos.size());
        for (int i = 0; i < sectionPhotos.size(); i++) {
            List<Photo> group = sectionPhotos.get(i);
            sections.add(new WebModels.Section(sectionFolders.get(i), sectionLabels.get(i), sectionTags.get(i), orderedPhotos
                    .size(), group.size()));
            for (Photo photo : group) {
                orderedPhotos.add(photo);
                sectionIndexes.add(i);
            }
        }
        return new Snapshot(effectiveMode, effectiveFolder, library.stats(), visibleFolders, sections, orderedPhotos,
                sectionIndexes);
    }

    /** Starts a new generation of snapshots after a catalog mutation. Old entries are left to the
     * size bound instead of being cleared while a catalog lock might be held. */
    void invalidate() {
        revision++;
    }

    /**
     * Reads one photo from the catalog.
     *
     * @param photoId the photo identifier
     * @return the photo, or null when it does not exist
     */
    @IpcChannel("freja:photo")
    public Photo photo(long photoId) {
        Library library = library();
        return library == null ? null : library.photo(photoId).orElse(null);
    }

    /**
     * Returns the folders of the catalog.
     *
     * @return the folders, or an empty list before the catalog opens
     */
    @IpcChannel("freja:folders")
    public List<Folder> folders() {
        Library library = library();
        return library == null ? List.of() : library.folders();
    }

    /**
     * Returns the aggregate counters of the catalog.
     *
     * @return the stats, or null before the catalog opens
     */
    @IpcChannel("freja:stats")
    public LibraryStats stats() {
        Library library = library();
        return library == null ? null : library.stats();
    }

    /** Enters or leaves the native fullscreen window used by the photo viewer. */
    @IpcChannel("freja:fullscreen")
    public void fullscreen(boolean enter) {
        Window window = app == null ? null : app.window();
        if (enter) {
            fullscreen.enter(window);
        } else {
            fullscreen.exit(window);
        }
    }

    /**
     * Returns the latest scanner state.
     *
     * @return a report, or null before the scanner opens
     */
    @IpcChannel("freja:state")
    public WebModels.ScannerReport state() {
        LibraryScanner scanner = scanner();
        return scanner == null ? null : FrejaWeb.report(scanner.state());
    }

    /**
     * Replaces the settings map and persists it. A changed language is applied to the translation
     * layer so the next browse returns localized labels.
     *
     * @param values the full map wrapper from the browser, or null for none
     */
    @IpcChannel("freja:settings")
    public void settings(WebModels.SettingsMap values) {
        WebSettings settings = settings();
        if (settings == null || values == null) {
            return;
        }
        String before = settings.string("language", "");
        settings.replace(values.values());
        String after = settings.string("language", "");
        if (!before.equals(after)) {
            app.applyLanguage(after);
        }
    }

    /**
     * Returns application info for the about dialog.
     *
     * @return the about payload
     */
    @IpcChannel("freja:about")
    public Map<String, Object> about() {
        Library library = library();
        Map<String, Object> info = new HashMap<>();
        info.put("name", FrejaWeb.NAME);
        info.put("version", FrejaWeb.VERSION);
        info.put("frameworkVersion", "0.1");
        info.put("platform", System.getProperty("os.name", "unknown"));
        info.put("arch", System.getProperty("os.arch", "unknown"));
        info.put("java", System.getProperty("java.version", "unknown"));
        info.put("library", library == null ? "library.db" : library.location().toString());
        info.put("stats", library == null ? null : library.stats());
        return info;
    }

    // ----------------------------------------------------------------------------
    // Photo actions
    // ----------------------------------------------------------------------------

    /**
     * Asks the scanner to decode the given photos again.
     *
     * @param request the identifiers
     */
    @IpcChannel("freja:reimport")
    public void reimport(WebModels.PhotoIds request) {
        LibraryScanner scanner = scanner();
        Library library = library();
        if (scanner == null || library == null || request == null) {
            return;
        }
        for (long photoId : request.ids()) {
            library.photo(photoId).ifPresent(scanner::reimport);
        }
    }

    /**
     * Removes the given photos from the catalog; the files on disk stay.
     *
     * @param request the identifiers
     */
    @IpcChannel("freja:removePhotos")
    public void removePhotos(WebModels.PhotoIds request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        library.deletePhotos(request.ids());
        refreshBootstrap();
    }

    /**
     * Deletes the photo files from disk and drops their catalog rows.
     *
     * @param request the identifiers
     * @return a summary with the counts
     */
    @IpcChannel("freja:deleteFromDisk")
    public Map<String, Object> deleteFromDisk(WebModels.PhotoIds request) {
        Library library = library();
        int deleted = 0;
        int failed = 0;
        List<Long> deletedIds = new ArrayList<>();
        if (library != null && request != null) {
            for (long photoId : request.ids()) {
                Optional<Photo> photo = library.photo(photoId);
                if (photo.isEmpty()) {
                    failed++;
                    continue;
                }
                try {
                    if (photo.get().archived()) {
                        // An archive entry has no file of its own; it can only be removed from the catalog.
                        continue;
                    }
                    if (Files.deleteIfExists(photo.get().path())) {
                        deletedIds.add(photoId);
                        deleted++;
                    } else {
                        failed++;
                    }
                } catch (IOException e) {
                    failed++;
                }
            }
            // File I/O above stays per photo, but the catalog rows go in one transaction.
            library.deletePhotos(deletedIds);
        }
        refreshBootstrap();
        Map<String, Object> summary = new HashMap<>();
        summary.put("deleted", deleted);
        summary.put("failed", failed);
        return summary;
    }

    /**
     * Opens the folder which contains the photo in the desktop's file manager.
     *
     * @param photoId the photo identifier
     */
    @IpcChannel("freja:reveal")
    public void reveal(long photoId) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.photo(photoId).ifPresent(photo -> revealPath(photo.origin()));
    }

    /**
     * Opens the folder in the desktop's file manager.
     *
     * @param folderId the folder identifier
     */
    @IpcChannel("freja:revealFolder")
    public void revealFolder(long folderId) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.folder(folderId).ifPresent(folder -> revealPath(folder.path()));
    }

    private void revealPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path target = Files.isDirectory(path) ? path : path.getParent();
            if (target != null) {
                Desktop.getDesktop().open(target.toFile());
            }
        } catch (IOException | RuntimeException e) {
            I.error("freja", new IOException("Cannot open the folder " + path, e));
        }
    }

    /**
     * Copies the given photo paths to the clipboard.
     *
     * @param request the identifiers
     * @return the number of paths copied
     */
    @IpcChannel("freja:copyPaths")
    public int copyPaths(WebModels.PhotoIds request) {
        Library library = library();
        if (library == null || request == null || request.ids().isEmpty()) {
            return 0;
        }
        StringBuilder text = new StringBuilder();
        for (long photoId : request.ids()) {
            library.photo(photoId).ifPresent(photo -> {
                if (text.length() > 0) {
                    text.append(System.lineSeparator());
                }
                text.append(photo.path());
            });
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        } catch (IllegalStateException e) {
            I.error("freja", new IllegalStateException("The clipboard is not available", e));
        }
        return request.ids().size();
    }

    /**
     * Sets the favorite flag on the given photos.
     *
     * @param request the identifiers and the flag
     */
    @IpcChannel("freja:favorite")
    public void favorite(WebModels.FavoriteRequest request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        library.setFavorites(request.ids(), request.value());
        refreshBootstrap();
    }

    /**
     * Sets the rating of one photo.
     *
     * @param photoId the photo identifier
     * @param value the star count
     */
    @IpcChannel("freja:rating")
    public void rating(long photoId, int value) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.setRating(photoId, value);
        refreshBootstrap();
    }

    /**
     * Sets the caption of one photo.
     *
     * @param photoId the photo identifier
     * @param value the caption, or null to clear
     */
    @IpcChannel("freja:caption")
    public void caption(long photoId, String value) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.setCaption(photoId, value);
        refreshBootstrap();
    }

    // ----------------------------------------------------------------------------
    // Folders
    // ----------------------------------------------------------------------------

    /**
     * Opens the operating system's native folder picker in front of the application window and
     * returns the chosen directory. The call blocks until the user approves or cancels; apocrypha
     * runs IPC handlers on virtual threads, so the webview event loop keeps running.
     *
     * @return the path, or null when canceled
     */
    @IpcChannel("freja:pickFolder")
    public String pickFolder() {
        Window window = app == null ? null : app.window();
        return window == null ? null : window.chooseFolder("フォルダを選択");
    }

    /**
     * Registers a directory.
     *
     * @param directory the directory path
     * @return the registered folder
     */
    @IpcChannel("freja:folder.add")
    public Folder addFolder(String directory) {
        Library library = library();
        if (library == null) {
            return null;
        }
        Folder folder = library.addFolder(Path.of(directory));
        refreshBootstrap();
        return folder;
    }

    /**
     * Renames a folder.
     *
     * @param folderId the folder identifier
     * @param name the new name
     */
    @IpcChannel("freja:folder.rename")
    public void renameFolder(long folderId, String name) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.renameFolder(folderId, name);
        refreshBootstrap();
    }

    /**
     * Removes the given folders from the catalog.
     *
     * @param request the identifiers
     */
    @IpcChannel("freja:folder.remove")
    public void removeFolders(WebModels.FolderIds request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        LibraryScanner scanner = scanner();
        if (scanner != null) {
            // Also drops the watch registrations for the removed trees.
            scanner.removeFolders(request.ids());
        } else {
            library.removeFolders(request.ids());
        }
        refreshBootstrap();
    }

    /**
     * Scans the given folders in the background.
     *
     * @param request the identifiers
     */
    @IpcChannel("freja:folder.rescan")
    public void rescanFolders(WebModels.FolderIds request) {
        LibraryScanner scanner = scanner();
        if (scanner == null || request == null) {
            return;
        }
        for (long folderId : request.ids()) {
            scanner.scanFolder(folderId);
        }
    }

    /**
     * Enables or disables a folder as a scan target.
     *
     * @param folderId the folder identifier
     * @param enabled whether the folder is scanned
     */
    @IpcChannel("freja:folder.scanEnabled")
    public void scanEnabled(long folderId, boolean enabled) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.setFolderScanEnabled(folderId, enabled);
        refreshBootstrap();
    }

    /**
     * Removes every folder from the catalog.
     */
    @IpcChannel("freja:folder.clear")
    public void clearFolders() {
        Library library = library();
        LibraryScanner scanner = scanner();
        if (library == null || scanner == null) {
            return;
        }
        scanner.clearFolders();
        refreshBootstrap();
    }

    /**
     * Sets the display order of one folder.
     *
     * @param folderId the folder identifier
     * @param sort the order name
     */
    @IpcChannel("freja:folder.sort")
    public void sort(long folderId, String sort) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.setFolderSort(folderId, resolveOrder(sort));
        refreshBootstrap();
    }

    // ----------------------------------------------------------------------------
    // Tags
    // ----------------------------------------------------------------------------

    /**
     * Returns all tags.
     *
     * @return the tags, or an empty list before the catalog opens
     */
    @IpcChannel("freja:tag.list")
    public List<Tag> list() {
        Library library = library();
        return library == null ? List.of() : library.tags();
    }

    /**
     * Returns all tag links.
     *
     * @return rows with source, target and name, or empty before the catalog opens
     */
    @IpcChannel("freja:tag.allLinks")
    public List<Map<String, Object>> linked() {
        Library library = library();
        if (library == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tag tag : library.tags()) {
            for (Tag linked : library.linkedTargets(tag.id())) {
                Map<String, Object> row = new HashMap<>();
                row.put("source", tag.id());
                row.put("target", linked.id());
                row.put("name", linked.name());
                out.add(row);
            }
        }
        return out;
    }

    /**
     * Creates a tag.
     *
     * @param name the tag name
     * @return the created tag identifier, or 0 before the catalog opens
     */
    @IpcChannel("freja:tag.add")
    public long add(String name) {
        Library library = library();
        return library == null ? 0 : library.addTag(name);
    }

    /**
     * Renames a tag.
     *
     * @param tagId the tag identifier
     * @param name the new name
     */
    @IpcChannel("freja:tag.rename")
    public void rename(long tagId, String name) {
        Library library = library();
        if (library == null) {
            return;
        }
        library.renameTag(tagId, name);
    }

    /**
     * Deletes the given tags.
     *
     * @param request the identifiers
     */
    @IpcChannel("freja:tag.delete")
    public void delete(WebModels.TagIds request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        library.deleteTags(request.ids());
    }

    /**
     * Replaces a piece of the name in every matching tag.
     *
     * @param request the full request with ids, from and to
     * @return the number of tags renamed
     */
    @IpcChannel("freja:tag.bulkRename")
    public int bulkRename(WebModels.BulkRename request) {
        Library library = library();
        if (library == null || request == null) {
            return 0;
        }
        return library.bulkRenameTags(request.ids(), request.from(), request.to());
    }

    /**
     * Sets the tag links of a tag.
     *
     * @param request the source and its targets
     */
    @IpcChannel("freja:tag.setLinks")
    public void setLinks(WebModels.SetLinks request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        library.setTagLinks(request.id(), request.targets());
    }

    /**
     * Attaches the given tag names to a folder.
     *
     * @param request the folder and its tag names
     */
    @IpcChannel("freja:tag.setFolderTags")
    public void setFolderTags(WebModels.SetFolderTags request) {
        Library library = library();
        if (library == null || request == null) {
            return;
        }
        library.setFolderTags(request.folderId(), request.names());
        refreshBootstrap();
    }

    // ----------------------------------------------------------------------------
    // Scanning
    // ----------------------------------------------------------------------------

    /**
     * Rescans every registered folder in the background.
     */
    @IpcChannel("freja:scan.all")
    public void scan() {
        LibraryScanner scanner = scanner();
        if (scanner != null) {
            scanner.scanAll();
        }
    }

    // ----------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------

    private SortOrder resolveOrder(String sort) {
        try {
            return SortOrder.valueOf(sort);
        } catch (IllegalArgumentException | NullPointerException e) {
            return SortOrder.NAME;
        }
    }

    /**
     * Parses the folder identifier with a blank guard. Sinobu converts a JSON null into an empty
     * string before Long construction, so the raw parseLong would throw a NumberFormatException
     * whenever the browser browses without a selected folder.
     *
     * @param value the string value from the request, or null
     * @return the parsed identifier, or null for a blank value
     */
    private Long parseFolderId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
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

    private void refreshBootstrap() {
        invalidate();
        if (app != null) {
            app.refreshBootstrap();
        }
    }
}
