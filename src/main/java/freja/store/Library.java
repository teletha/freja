/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import freja.model.Folder;
import freja.model.LibraryStats;
import freja.model.Photo;
import freja.model.PhotoFilter;
import freja.model.Tag;
import freja.util.PathUtil;
import freja.util.SmartTags;

/**
 * The SQLite-backed photo catalog.
 * <p>
 * A single connection is guarded by the intrinsic lock of this object. SQLite is fast enough for
 * the reads the UI makes, and keeping one writer avoids {@code SQLITE_BUSY} between the scanner
 * and the rest of the application. All thumbnails are stored in a separate table so browsing the
 * catalog never reads megabyte-sized blobs by accident.
 * </p>
 */
public final class Library implements AutoCloseable {

    /** How far back the "recent" view reaches. */
    private static final long RECENT_WINDOW = 30L * 24 * 60 * 60 * 1000;

    /**
     * Bumped whenever thumbnail generation changes. Rows below this revision are decoded again by
     * the next scan, which is how existing libraries pick up a new thumbnail pipeline.
     */
    private static final int THUMBNAIL_REVISION = 9;

    /** A thumbnail stored in the database. */
    public record Thumbnail(byte[] data, int width, int height) {
    }

    /** The file attributes a scan compares against the catalog. */
    public record FileStamp(long size, long modifiedAt) {
    }

    /** A string greater than every possible path key sharing the same prefix. */
    private static final String KEY_MAX = new String(Character.toChars(Character.MAX_CODE_POINT));

    private static String upperBound(String prefix) {
        return prefix + KEY_MAX;
    }

    /** Raised when a database operation fails. */
    public static class LibraryException extends RuntimeException {

        public LibraryException(String message) {
            super(message);
        }

        public LibraryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Path database;

    /** The single writer connection, guarded by this object's intrinsic lock. */
    private final Connection connection;

    /**
     * A second connection for reads from the UI. SQLite's WAL mode lets it read while the scanner
     * commits a batch, so a full catalog refresh on the event dispatch thread never waits for the
     * writer lock. An in-memory database cannot be shared, so it uses the writer connection.
     */
    private final Connection reader;

    /** Guards the reader connection; the same lock as the writer when the connection is shared. */
    private final Object readerLock;

    /**
     * Opens a library database, creating it when necessary.
     *
     * @param database the SQLite file, or {@code :memory:}
     * @throws SQLException when the database cannot be opened
     */
    public Library(Path database) throws SQLException {
        this.database = database;
        try {
            // Loading by name also works when the service descriptor of the driver is not visible,
            // for example in an IDE that builds its classpath from an older project configuration.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The SQLite JDBC driver (org.xerial:sqlite-jdbc) is not on the classpath.", e);
        }
        boolean memory = ":memory:".equals(database.toString());
        if (!memory) {
            Path parent = database.toAbsolutePath().getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new SQLException("Cannot create the library directory: " + parent, e);
                }
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
        } else {
            this.connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        }
        initialize();
        if (memory) {
            this.reader = this.connection;
            this.readerLock = this;
        } else {
            this.reader = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            this.readerLock = new Object();
            try (Statement statement = reader.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
            } catch (SQLException e) {
                throw new SQLException("Cannot prepare the library reader connection", e);
            }
        }
    }

    /**
     * Returns the database file.
     *
     * @return the database location
     */
    public Path location() {
        return database;
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA temp_store=MEMORY");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        path TEXT NOT NULL,
                        path_key TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL DEFAULT '',
                        added_at INTEGER NOT NULL,
                        last_scan_at INTEGER NOT NULL DEFAULT 0,
                        sort_order TEXT NOT NULL DEFAULT 'NAME',
                        explicit INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                        path TEXT NOT NULL,
                        path_key TEXT NOT NULL UNIQUE,
                        file_name TEXT NOT NULL,
                        extension TEXT NOT NULL,
                        byte_size INTEGER NOT NULL,
                        modified_at INTEGER NOT NULL,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        captured_at INTEGER NOT NULL,
                        camera_make TEXT,
                        camera_model TEXT,
                        lens TEXT,
                        exposure TEXT,
                        aperture REAL,
                        iso INTEGER,
                        focal_length REAL,
                        orientation INTEGER NOT NULL DEFAULT 1,
                        favorite INTEGER NOT NULL DEFAULT 0,
                        rating INTEGER NOT NULL DEFAULT 0,
                        caption TEXT,
                        thumbnail_revision INTEGER NOT NULL DEFAULT 9,
                        imported_at INTEGER NOT NULL,
                        scan_token TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS thumbnails (
                        photo_id INTEGER PRIMARY KEY REFERENCES photos(id) ON DELETE CASCADE,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        data BLOB NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS folder_tags (
                        folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                        tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                        PRIMARY KEY (folder_id, tag_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS photos_folder ON photos(folder_id, captured_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS photos_captured ON photos(captured_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS photos_name ON photos(file_name COLLATE NOCASE)");
            statement.execute("CREATE INDEX IF NOT EXISTS photos_imported ON photos(imported_at DESC)");
        }
        ensureColumn("folders", "sort_order", "TEXT NOT NULL DEFAULT 'NAME'");
        ensureColumn("folders", "explicit", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn("folders", "name", "TEXT NOT NULL DEFAULT ''");
        ensureColumn("photos", "thumbnail_revision", "INTEGER NOT NULL DEFAULT 1");
        normalizeExistingTags();
        backfillFolderNames();
        migrateThumbnailRevision();
    }

    /** Converts tags stored with full-width names to the half-width spelling used now. */
    private void normalizeExistingTags() throws SQLException {
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT id, name FROM tags")) {
            while (results.next()) {
                ids.add(results.getLong("id"));
                names.add(results.getString("name"));
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            String normalized = SmartTags.normalize(names.get(i));
            if (normalized.isEmpty() || normalized.equals(names.get(i))) {
                continue;
            }
            Long existing = null;
            try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM tags WHERE name=? AND id<>?")) {
                statement.setString(1, normalized);
                statement.setLong(2, id);
                try (ResultSet results = statement.executeQuery()) {
                    if (results.next()) {
                        existing = results.getLong(1);
                    }
                }
            }
            if (existing == null) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE tags SET name=? WHERE id=?")) {
                    statement.setString(1, normalized);
                    statement.setLong(2, id);
                    statement.executeUpdate();
                }
            } else {
                // Two spellings of the same tag: merge the assignments into the existing row.
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO folder_tags(folder_id, tag_id) SELECT folder_id, ? FROM folder_tags WHERE tag_id=?")) {
                    statement.setLong(1, existing);
                    statement.setLong(2, id);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tags WHERE id=?")) {
                    statement.setLong(1, id);
                    statement.executeUpdate();
                }
            }
        }
    }

    /**
     * Fills the folder name of libraries created before names were stored separately.
     */
    private void backfillFolderNames() throws SQLException {
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery("SELECT id, path FROM folders WHERE name IS NULL OR name=''")) {
            while (results.next()) {
                ids.add(results.getLong("id"));
                names.add(PathUtil.folderName(Path.of(results.getString("path"))));
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE folders SET name=? WHERE id=?")) {
            for (int i = 0; i < ids.size(); i++) {
                statement.setString(1, names.get(i));
                statement.setLong(2, ids.get(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (int i = 0; i < ids.size(); i++) {
            applySmartTags(ids.get(i), names.get(i));
        }
    }

    /**
     * Drops thumbnails produced before the WebP thumbnail format was introduced. The next scan
     * regenerates them.
     */
    private void migrateThumbnailRevision() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM thumbnails WHERE photo_id IN (SELECT id FROM photos WHERE thumbnail_revision < 4)");
        }
    }

    /**
     * Adds a column to an existing table when an older library is opened.
     *
     * @param table the table name, which is a compile-time constant here
     * @param column the column name
     * @param definition the column definition
     */
    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (results.next()) {
                if (column.equalsIgnoreCase(results.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    /**
     * Registers a directory.
     * <p>
     * Registering the same directory twice returns the existing entry. A directory below an
     * already registered scan root is accepted as a managed folder; the root scan discovers those
     * directories automatically, so selecting one in the dialog simply navigates to it. A new
     * parent above an existing root is rejected because it would scan the same files twice.
     * </p>
     *
     * @param path the directory
     * @return the registered folder
     */
    public synchronized Folder addFolder(Path path) {
        Path normalized = PathUtil.normalize(path);
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Not a directory: " + normalized);
        }
        String key = PathUtil.key(normalized);
        List<Folder> folders = folders();
        for (Folder folder : folders) {
            if (PathUtil.key(folder.path()).equals(key)) {
                return folder;
            }
        }
        for (Folder folder : folders) {
            if (!folder.explicit()) {
                continue;
            }
            String existing = PathUtil.key(folder.path());
            if (key.startsWith(PathUtil.prefix(existing))) {
                // The root scan already owns this directory. Register it as a managed folder so
                // it has its own row even before the next scan walks the tree.
                return register(normalized, key, false);
            }
            if (existing.startsWith(PathUtil.prefix(key))) {
                throw new IllegalArgumentException("The folder overlaps an existing library folder: " + folder.path());
            }
        }
        return register(normalized, key, true);
    }

    /** Registers a folder and attaches the smart tags found in its name. */
    private Folder register(Path normalized, String key, boolean explicit) {
        Folder folder = insertFolder(normalized, key, explicit);
        applySmartTags(folder.id(), folder.name());
        return folder(folder.id()).orElse(folder);
    }

    /**
     * Registers a directory discovered by the scanner.
     *
     * @param path the directory
     * @return the existing or newly created managed folder
     */
    public synchronized Folder ensureFolder(Path path) {
        Path normalized = PathUtil.normalize(path);
        String key = PathUtil.key(normalized);
        Folder existing = findFolderByKey(key);
        return existing != null ? existing : register(normalized, key, false);
    }

    private String folderPath(long folderId) {
        try (PreparedStatement statement = reader.prepareStatement("SELECT path FROM folders WHERE id=?")) {
            statement.setLong(1, folderId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the folder path", e);
        }
    }

    private Folder findFolderByKey(String key) {
        String sql = """
                SELECT f.id, f.path, f.name, f.added_at, f.last_scan_at, f.sort_order, f.explicit,
                       (SELECT COUNT(*) FROM photos p WHERE p.folder_id = f.id) AS photo_count
                FROM folders f
                WHERE f.path_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? mapFolder(results, connection) : null;
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the folder: " + key, e);
        }
    }

    private Folder insertFolder(Path normalized, String key, boolean explicit) throws LibraryException {
        long now = System.currentTimeMillis();
        String name = PathUtil.folderName(normalized);
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO folders(path, path_key, name, added_at, sort_order, explicit) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, normalized.toString());
            statement.setString(2, key);
            statement.setString(3, name);
            statement.setLong(4, now);
            statement.setString(5, PhotoFilter.SortOrder.NAME.name());
            statement.setInt(6, explicit ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Folder(keys.getLong(1), normalized, name, 0, now, 0, PhotoFilter.SortOrder.NAME, explicit, List.of());
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to add the folder: " + normalized, e);
        }
    }


    /**
     * Returns all registered folders with their photo counts.
     *
     * @return the folders ordered by name, with the path as a tie-breaker
     */
    public List<Folder> folders() {
        synchronized (readerLock) {
            return foldersInternal();
        }
    }

    private List<Folder> foldersInternal() {
        String sql = """
                SELECT f.id, f.path, f.name, f.added_at, f.last_scan_at, f.sort_order, f.explicit, COUNT(p.id) AS photo_count
                FROM folders f
                LEFT JOIN photos p ON p.folder_id = f.id
                GROUP BY f.id
                ORDER BY f.name COLLATE NOCASE, f.path COLLATE NOCASE
                """;
        try (Statement statement = reader.createStatement(); ResultSet results = statement.executeQuery(sql)) {
            List<Folder> folders = new ArrayList<>();
            while (results.next()) {
                folders.add(mapFolder(results, reader));
            }
            return folders;
        } catch (SQLException e) {
            throw new LibraryException("Failed to read folders", e);
        }
    }

    private Folder mapFolder(ResultSet results, Connection source) throws SQLException {
        Path path = Path.of(results.getString("path"));
        String name = results.getString("name");
        if (name == null || name.isBlank()) {
            name = PathUtil.folderName(path);
        }
        long folderId = results.getLong("id");
        return new Folder(folderId, path, name, results.getInt("photo_count"), results.getLong("added_at"),
                results.getLong("last_scan_at"), parseSort(results.getString("sort_order")),
                results.getInt("explicit") != 0, folderTags(folderId, source));
    }

    private List<String> folderTags(long folderId, Connection source) throws SQLException {
        String sql = """
                SELECT t.name FROM tags t
                JOIN folder_tags ft ON ft.tag_id = t.id
                WHERE ft.folder_id = ? ORDER BY t.name COLLATE NOCASE
                """;
        try (PreparedStatement statement = source.prepareStatement(sql)) {
            statement.setLong(1, folderId);
            try (ResultSet results = statement.executeQuery()) {
                List<String> tags = new ArrayList<>();
                while (results.next()) {
                    tags.add(results.getString(1));
                }
                return List.copyOf(tags);
            }
        }
    }

    /**
     * Applies smart tags to every folder that has bracketed names. Tags assigned by hand are kept.
     *
     * @return how many folders gained at least one tag
     */
    public synchronized int applySmartTagsToAllFolders() {
        int updated = 0;
        for (Folder folder : folders()) {
            List<String> smart = SmartTags.fromFolderName(folder.name());
            if (smart.isEmpty()) {
                continue;
            }
            Set<String> merged = new LinkedHashSet<>();
            try {
                merged.addAll(folderTags(folder.id(), connection));
            } catch (SQLException e) {
                throw new LibraryException("Failed to read folder tags", e);
            }
            int before = merged.size();
            merged.addAll(smart);
            if (merged.size() > before) {
                setFolderTags(folder.id(), merged);
                updated++;
            }
        }
        return updated;
    }

    /** Adds the tags parsed from a folder name without dropping tags the user assigned by hand. */
    private void applySmartTags(long folderId, String folderName) {
        List<String> smart = SmartTags.fromFolderName(folderName);
        if (smart.isEmpty()) {
            return;
        }
        List<String> merged = new ArrayList<>(smart);
        try {
            for (String tag : folderTags(folderId, connection)) {
                if (!merged.contains(tag)) {
                    merged.add(tag);
                }
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read folder tags", e);
        }
        setFolderTags(folderId, merged);
    }

    private static PhotoFilter.SortOrder parseSort(String value) {
        if (value != null) {
            try {
                return PhotoFilter.SortOrder.valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return PhotoFilter.SortOrder.NAME;
    }

    /** Lists every tag with the number of folders carrying it. */
    public List<Tag> tags() {
        synchronized (readerLock) {
            return tagsInternal();
        }
    }

    private List<Tag> tagsInternal() {
        String sql = """
                SELECT t.id, t.name, COUNT(ft.folder_id) AS folder_count
                FROM tags t LEFT JOIN folder_tags ft ON ft.tag_id = t.id
                GROUP BY t.id ORDER BY t.name COLLATE NOCASE
                """;
        try (Statement statement = reader.createStatement(); ResultSet results = statement.executeQuery(sql)) {
            List<Tag> tags = new ArrayList<>();
            while (results.next()) {
                tags.add(new Tag(results.getLong("id"), results.getString("name"), results.getInt("folder_count")));
            }
            return List.copyOf(tags);
        } catch (SQLException e) {
            throw new LibraryException("Failed to read tags", e);
        }
    }

    /** Returns the names of the tags attached to one folder. */
    public synchronized List<String> folderTags(long folderId) {
        synchronized (readerLock) {
            try {
                return folderTags(folderId, connection);
            } catch (SQLException e) {
                throw new LibraryException("Failed to read folder tags", e);
            }
        }
    }

    /** Creates a tag. */
    public synchronized long addTag(String name) {
        String value = tagName(name);
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tags(name) VALUES(?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, value);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new LibraryException("A tag named '" + value + "' already exists", e);
        }
    }

    /** Renames a tag; the name must remain unique. */
    public synchronized void renameTag(long id, String name) {
        String value = tagName(name);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE tags SET name=? WHERE id=?")) {
            statement.setString(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("A tag named '" + value + "' already exists", e);
        }
    }

    /** Deletes tags everywhere; their folder assignments go with them. */
    public synchronized void deleteTags(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tags WHERE id=?")) {
                for (Long id : ids) {
                    statement.setLong(1, id);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new LibraryException("Failed to delete tags", e);
        } finally {
            autoCommit(true);
        }
    }

    /**
     * Replaces a piece of the name in every selected tag. The whole operation is rejected when a
     * result would be blank or collide with another tag, so a bulk edit never leaves a half-applied
     * rename behind.
     *
     * @return how many tags were renamed
     */
    public synchronized int bulkRenameTags(Collection<Long> ids, String from, String to) {
        if (from == null || from.isEmpty()) {
            throw new IllegalArgumentException("置換する文字列を入力してください");
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Set<Long> selected = new HashSet<>(ids);
        List<Tag> current = tags();
        Map<String, Long> others = new HashMap<>();
        for (Tag tag : current) {
            if (!selected.contains(tag.id())) {
                others.put(tag.name().toLowerCase(Locale.ROOT), tag.id());
            }
        }
        List<Tag> changes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Tag tag : current) {
            if (!selected.contains(tag.id())) {
                continue;
            }
            String next = tag.name().replace(from, to == null ? "" : to).trim();
            if (next.isEmpty()) {
                throw new IllegalArgumentException("名前が空になるタグがあります: " + tag.name());
            }
            String key = next.toLowerCase(Locale.ROOT);
            if ((others.containsKey(key) && !selected.contains(others.get(key))) || !seen.add(key)) {
                throw new LibraryException("タグ '" + next + "' は既に存在します");
            }
            changes.add(new Tag(tag.id(), next, 0));
        }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE tags SET name=? WHERE id=?")) {
                for (Tag tag : changes) {
                    statement.setString(1, tag.name());
                    statement.setLong(2, tag.id());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new LibraryException("Failed to rename tags", e);
        } finally {
            autoCommit(true);
        }
        return changes.size();
    }

    /** Replaces the complete set of tags on a folder, creating tags that do not exist yet. */
    public synchronized void setFolderTags(long folderId, Collection<String> names) {
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String value = SmartTags.normalize(name);
                if (value.isEmpty()) {
                    continue;
                }
                if (seen.add(value.toLowerCase(Locale.ROOT))) {
                    values.add(value);
                }
            }
        }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM folder_tags WHERE folder_id=?")) {
                statement.setLong(1, folderId);
                statement.executeUpdate();
            }
            for (String value : values) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO folder_tags(folder_id, tag_id) VALUES(?,?)")) {
                    statement.setLong(1, folderId);
                    statement.setLong(2, tagId(value));
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new LibraryException("Failed to update folder tags", e);
        } finally {
            autoCommit(true);
        }
    }

    private long tagId(String name) throws SQLException {
        String value = SmartTags.normalize(name);
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM tags WHERE name=?")) {
            statement.setString(1, value);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return results.getLong(1);
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO tags(name) VALUES(?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, value);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    private static String tagName(String name) {
        String value = SmartTags.normalize(name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("タグ名を入力してください");
        }
        return value;
    }

    private void autoCommit(boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException ignored) {
        }
    }

    /**
     * Stores the display order for one folder.
     *
     * @param folderId the folder identifier
     * @param sort the display order
     */
    public synchronized void setFolderSort(long folderId, PhotoFilter.SortOrder sort) {
        PhotoFilter.SortOrder value = sort == null ? PhotoFilter.SortOrder.NAME : sort;
        try (PreparedStatement statement = connection.prepareStatement("UPDATE folders SET sort_order=? WHERE id=?")) {
            statement.setString(1, value.name());
            statement.setLong(2, folderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the folder display order", e);
        }
    }

    /**
     * Changes a folder's display name. The directory on disk is not touched; the name stored here
     * is what the sidebar and the grid headers show.
     *
     * @param folderId the folder identifier
     * @param name the new display name
     */
    public synchronized void renameFolder(long folderId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A folder name cannot be blank");
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE folders SET name=? WHERE id=?")) {
            statement.setString(1, name.trim());
            statement.setLong(2, folderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to rename the folder", e);
        }
        applySmartTags(folderId, name.trim());
    }

    /**
     * Finds a registered folder by its database identifier.
     *
     * @param id a folder identifier
     * @return the folder, if it exists
     */
    public Optional<Folder> folder(long id) {
        synchronized (readerLock) {
            return folders().stream().filter(folder -> folder.id() == id).findFirst();
        }
    }

    /**
     * Removes a folder and every catalog entry below it. Files on disk are not touched.
     *
     * @param folderId the folder identifier
     */
    public synchronized void removeFolder(long folderId) {
        Optional<Folder> found = folder(folderId);
        if (found.isEmpty()) {
            return;
        }
        String key = PathUtil.key(found.get().path());
        String prefix = PathUtil.prefix(key);
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM folders WHERE path_key=? OR (path_key>=? AND path_key<?)")) {
            statement.setString(1, key);
            statement.setString(2, prefix);
            statement.setString(3, upperBound(prefix));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to remove the folder", e);
        }
    }

    /**
     * Removes managed folders below a directory which disappeared from disk. The explicit scan
     * root is kept so a temporarily unavailable drive can be reconnected.
     *
     * @param path the deleted directory
     * @return the number of removed folders
     */
    public synchronized int removeAutoFoldersUnder(Path path) {
        String key = PathUtil.key(path);
        String prefix = PathUtil.prefix(key);
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM folders WHERE explicit=0 AND (path_key=? OR (path_key>=? AND path_key<?))")) {
            statement.setString(1, key);
            statement.setString(2, prefix);
            statement.setString(3, upperBound(prefix));
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to remove folders below " + path, e);
        }
    }

    /**
     * Removes managed folders which a scan no longer found.
     *
     * @param folderIds folder identifiers
     * @return the number of removed folders
     */
    public synchronized int deleteFolders(java.util.Collection<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM folders WHERE id=? AND explicit=0")) {
            int count = 0;
            for (long id : folderIds) {
                statement.setLong(1, id);
                if (statement.executeUpdate() > 0) {
                    count++;
                }
            }
            return count;
        } catch (SQLException e) {
            throw new LibraryException("Failed to prune folders", e);
        }
    }

    /**
     * Returns photos matching a filter.
     *
     * @param filter the query
     * @return matching photos in the requested order
     */
    public List<Photo> photos(PhotoFilter filter) {
        synchronized (readerLock) {
            return photosInternal(filter);
        }
    }

    private List<Photo> photosInternal(PhotoFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.*, f.path AS folder_path
                FROM photos p
                JOIN folders f ON f.id = p.folder_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (filter.folderId() != null) {
            if (filter.includeSubfolders()) {
                String folderPath = folderPath(filter.folderId());
                if (folderPath == null) {
                    sql.append(" AND 1 = 0");
                } else {
                    String prefix = PathUtil.prefix(PathUtil.key(Path.of(folderPath)));
                    sql.append(" AND p.path_key >= ? AND p.path_key < ?");
                    parameters.add(prefix);
                    parameters.add(upperBound(prefix));
                }
            } else {
                sql.append(" AND p.folder_id = ?");
                parameters.add(filter.folderId());
            }
        }
        if (filter.favoritesOnly()) {
            sql.append(" AND p.favorite = 1");
        }
        if (filter.recentOnly()) {
            sql.append(" AND p.imported_at >= ?");
            parameters.add(Instant.now().minus(RECENT_WINDOW, ChronoUnit.MILLIS).toEpochMilli());
        }
        if (filter.search() != null) {
            String search = "%" + PathUtil.likeValue(filter.search()) + "%";
            sql.append(" AND f.name LIKE ? ESCAPE '\\'");
            parameters.add(search);
        }
        if (!filter.tagIds().isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM folder_tags ft WHERE ft.folder_id = p.folder_id AND ft.tag_id IN (");
            for (int i = 0; i < filter.tagIds().size(); i++) {
                sql.append(i == 0 ? "?" : ",?");
                parameters.add(filter.tagIds().get(i));
            }
            sql.append("))");
        }
        sql.append(switch (filter.sort()) {
        case DATE_OLDEST -> " ORDER BY p.captured_at ASC, p.file_name COLLATE NOCASE ASC";
        case NAME -> " ORDER BY p.file_name COLLATE NOCASE ASC, p.captured_at DESC";
        case SIZE -> " ORDER BY p.byte_size DESC, p.file_name COLLATE NOCASE ASC";
        default -> " ORDER BY p.captured_at DESC, p.file_name COLLATE NOCASE ASC";
        });

        try (PreparedStatement statement = reader.prepareStatement(sql.toString())) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            try (ResultSet results = statement.executeQuery()) {
                List<Photo> photos = new ArrayList<>();
                while (results.next()) {
                    photos.add(mapPhoto(results));
                }
                return photos;
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read photos", e);
        }
    }

    /**
     * Finds a photo by identifier.
     *
     * @param id a photo identifier
     * @return the photo, if it exists
     */
    public Optional<Photo> photo(long id) {
        synchronized (readerLock) {
            return photoInternal(id);
        }
    }

    private Optional<Photo> photoInternal(long id) {
        String sql = """
                SELECT p.*, f.path AS folder_path
                FROM photos p
                JOIN folders f ON f.id = p.folder_id
                WHERE p.id = ?
                """;
        try (PreparedStatement statement = reader.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapPhoto(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the photo", e);
        }
    }

    /**
     * Loads the thumbnail of a photo.
     *
     * @param photoId a photo identifier
     * @return the thumbnail, if one has been generated
     */
    public Optional<Thumbnail> thumbnail(long photoId) {
        synchronized (readerLock) {
            return thumbnailInternal(photoId);
        }
    }

    private Optional<Thumbnail> thumbnailInternal(long photoId) {
        try (PreparedStatement statement = reader.prepareStatement("SELECT width, height, data FROM thumbnails WHERE photo_id=?")) {
            statement.setLong(1, photoId);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return Optional.of(new Thumbnail(results.getBytes("data"), results.getInt("width"), results.getInt("height")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the thumbnail", e);
        }
    }

    /**
     * Returns aggregate counters.
     *
     * @return library statistics
     */
    public LibraryStats stats() {
        synchronized (readerLock) {
            return statsInternal();
        }
    }

    private LibraryStats statsInternal() {
        long recentSince = Instant.now().minus(RECENT_WINDOW, ChronoUnit.MILLIS).toEpochMilli();
        try (PreparedStatement statement = reader.prepareStatement("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(favorite), 0) AS favorites,
                       COALESCE(SUM(CASE WHEN imported_at >= ? THEN 1 ELSE 0 END), 0) AS recent,
                       COALESCE(SUM(byte_size), 0) AS bytes,
                       (SELECT COUNT(*) FROM folders) AS folders
                FROM photos
                """)) {
            statement.setLong(1, recentSince);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return new LibraryStats(results.getInt("total"), results.getInt("favorites"), results.getInt("recent"), results.getInt("folders"), results.getLong("bytes"));
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read statistics", e);
        }
    }

    /**
     * Tests whether a file is already cataloged with the given size and modification time.
     *
     * @param key a normalized path key
     * @param size the file size
     * @param modifiedAt the modification time
     * @return true when the catalog row is current
     */
    public synchronized boolean isCurrent(String key, long size, long modifiedAt) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM photos WHERE path_key=? AND byte_size=? AND modified_at=? AND thumbnail_revision>=?")) {
            statement.setString(1, key);
            statement.setLong(2, size);
            statement.setLong(3, modifiedAt);
            statement.setInt(4, THUMBNAIL_REVISION);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to test the catalog entry", e);
        }
    }

    /**
     * Loads every up-to-date file under a scan root in one query so the scanner can compare stamps
     * in memory instead of issuing one {@code SELECT} per file.
     *
     * @param root the scanned directory tree
     * @return the catalog stamp for each current photo, keyed by normalized path key
     */
    public synchronized Map<String, FileStamp> currentFiles(Path root) {
        String prefix = PathUtil.prefix(PathUtil.key(root));
        String sql = """
                SELECT path_key, byte_size, modified_at FROM photos
                WHERE thumbnail_revision >= ? AND path_key >= ? AND path_key < ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, THUMBNAIL_REVISION);
            statement.setString(2, prefix);
            statement.setString(3, upperBound(prefix));
            Map<String, FileStamp> stamps = new HashMap<>();
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    stamps.put(results.getString(1), new FileStamp(results.getLong(2), results.getLong(3)));
                }
            }
            return stamps;
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the catalog stamps for " + root, e);
        }
    }

    /**
     * Marks a group of catalog rows as seen by the current full scan. All keys must belong to the
     * same folder, which lets one statement replace hundreds of individual updates.
     *
     * @param keys normalized path keys
     * @param token the scan token
     * @param folderId the folder containing the files
     */
    public synchronized void touchBatch(Collection<String> keys, String token, long folderId) {
        if (keys.isEmpty() || token == null) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        String sql = "UPDATE photos SET scan_token=?, folder_id=? WHERE path_key IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.setLong(2, folderId);
            int index = 3;
            for (String key : keys) {
                statement.setString(index++, key);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to touch catalog entries", e);
        }
    }

    /**
     * Marks a catalog row as seen by the current full scan.
     *
     * @param key a normalized path key
     * @param token the scan token
     */
    public synchronized void touch(String key, String token) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE photos SET scan_token=? WHERE path_key=?")) {
            statement.setString(1, token);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to touch the catalog entry", e);
        }
    }

    /**
     * Marks a row as seen and moves it to the folder which actually contains the file.
     *
     * @param key a normalized path key
     * @param token the scan token, or null to leave the current token untouched
     * @param folderId the folder containing the file
     */
    public synchronized void touch(String key, String token, long folderId) {
        String sql = token == null ? "UPDATE photos SET folder_id=? WHERE path_key=?" : "UPDATE photos SET folder_id=?, scan_token=? WHERE path_key=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, folderId);
            if (token == null) {
                statement.setString(2, key);
            } else {
                statement.setString(2, token);
                statement.setString(3, key);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the catalog entry", e);
        }
    }

    /**
     * Starts a full scan and returns its token.
     *
     * @param folderId the folder being scanned
     * @return a token which must be passed back to {@link #finishScan(long, String)}
     */
    public synchronized String beginScan(long folderId) {
        return UUID.randomUUID().toString();
    }

    /**
     * Finishes a full scan and removes rows which were not seen.
     *
     * @param folderId the folder that was scanned
     * @param token the token returned by {@link #beginScan(long)}
     * @return the number of removed rows
     */
    public synchronized int finishScan(long folderId, String token) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM photos WHERE folder_id=? AND (scan_token IS NULL OR scan_token <> ?)")) {
            statement.setLong(1, folderId);
            statement.setString(2, token);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to finish the scan", e);
        }
    }

    /**
     * Finishes a recursive scan of a directory tree and removes rows which were not seen.
     *
     * @param root the scanned directory
     * @param token the token returned by {@link #beginScan(long)}
     * @return the number of removed rows
     */
    public synchronized int finishScanTree(Path root, String token) {
        String prefix = PathUtil.prefix(PathUtil.key(root));
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM photos WHERE (scan_token IS NULL OR scan_token <> ?) AND path_key >= ? AND path_key < ?")) {
            statement.setString(1, token);
            statement.setString(2, prefix);
            statement.setString(3, upperBound(prefix));
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to finish the scan of " + root, e);
        }
    }

    /**
     * Records that a folder was scanned now.
     *
     * @param folderId the folder identifier
     */
    public synchronized void markFolderScanned(long folderId) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE folders SET last_scan_at=? WHERE id=?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, folderId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the folder", e);
        }
    }

    /** One catalog write waiting to be committed. */
    public record PhotoSave(long folderId, Path file, long size, long modifiedAt,
            PhotoImporter.AnalyzedPhoto analyzed, String token) {
    }

    /**
     * Inserts or updates a catalog row and its thumbnail in its own transaction.
     *
     * @param folderId the owning folder
     * @param file the image file
     * @param size the file size at scan time
     * @param modifiedAt the modification time at scan time
     * @param analyzed the decoded metadata, or null for a file that could not be decoded
     * @param token the current scan token, or null for a watch event
     * @return the photo identifier
     */
    public synchronized long savePhoto(long folderId, Path file, long size, long modifiedAt, PhotoImporter.AnalyzedPhoto analyzed, String token) {
        return savePhoto(new PhotoSave(folderId, file, size, modifiedAt, analyzed, token));
    }

    /**
     * Commits one write with its own transaction. Used by watch events, which are too sparse to
     * justify a batch.
     *
     * @param save the write to perform
     * @return the photo identifier
     */
    public synchronized long savePhoto(PhotoSave save) {
        try {
            connection.setAutoCommit(false);
            long id = savePhotoInternal(save);
            connection.commit();
            return id;
        } catch (SQLException e) {
            rollback();
            throw new LibraryException("Failed to save the photo: " + save.file(), e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Commits many writes in one transaction. An initial scan saves tens of thousands of photos,
     * and one commit per photo is a substantial cost even in WAL mode.
     *
     * @param saves the writes to perform
     */
    public synchronized void savePhotoBatch(List<PhotoSave> saves) {
        if (saves.isEmpty()) {
            return;
        }
        try {
            connection.setAutoCommit(false);
            for (PhotoSave save : saves) {
                savePhotoInternal(save);
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new LibraryException("Failed to save a batch of " + saves.size() + " photos", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private long savePhotoInternal(PhotoSave save) throws SQLException {
        long folderId = save.folderId();
        Path file = save.file();
        long size = save.size();
        long modifiedAt = save.modifiedAt();
        PhotoImporter.AnalyzedPhoto analyzed = save.analyzed();
        String token = save.token();
        Path normalized = PathUtil.normalize(file);
        String key = PathUtil.key(normalized);
        Exif.Data exif = analyzed == null ? Exif.Data.EMPTY : analyzed.exif();
        long capturedAt = analyzed == null ? modifiedAt : analyzed.capturedAt();
        int width = analyzed == null ? 0 : analyzed.width();
        int height = analyzed == null ? 0 : analyzed.height();
        long now = System.currentTimeMillis();
        {
            long photoId;
            try (PreparedStatement find = connection.prepareStatement("SELECT id FROM photos WHERE path_key=?")) {
                find.setString(1, key);
                try (ResultSet results = find.executeQuery()) {
                    photoId = results.next() ? results.getLong(1) : -1;
                }
            }
            if (photoId < 0) {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO photos(folder_id, path, path_key, file_name, extension, byte_size, modified_at,
                            width, height, captured_at, camera_make, camera_model, lens, exposure, aperture, iso,
                            focal_length, orientation, thumbnail_revision, imported_at, scan_token)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    bindCommon(insert, folderId, normalized, key, size, modifiedAt, exif, capturedAt, width, height);
                    insert.setInt(19, THUMBNAIL_REVISION);
                    insert.setLong(20, now);
                    insert.setString(21, token);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        photoId = keys.getLong(1);
                    }
                }
            } else {
                // User-owned properties (favorite, rating, caption) are deliberately not touched.
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE photos SET folder_id=?, path=?, file_name=?, extension=?, byte_size=?, modified_at=?,
                            width=?, height=?, captured_at=?, camera_make=?, camera_model=?, lens=?, exposure=?,
                            aperture=?, iso=?, focal_length=?, orientation=?, thumbnail_revision=?, scan_token=?
                        WHERE id=?
                        """)) {
                    update.setLong(1, folderId);
                    update.setString(2, normalized.toString());
                    update.setString(3, normalized.getFileName().toString());
                    update.setString(4, PathUtil.extension(normalized));
                    update.setLong(5, size);
                    update.setLong(6, modifiedAt);
                    update.setInt(7, width);
                    update.setInt(8, height);
                    update.setLong(9, capturedAt);
                    update.setString(10, exif.make());
                    update.setString(11, exif.model());
                    update.setString(12, exif.lens());
                    update.setString(13, exif.exposure());
                    setDouble(update, 14, exif.aperture());
                    setInteger(update, 15, exif.iso());
                    setDouble(update, 16, exif.focalLength());
                    update.setInt(17, exif.orientation());
                    update.setInt(18, THUMBNAIL_REVISION);
                    update.setString(19, token);
                    update.setLong(20, photoId);
                    update.executeUpdate();
                }
            }
            if (analyzed != null) {
                try (PreparedStatement thumb = connection.prepareStatement("""
                        INSERT INTO thumbnails(photo_id, width, height, data) VALUES(?,?,?,?)
                        ON CONFLICT(photo_id) DO UPDATE SET width=excluded.width, height=excluded.height, data=excluded.data
                        """)) {
                    thumb.setLong(1, photoId);
                    thumb.setInt(2, analyzed.thumbnailWidth());
                    thumb.setInt(3, analyzed.thumbnailHeight());
                    thumb.setBytes(4, analyzed.thumbnail());
                    thumb.executeUpdate();
                }
            } else {
                try (PreparedStatement thumb = connection.prepareStatement("DELETE FROM thumbnails WHERE photo_id=?")) {
                    thumb.setLong(1, photoId);
                    thumb.executeUpdate();
                }
            }
            return photoId;
        }
    }

    private void bindCommon(PreparedStatement statement, long folderId, Path file, String key, long size, long modifiedAt, Exif.Data exif, long capturedAt, int width, int height) throws SQLException {
        statement.setLong(1, folderId);
        statement.setString(2, file.toString());
        statement.setString(3, key);
        statement.setString(4, file.getFileName().toString());
        statement.setString(5, PathUtil.extension(file));
        statement.setLong(6, size);
        statement.setLong(7, modifiedAt);
        statement.setInt(8, width);
        statement.setInt(9, height);
        statement.setLong(10, capturedAt);
        statement.setString(11, exif.make());
        statement.setString(12, exif.model());
        statement.setString(13, exif.lens());
        statement.setString(14, exif.exposure());
        setDouble(statement, 15, exif.aperture());
        setInteger(statement, 16, exif.iso());
        setDouble(statement, 17, exif.focalLength());
        statement.setInt(18, exif.orientation());
    }

    /**
     * Marks a catalog row stale so the next scan decodes it again.
     *
     * @param photoId a photo identifier
     */
    public synchronized void invalidate(long photoId) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE photos SET byte_size=-1 WHERE id=?")) {
            statement.setLong(1, photoId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to invalidate the photo", e);
        }
    }

    /**
     * Removes a photo from the catalog. The file on disk is not touched.
     *
     * @param photoId a photo identifier
     */
    public synchronized void deletePhoto(long photoId) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM photos WHERE id=?")) {
            statement.setLong(1, photoId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to remove the photo", e);
        }
    }

    /**
     * Removes a catalog row by path.
     *
     * @param key a normalized path key
     * @return true when a row was removed
     */
    public synchronized boolean deletePhoto(String key) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM photos WHERE path_key=?")) {
            statement.setString(1, key);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new LibraryException("Failed to remove the photo", e);
        }
    }

    /**
     * Removes every photo under a directory which is no longer present.
     *
     * @param folderId the owning folder
     * @param directory the deleted directory
     * @return the number of removed rows
     */
    public synchronized int deleteUnder(long folderId, Path directory) {
        String prefix = PathUtil.key(directory);
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, path_key FROM photos WHERE folder_id=?")) {
            statement.setLong(1, folderId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String key = results.getString("path_key");
                    if (key.equals(prefix) || key.startsWith(PathUtil.prefix(prefix))) {
                        ids.add(results.getLong("id"));
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM photos WHERE id=?")) {
                for (long id : ids) {
                    delete.setLong(1, id);
                    delete.addBatch();
                }
                if (!ids.isEmpty()) {
                    delete.executeBatch();
                }
            }
            return ids.size();
        } catch (SQLException e) {
            throw new LibraryException("Failed to remove photos below " + directory, e);
        }
    }

    /**
     * Sets the favorite flag.
     *
     * @param photoId a photo identifier
     * @param favorite the new value
     */
    public synchronized void setFavorite(long photoId, boolean favorite) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE photos SET favorite=? WHERE id=?")) {
            statement.setInt(1, favorite ? 1 : 0);
            statement.setLong(2, photoId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the favorite flag", e);
        }
    }

    /**
     * Sets the star rating.
     *
     * @param photoId a photo identifier
     * @param rating a value from 0 to 5
     */
    public synchronized void setRating(long photoId, int rating) {
        int value = Math.max(0, Math.min(5, rating));
        try (PreparedStatement statement = connection.prepareStatement("UPDATE photos SET rating=? WHERE id=?")) {
            statement.setInt(1, value);
            statement.setLong(2, photoId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the rating", e);
        }
    }

    /**
     * Sets the caption.
     *
     * @param photoId a photo identifier
     * @param caption the caption, or null to clear it
     */
    public synchronized void setCaption(long photoId, String caption) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE photos SET caption=? WHERE id=?")) {
            statement.setString(1, caption == null || caption.isBlank() ? null : caption.trim());
            statement.setLong(2, photoId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to update the caption", e);
        }
    }

    /**
     * Reads an application setting.
     *
     * @param key the setting name
     * @return the stored value, if any
     */
    public Optional<String> setting(String key) {
        synchronized (readerLock) {
            return settingInternal(key);
        }
    }

    private Optional<String> settingInternal(String key) {
        try (PreparedStatement statement = reader.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            statement.setString(1, key);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(results.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new LibraryException("Failed to read the setting " + key, e);
        }
    }

    /**
     * Stores an application setting.
     *
     * @param key the setting name
     * @param value the value
     */
    public synchronized void setting(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO settings(key, value) VALUES(?,?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LibraryException("Failed to store the setting " + key, e);
        }
    }

    private Photo mapPhoto(ResultSet results) throws SQLException {
        Double aperture = nullableDouble(results, "aperture");
        Double focalLength = nullableDouble(results, "focal_length");
        Integer iso = nullableInteger(results, "iso");
        return new Photo(results.getLong("id"), results.getLong("folder_id"), results.getString("folder_path"), Path.of(results.getString("path")), results.getString("file_name"), results.getString("extension"), results.getLong("byte_size"), results.getLong("modified_at"), results.getInt("width"), results.getInt("height"), results.getLong("captured_at"), results.getString("camera_make"), results.getString("camera_model"), results.getString("lens"), results.getString("exposure"), aperture, iso, focalLength, results.getInt("orientation"), results.getInt("favorite") != 0, results.getInt("rating"), results.getString("caption"), results.getLong("imported_at"));
    }

    private static Double nullableDouble(ResultSet results, String column) throws SQLException {
        double value = results.getDouble(column);
        return results.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet results, String column) throws SQLException {
        int value = results.getInt(column);
        return results.wasNull() ? null : value;
    }

    private static void setDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    @Override
    public synchronized void close() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA optimize");
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException ignored) {
        }
        try {
            if (reader != connection) {
                reader.close();
            }
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
