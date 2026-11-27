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

    private final List<Path> history = new ArrayList<>();

    private int index = -1;

    FileTab(Path root, String displayName) {
        this.root = root.toAbsolutePath().normalize();
        this.current = this.root;
        Path name = this.root.getFileName();
        this.displayName = displayName == null || displayName.isBlank()
                ? (name == null ? this.root.toString() : name.toString())
                : displayName;
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

    void displayName(String value) {
        if (value != null && !value.isBlank()) {
            displayName = value;
        }
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
    }

    boolean back() {
        if (!canBack()) {
            return false;
        }
        current = history.get(--index);
        return true;
    }

    boolean forward() {
        if (!canForward()) {
            return false;
        }
        current = history.get(++index);
        return true;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
