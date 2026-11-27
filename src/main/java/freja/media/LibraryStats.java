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

/**
 * Aggregate counters shown by the sidebar and status bar.
 */
public record LibraryStats(int photos, int favorites, int recent, int folders, long bytes) {
}
