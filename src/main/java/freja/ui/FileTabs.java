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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** The pinned folders of the file manager, persisted as a vertical tab strip. */
final class FileTabs {

    interface Listener {
        void changed();
    }

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FileTabs.class);

    private static final String COUNT = "count";

    private final List<FileTab> tabs = new ArrayList<>();

    private final Listener listener;

    private int selected = -1;

    FileTabs(Listener listener) {
        this.listener = listener;
        load();
    }

    List<FileTab> tabs() {
        return tabs;
    }

    int selectedIndex() {
        return selected;
    }

    FileTab active() {
        return selected < 0 || selected >= tabs.size() ? null : tabs.get(selected);
    }

    void add(Path folder) {
        tabs.add(new FileTab(folder, null));
        selected = tabs.size() - 1;
        changed();
    }

    void remove(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        tabs.remove(index);
        if (tabs.isEmpty()) {
            selected = -1;
        } else {
            selected = Math.min(selected, tabs.size() - 1);
        }
        changed();
    }

    void select(int index) {
        if (index < 0 || index >= tabs.size() || selected == index) {
            return;
        }
        selected = index;
        changed();
    }

    void rename(int index, String name) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        tabs.get(index).displayName(name);
        changed();
    }

    void move(int from, int to) {
        if (from < 0 || from >= tabs.size() || to < 0 || to >= tabs.size() || from == to) {
            return;
        }
        FileTab tab = tabs.remove(from);
        tabs.add(to, tab);
        selected = to;
        changed();
    }

    void navigate(Path path) {
        FileTab tab = active();
        if (tab == null) {
            return;
        }
        tab.goTo(path);
        changed();
    }

    boolean back() {
        FileTab tab = active();
        if (tab == null || !tab.back()) {
            return false;
        }
        changed();
        return true;
    }

    boolean forward() {
        FileTab tab = active();
        if (tab == null || !tab.forward()) {
            return false;
        }
        changed();
        return true;
    }

    private void changed() {
        save();
        listener.changed();
    }

    private void load() {
        int count = PREFERENCES.getInt(COUNT, 0);
        for (int i = 0; i < count; i++) {
            String root = PREFERENCES.get("root." + i, null);
            if (root == null || root.isBlank()) {
                continue;
            }
            try {
                FileTab tab = new FileTab(Path.of(root), PREFERENCES.get("name." + i, null));
                String current = PREFERENCES.get("current." + i, null);
                if (current != null && !current.isBlank()) {
                    tab.goTo(Path.of(current));
                }
                tabs.add(tab);
            } catch (InvalidPathException ignored) {
                // A stale or platform-specific path is simply skipped.
            }
        }
        selected = tabs.isEmpty() ? -1 : Math.max(0, Math.min(PREFERENCES.getInt("selected", 0), tabs.size() - 1));
    }

    private void save() {
        PREFERENCES.putInt(COUNT, tabs.size());
        PREFERENCES.putInt("selected", selected);
        for (int i = 0; i < tabs.size(); i++) {
            FileTab tab = tabs.get(i);
            PREFERENCES.put("root." + i, tab.root().toString());
            PREFERENCES.put("name." + i, tab.displayName());
            PREFERENCES.put("current." + i, tab.current().toString());
        }
    }
}
