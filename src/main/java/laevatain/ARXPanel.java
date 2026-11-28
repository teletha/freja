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

import java.awt.LayoutManager;
import javax.swing.JPanel;

import laevatain.api.ComponentHelper;

/**
 * A {@link JPanel} with the fluent ARX helper API. It is a drop-in replacement: no look or layout
 * default is changed, so callers keep full control and can describe the panel in one constructor
 * chain, e.g. {@code new ARXPanel(new BorderLayout()).opaque(false).background(color)}.
 */
public class ARXPanel extends JPanel implements ComponentHelper<ARXPanel> {
    public ARXPanel() {
        super();
    }

    public ARXPanel(LayoutManager layout) {
        super(layout);
    }

    public ARXPanel(boolean isDoubleBuffered) {
        super(isDoubleBuffered);
    }

    public ARXPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
    }
}
