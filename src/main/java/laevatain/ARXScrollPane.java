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

import java.awt.Color;
import java.awt.Component;
import javax.swing.JScrollPane;
import javax.swing.border.Border;

import laevatain.api.ComponentHelper;

/**
 * The ARX scroll container: a borderless {@link JScrollPane} whose viewport follows the pane's own
 * background and opacity. Plain Swing scroll panes regain a hairline border and an opaque default
 * viewport on every look and feel change, which breaks the flat ARX surfaces; this component keeps
 * the chrome flat across theme changes.
 * <p>
 * Horizontal scrolling is off and the wheel advances by one navigation row (28 pixels), matching
 * {@link ARXNavList}. {@link #setBackground(Color)} and {@link #setOpaque(boolean)} propagate to the
 * viewport, so the fluent helpers inherited from {@link ComponentHelper} style both layers at once.
 * </p>
 */
public final class ARXScrollPane extends JScrollPane implements ComponentHelper<ARXScrollPane> {
    /** The wheel increment shared by the navigation list and the folder rail. */
    private static final int UNIT_INCREMENT = 28;

    /** True once a caller has installed a visible border, which a theme change must not remove. */
    private boolean customBorder;

    public ARXScrollPane() {
        this(null);
    }

    public ARXScrollPane(Component view) {
        super(view);
        border(null).opaque(true);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        // The viewport exists by now; the overrides below keep it in step with later style changes.
        getViewport().setBackground(getBackground());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Changing the theme reinstalls the default border and viewport. Restore the flat chrome,
        // unless the caller deliberately installed a border of its own.
        if (!customBorder) {
            border(null);
        }
        setOpaque(isOpaque());
        setBackground(getBackground());
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
    }

    @Override
    public void setBorder(Border border) {
        super.setBorder(border);
        customBorder = border != null;
    }

    @Override
    public void setBackground(Color color) {
        super.setBackground(color);
        if (getViewport() != null) {
            getViewport().setBackground(color);
        }
    }

    @Override
    public void setOpaque(boolean opaque) {
        super.setOpaque(opaque);
        if (getViewport() != null) {
            getViewport().setOpaque(opaque);
        }
    }
}
