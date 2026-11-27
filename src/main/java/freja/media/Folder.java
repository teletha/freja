/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media;

import java.nio.file.Path;
import java.util.List;

/**
 * A directory registered with the library.
 * <p>
 * A folder added by the user is explicit. The descendants discovered below it are managed
 * automatically and keep their own catalog rows so every directory can have its own photos,
 * display order and counts.
 * </p>
 */
public record Folder(long id, Path path, String name, int photoCount, long addedAt, long lastScanAt,
        PhotoFilter.SortOrder sort, boolean explicit, List<String> tags, boolean scanEnabled) {
}
