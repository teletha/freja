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

import java.awt.Color;

/** Stable colours for tags, shared by the manager, the picker and the grid headers. */
final class TagColors {

    private static final Color[] PALETTE = {
            new Color(0x5473B5), new Color(0x528A67), new Color(0xB88947),
            new Color(0xB85F52), new Color(0x7A66A3), new Color(0x438391)
    };

    private TagColors() {
    }

    static Color of(String name) {
        return PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];
    }
}
