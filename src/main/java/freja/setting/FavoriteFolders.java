/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.setting;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Favourite paths, each optionally bound to a single number key on the home page. */
public final class FavoriteFolders {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FavoriteFolders.class);

    private static final String COUNT = "count";

    private record Item(Path path, int shortcut) {
    }

    private FavoriteFolders() {
    }

    public static List<Path> folders() {
        return items().stream().map(Item::path).toList();
    }

    /** The assigned number key, or -1 when none was chosen. */
    public static int shortcut(Path folder) {
        for (Item item : items()) {
            if (item.path().equals(folder)) {
                return item.shortcut();
            }
        }
        return -1;
    }

    public static boolean contains(Path folder) {
        return folders().stream().anyMatch(favorite -> favorite.equals(folder));
    }

    public static void add(Path folder) {
        if (folder == null || contains(folder)) {
            return;
        }
        List<Item> items = items();
        items.add(new Item(folder.toAbsolutePath().normalize(), -1));
        save(items);
    }

    public static void remove(Path folder) {
        List<Item> items = items();
        if (items.removeIf(item -> item.path().equals(folder))) {
            save(items);
        }
    }

    public static void toggle(Path folder) {
        if (contains(folder)) {
            remove(folder);
        } else {
            add(folder);
        }
    }

    /** Binds a number key to one favourite, clearing the same key from any other entry. */
    public static void shortcut(Path folder, int key) {
        List<Item> items = items();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int value = item.path().equals(folder) ? key : (item.shortcut() == key ? -1 : item.shortcut());
            items.set(i, new Item(item.path(), value));
        }
        save(items);
    }

    private static List<Item> items() {
        List<Item> items = new ArrayList<>();
        int count = PREFERENCES.getInt(COUNT, 0);
        for (int i = 0; i < count; i++) {
            String value = PREFERENCES.get("folder." + i, null);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                String saved = PREFERENCES.get("key." + i, "");
                int key = saved.isBlank() ? -1 : Integer.parseInt(saved);
                items.add(new Item(Path.of(value), key >= 0 && key <= 9 ? key : -1));
            } catch (InvalidPathException | NumberFormatException ignored) {
                // A stale or platform-specific path is simply skipped.
            }
        }
        return items;
    }

    private static void save(List<Item> items) {
        PREFERENCES.putInt(COUNT, items.size());
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            PREFERENCES.put("folder." + i, item.path().toString());
            PREFERENCES.put("key." + i, item.shortcut() < 0 ? "" : Integer.toString(item.shortcut()));
        }
        try {
            PREFERENCES.flush();
        } catch (Exception ignored) {
        }
    }
}
