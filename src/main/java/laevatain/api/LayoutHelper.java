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

import javax.swing.BorderFactory;
import javax.swing.JComponent;

/**
 * Fluent spacing helpers for the ARX panels. Implementing this interface lets a panel take its
 * inset in the same chained expression which builds it, instead of exposing a mutable setter.
 * <p>
 * A margin is represented by an empty {@link javax.swing.border.Border}: Swing reserves the space
 * inside the component, so the panel's background still extends behind it. Setting a margin
 * replaces any border the panel already had; callers which need a visible border as well must
 * install a compound border afterwards.
 * </p>
 *
 * @param <C> the concrete panel type returned by every helper, so chains keep the caller's type
 */
public interface LayoutHelper<C extends JComponent> {
    /**
     * Applies the same inset on all four sides.
     *
     * @param size the inset in pixels
     * @return this panel
     */
    default C margin(int size) {
        return margin(size, size, size, size);
    }

    /**
     * Applies one inset to the top and bottom and another to the left and right.
     *
     * @param vertical the top and bottom inset in pixels
     * @param horizontal the left and right inset in pixels
     * @return this panel
     */
    default C margin(int vertical, int horizontal) {
        return margin(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Applies the side insets and lets the left and right share one value.
     *
     * @param top the top inset in pixels
     * @param horizontal the left and right inset in pixels
     * @param bottom the bottom inset in pixels
     * @return this panel
     */
    default C margin(int top, int horizontal, int bottom) {
        return margin(top, horizontal, bottom, horizontal);
    }

    /**
     * Applies an independent inset to each side. This is the single method which touches the
     * border; the shorter overloads only normalise their arguments and delegate here.
     * <p>
     * The order matches {@link java.awt.Insets} (top, right, bottom, left), whereas
     * {@link BorderFactory#createEmptyBorder(int, int, int, int)} takes top, left, bottom, right;
     * the two middle values are swapped below.
     * </p>
     *
     * @param top the top inset in pixels
     * @param right the right inset in pixels
     * @param bottom the bottom inset in pixels
     * @param left the left inset in pixels
     * @return this panel
     */
    default C margin(int top, int right, int bottom, int left) {
        ((JComponent) this).setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return (C) this;
    }
}
