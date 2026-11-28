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
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;

/**
 * Fluent border helpers for the ARX components. Implementing this interface lets a component
 * describe its spacing in the same chained expression which builds it, instead of exposing a
 * mutable setter.
 * <p>
 * Swing gives a component a single border slot, so padding, the visible border and margin are
 * composed into one border, from the outside in: margin, border, padding. All three reserve space
 * inside the component; the background therefore reaches out behind them. Call
 * {@link #border(Border)} first, then wrap it with {@link #margin(int)} and {@link #padding(int)}:
 * those two keep whatever border is already installed. A later {@code border} call replaces the
 * whole composition.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface LayoutHelper<C extends JComponent> {
    /**
     * Applies the same margin on all four sides.
     *
     * @param size the margin in pixels
     * @return this component
     */
    default C margin(int size) {
        return margin(size, size, size, size);
    }

    /**
     * Applies one margin to the top and bottom and another to the left and right.
     *
     * @param vertical the top and bottom margin in pixels
     * @param horizontal the left and right margin in pixels
     * @return this component
     */
    default C margin(int vertical, int horizontal) {
        return margin(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Applies the side margins and lets the left and right share one value.
     *
     * @param top the top margin in pixels
     * @param horizontal the left and right margin in pixels
     * @param bottom the bottom margin in pixels
     * @return this component
     */
    default C margin(int top, int horizontal, int bottom) {
        return margin(top, horizontal, bottom, horizontal);
    }

    /**
     * Applies an independent margin to each side. The empty border is placed outside the border
     * already installed, so a visible border and the padding inside it survive the call.
     * <p>
     * The order matches {@link java.awt.Insets} (top, right, bottom, left), whereas
     * {@link BorderFactory#createEmptyBorder(int, int, int, int)} takes top, left, bottom, right;
     * the two middle values are swapped below.
     * </p>
     *
     * @param top the top margin in pixels
     * @param right the right margin in pixels
     * @param bottom the bottom margin in pixels
     * @param left the left margin in pixels
     * @return this component
     */
    default C margin(int top, int right, int bottom, int left) {
        JComponent component = (JComponent) this;
        component.setBorder(outside(BorderFactory.createEmptyBorder(top, left, bottom, right), component.getBorder()));
        return self();
    }

    /**
     * Applies the same padding on all four sides.
     *
     * @param size the padding in pixels
     * @return this component
     */
    default C padding(int size) {
        return padding(size, size, size, size);
    }

    /**
     * Applies one padding to the top and bottom and another to the left and right.
     *
     * @param vertical the top and bottom padding in pixels
     * @param horizontal the left and right padding in pixels
     * @return this component
     */
    default C padding(int vertical, int horizontal) {
        return padding(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Applies the side padding and lets the left and right share one value.
     *
     * @param top the top padding in pixels
     * @param horizontal the left and right padding in pixels
     * @param bottom the bottom padding in pixels
     * @return this component
     */
    default C padding(int top, int horizontal, int bottom) {
        return padding(top, horizontal, bottom, horizontal);
    }

    /**
     * Applies an independent padding to each side. The empty border is placed inside the border
     * already installed, so a visible border is painted outside the space reserved here.
     *
     * @param top the top padding in pixels
     * @param right the right padding in pixels
     * @param bottom the bottom padding in pixels
     * @param left the left padding in pixels
     * @return this component
     */
    default C padding(int top, int right, int bottom, int left) {
        JComponent component = (JComponent) this;
        component.setBorder(inside(component.getBorder(), BorderFactory.createEmptyBorder(top, left, bottom, right)));
        return self();
    }

    /**
     * Sets the visible border, replacing any previously composed margin and padding. Call this
     * before {@link #margin(int)} and {@link #padding(int)} to keep all three layers.
     *
     * @param border the border to paint, or null for no border
     * @return this component
     */
    default C border(Border border) {
        ((JComponent) this).setBorder(border);
        return self();
    }

    /**
     * Sets a plain line border of the same width on all four sides.
     *
     * @param color the line colour
     * @param thickness the line width in pixels
     * @return this component
     */
    default C border(Color color, int thickness) {
        return border(color, thickness, thickness, thickness, thickness);
    }

    /**
     * Sets a matte border with an independent width on each side, e.g. a single hairline on the
     * left edge. The widths follow {@link java.awt.Insets} order (top, right, bottom, left) and a
     * zero width suppresses that side. Like {@link #border(Border)}, this replaces any previously
     * composed margin and padding, so call it before those helpers.
     *
     * @param color the line colour
     * @param top the top width in pixels
     * @param right the right width in pixels
     * @param bottom the bottom width in pixels
     * @param left the left width in pixels
     * @return this component
     */
    default C border(Color color, int top, int right, int bottom, int left) {
        // createMatteBorder takes top, left, bottom, right, unlike Insets and the helpers here.
        return border(BorderFactory.createMatteBorder(top, left, bottom, right, color));
    }

    /** Wraps the current border with an empty border on the outside. */
    private static Border outside(Border empty, Border current) {
        return current == null ? empty : BorderFactory.createCompoundBorder(empty, current);
    }

    /** Wraps the current border with an empty border on the inside. */
    private static Border inside(Border current, Border empty) {
        return current == null ? empty : BorderFactory.createCompoundBorder(current, empty);
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
