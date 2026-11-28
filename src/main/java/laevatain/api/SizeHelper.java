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

import java.awt.Dimension;
import javax.swing.JComponent;

/**
 * Fluent sizing helpers for the ARX components. They replace repeated {@code setPreferredSize}
 * calls in constructor chains and make the intent of a fixed axis explicit.
 * <p>
 * A layout manager can stretch or shrink a component unless its minimum and maximum agree with the
 * preferred size, so {@link #fixedWidth(int)} and {@link #fixedHeight(int)} set all three together.
 * Passing null to a size helper restores the look and feel's default. Dimensions are copied on the
 * way in, so a caller cannot change a component later by mutating its own {@link Dimension}.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface SizeHelper<C extends JComponent> {
    /**
     * Sets the preferred size.
     *
     * @param width the preferred width in pixels
     * @param height the preferred height in pixels
     * @return this component
     */
    default C preferredSize(int width, int height) {
        return preferredSize(new Dimension(width, height));
    }

    /**
     * Sets the preferred size, copying the supplied dimension.
     *
     * @param size the preferred size, or null to restore the default
     * @return this component
     */
    default C preferredSize(Dimension size) {
        ((JComponent) this).setPreferredSize(size == null ? null : new Dimension(size));
        return self();
    }

    /**
     * Sets the minimum size.
     *
     * @param width the minimum width in pixels
     * @param height the minimum height in pixels
     * @return this component
     */
    default C minimumSize(int width, int height) {
        return minimumSize(new Dimension(width, height));
    }

    /**
     * Sets the minimum size, copying the supplied dimension.
     *
     * @param size the minimum size, or null to restore the default
     * @return this component
     */
    default C minimumSize(Dimension size) {
        ((JComponent) this).setMinimumSize(size == null ? null : new Dimension(size));
        return self();
    }

    /**
     * Sets the maximum size.
     *
     * @param width the maximum width in pixels
     * @param height the maximum height in pixels
     * @return this component
     */
    default C maximumSize(int width, int height) {
        return maximumSize(new Dimension(width, height));
    }

    /**
     * Sets the maximum size, copying the supplied dimension.
     *
     * @param size the maximum size, or null to restore the default
     * @return this component
     */
    default C maximumSize(Dimension size) {
        ((JComponent) this).setMaximumSize(size == null ? null : new Dimension(size));
        return self();
    }

    /**
     * Pins the width to one value while keeping the current preferred height. A layout can then
     * grow or shrink the component vertically but never horizontally.
     *
     * @param width the fixed width in pixels
     * @return this component
     */
    default C fixedWidth(int width) {
        JComponent component = (JComponent) this;
        Dimension current = component.getPreferredSize();
        int height = current == null ? 0 : current.height;
        // Call the helpers on this interface rather than chaining on C: a type variable bounded by
        // JComponent exposes Swing's deprecated no-arg minimumSize(), not these overloads.
        preferredSize(width, height);
        minimumSize(width, height);
        maximumSize(width, height);
        return self();
    }

    /**
     * Pins the height to one value while keeping the current preferred width.
     *
     * @param height the fixed height in pixels
     * @return this component
     */
    default C fixedHeight(int height) {
        JComponent component = (JComponent) this;
        Dimension current = component.getPreferredSize();
        int width = current == null ? 0 : current.width;
        preferredSize(width, height);
        minimumSize(width, height);
        maximumSize(width, height);
        return self();
    }

    /**
     * Sets the horizontal alignment used by {@link javax.swing.BoxLayout} and similar managers.
     *
     * @param alignment the alignment, e.g. {@link java.awt.Component#LEFT_ALIGNMENT}
     * @return this component
     */
    default C alignmentX(float alignment) {
        ((JComponent) this).setAlignmentX(alignment);
        return self();
    }

    /**
     * Sets the vertical alignment used by {@link javax.swing.BoxLayout} and similar managers.
     *
     * @param alignment the alignment, e.g. {@link java.awt.Component#TOP_ALIGNMENT}
     * @return this component
     */
    default C alignmentY(float alignment) {
        ((JComponent) this).setAlignmentY(alignment);
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
