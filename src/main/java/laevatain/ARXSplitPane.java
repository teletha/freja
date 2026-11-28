/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain;

import java.awt.Component;
import javax.swing.JSplitPane;

import laevatain.api.ComponentHelper;

/**
 * A {@link JSplitPane} with the fluent ARX helper API. Drop-in like the other ARX wrappers: callers
 * set the divider and border through the helpers instead of scattered setters.
 */
public class ARXSplitPane extends JSplitPane implements ComponentHelper<ARXSplitPane> {
    public ARXSplitPane(int orientation) {
        super(orientation);
    }

    public ARXSplitPane(int orientation, Component left, Component right) {
        super(orientation, left, right);
    }
}
