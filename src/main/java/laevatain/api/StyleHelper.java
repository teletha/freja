/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain.api;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Fluent style helpers for the ARX components. A component implements this alongside
 * {@link LayoutHelper} so its colours, text and visibility can be set in the same chained expression
 * which builds it.
 * <p>
 * Each helper changes only the property it names: setting the family keeps the current style and
 * size, and setting the size keeps the current family and style. The size is used as given; a host
 * which supports a user-selected scale should scale the value before calling {@link #fontSize(int)}.
 * Visibility is a style concern here, while the enabled state lives in {@link DisableHelper}.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface StyleHelper<C extends JComponent> {
    /**
     * Sets the background colour. Null restores the look and feel's default painting.
     *
     * @param color the background colour
     * @return this component
     */
    default C background(Color color) {
        ((JComponent) this).setBackground(color);
        return self();
    }

    /**
     * Sets the foreground colour, which is the colour Swing labels use for their text.
     *
     * @param color the font colour
     * @return this component
     */
    default C foreground(Color color) {
        ((JComponent) this).setForeground(color);
        return self();
    }

    /**
     * An alias for {@link #foreground(Color)} for callers which think in terms of text colour.
     *
     * @param color the font colour
     * @return this component
     */
    default C fontColor(Color color) {
        return foreground(color);
    }

    /**
     * Changes the font family while keeping the current style and size. Blank or null values leave
     * the font alone rather than installing an unusable family.
     *
     * @param family the font family name, e.g. one from
     *        {@link java.awt.GraphicsEnvironment#getAvailableFontFamilyNames()}
     * @return this component
     */
    default C fontFamily(String family) {
        if (family != null && !family.isBlank()) {
            JComponent component = (JComponent) this;
            // deriveFont with an attribute map preserves style, size and any other font attributes
            // the host installed; constructing a new Font would silently discard them.
            component.setFont(font(component).deriveFont(Map.of(TextAttribute.FAMILY, family)));
        }
        return self();
    }

    /**
     * Changes the font size while keeping the current family and style. Values below one are raised
     * to one, since a non-positive size cannot produce a font.
     *
     * @param size the font size in points
     * @return this component
     */
    default C fontSize(int size) {
        JComponent component = (JComponent) this;
        component.setFont(font(component).deriveFont((float) Math.max(1, size)));
        return self();
    }

    /**
     * Shows or hides the component. A hidden component keeps its validation state and its enabled
     * flag, so showing it again does not rebuild its layout or its interaction state.
     *
     * @param visible true to show
     * @return this component
     */
    default C visible(boolean visible) {
        ((JComponent) this).setVisible(visible);
        return self();
    }

    /**
     * Sets whether the component paints its own background. Transparent children let a parent's
     * background show through, which is needed inside tinted cards and rounded panels.
     *
     * @param opaque true to paint the background
     * @return this component
     */
    default C opaque(boolean opaque) {
        ((JComponent) this).setOpaque(opaque);
        return self();
    }

    /** The current font, falling back to the look and feel's label font when none is installed. */
    private static Font font(JComponent component) {
        Font font = component.getFont();
        if (font != null) {
            return font;
        }
        Font fallback = UIManager.getFont("Label.font");
        return fallback == null ? new Font(Font.DIALOG, Font.PLAIN, 12) : fallback;
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
