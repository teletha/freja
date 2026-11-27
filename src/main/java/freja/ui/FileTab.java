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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One vertical tab in the file manager: a pinned folder with its own navigation history. */
final class FileTab {

    private final String id = UUID.randomUUID().toString();

    private final Path root;

    private String displayName;

    /** A name chosen with "タブ名を変更" wins over the folder name. */
    private boolean customName;

    private final List<Path> history = new ArrayList<>();

    private int index = -1;

    FileTab(Path root, String displayName) {
        this.root = root.toAbsolutePath().normalize();
        this.current = this.root;
        customName = displayName != null && !displayName.isBlank();
        this.displayName = customName ? displayName : nameOf(this.root);
        history.add(this.root);
        index = 0;
    }

    private Path current;

    String id() {
        return id;
    }

    Path root() {
        return root;
    }

    Path current() {
        return current;
    }

    String displayName() {
        return displayName;
    }

    boolean customName() {
        return customName;
    }

    void displayName(String value) {
        if (value != null && !value.isBlank()) {
            displayName = value;
            customName = true;
        }
    }

    private static String nameOf(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    /** Keeps the tab label in step with the folder being browsed, unless the user renamed it. */
    private void followCurrentName() {
        if (!customName) {
            displayName = nameOf(current);
        }
    }

    /**
     * Creates an independent copy at the same location, including the navigation history, so a
     * duplicated tab can be browsed separately from the original.
     */
    FileTab duplicate() {
        FileTab copy = new FileTab(root, customName ? displayName : null);
        copy.history.clear();
        copy.history.addAll(history);
        copy.index = index;
        copy.current = current;
        copy.followCurrentName();
        return copy;
    }

    boolean canBack() {
        return index > 0;
    }

    boolean canForward() {
        return index < history.size() - 1;
    }

    void goTo(Path path) {
        Path next = path.toAbsolutePath().normalize();
        if (next.equals(current())) {
            return;
        }
        while (history.size() - 1 > index) {
            history.remove(history.size() - 1);
        }
        history.add(next);
        index = history.size() - 1;
        current = next;
        followCurrentName();
    }

    boolean back() {
        if (!canBack()) {
            return false;
        }
        current = history.get(--index);
        followCurrentName();
        return true;
    }

    boolean forward() {
        if (!canForward()) {
            return false;
        }
        current = history.get(++index);
        followCurrentName();
        return true;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
