/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.file;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One vertical tab in the file manager: a pinned folder with its own navigation history. */
public final class FileTab {

    private final String id = UUID.randomUUID().toString();

    private final Path root;

    private String displayName;

    /** A name chosen with "タブ名を変更" wins over the folder name. */
    private boolean customName;

    private final List<FileLocation> history = new ArrayList<>();

    private int index = -1;

    public FileTab(Path root, String displayName) {
        this(FileLocation.folder(root), displayName, root.toAbsolutePath().normalize());
    }

    private FileTab(FileLocation location, String displayName, Path root) {
        this.root = root;
        this.current = location;
        customName = displayName != null && !displayName.isBlank();
        this.displayName = customName ? displayName : nameOf(location);
        history.add(this.current);
        index = 0;
    }

    /** A tab which starts on the virtual home page rather than a real folder. */
    public static FileTab home() {
        return new FileTab(FileLocation.Home.INSTANCE, null, null);
    }

    private static String nameOf(FileLocation location) {
        return location instanceof FileLocation.Folder folder ? nameOf(folder.path()) : "HOME";
    }

    private FileLocation current;

    public String id() {
        return id;
    }

    public Path root() {
        return root;
    }

    public boolean isHome() {
        return root == null;
    }

    public FileLocation current() {
        return current;
    }

    public String displayName() {
        return displayName;
    }

    public boolean customName() {
        return customName;
    }

    public void displayName(String value) {
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
            displayName = current instanceof FileLocation.Folder folder
                    ? nameOf(folder.path()) : "HOME";
        }
    }

    /**
     * Creates an independent copy at the same location, including the navigation history, so a
     * duplicated tab can be browsed separately from the original.
     */
    public FileTab duplicate() {
        FileTab copy = root == null ? home() : new FileTab(root, customName ? displayName : null);
        copy.history.clear();
        copy.history.addAll(history);
        copy.index = index;
        copy.current = current;
        copy.followCurrentName();
        return copy;
    }

    public boolean canBack() {
        return index > 0;
    }

    public boolean canForward() {
        return index < history.size() - 1;
    }

    public void goTo(Path path) {
        goTo(FileLocation.folder(path));
    }

    public void goTo(FileLocation location) {
        if (location.equals(current())) {
            return;
        }
        while (history.size() - 1 > index) {
            history.remove(history.size() - 1);
        }
        history.add(location);
        index = history.size() - 1;
        current = location;
        followCurrentName();
    }

    public boolean back() {
        if (!canBack()) {
            return false;
        }
        current = history.get(--index);
        followCurrentName();
        return true;
    }

    public boolean forward() {
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
