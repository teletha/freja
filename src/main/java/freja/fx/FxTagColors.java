/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import javafx.scene.paint.Color;

/** Stable colours for tags, mirroring the Swing edition's {@code TagColors} palette. */
final class FxTagColors {

    private static final int[] PALETTE = {0x5473B5, 0x528A67, 0xB88947, 0xB85F52, 0x7A66A3, 0x438391};

    private FxTagColors() {
    }

    /** @return the palette entry for a tag name, as a CSS string */
    static String css(String name) {
        return String.format("#%06X", PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)]);
    }

    /** @return the palette entry for a tag name, as a JavaFX paint */
    static Color paint(String name) {
        return Color.web(css(name));
    }
}
