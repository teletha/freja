/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.model;

/**
 * A label that can be attached to folders.
 *
 * @param id the catalog identifier
 * @param name the display name
 * @param folderCount how many folders carry this tag
 */
public record Tag(long id, String name, int folderCount) {
}
