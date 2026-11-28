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

import javax.swing.AbstractButton;

/**
 * Fluent helpers for the button-only flags which the general helpers do not cover: painted chrome,
 * rollover feedback and alignment. A button implements this alongside {@link ComponentHelper} so its
 * whole setup can stay in one constructor chain.
 *
 * @param <C> the concrete button type returned by every helper, so chains keep the caller's type
 */
public interface ButtonHelper<C extends AbstractButton> {
    /**
     * Sets whether the button paints its own content area. Flat custom buttons turn this off and
     * paint their background themselves.
     *
     * @param filled true to let the look and feel fill the content area
     * @return this button
     */
    default C contentAreaFilled(boolean filled) {
        ((AbstractButton) this).setContentAreaFilled(filled);
        return self();
    }

    /**
     * Sets whether the border is painted. Custom buttons which draw their own rounded outline turn
     * this off.
     *
     * @param painted true to paint the border
     * @return this button
     */
    default C borderPainted(boolean painted) {
        ((AbstractButton) this).setBorderPainted(painted);
        return self();
    }

    /**
     * Sets whether the focus indicator is painted by the look and feel.
     *
     * @param painted true to paint the focus ring
     * @return this button
     */
    default C focusPainted(boolean painted) {
        ((AbstractButton) this).setFocusPainted(painted);
        return self();
    }

    /**
     * Sets whether the button reacts to a pointer resting over it.
     *
     * @param enabled true to track the pointer
     * @return this button
     */
    default C rolloverEnabled(boolean enabled) {
        ((AbstractButton) this).setRolloverEnabled(enabled);
        return self();
    }

    /**
     * Sets the horizontal alignment of the icon and text.
     *
     * @param alignment a {@code SwingConstants} alignment
     * @return this button
     */
    default C horizontalAlignment(int alignment) {
        ((AbstractButton) this).setHorizontalAlignment(alignment);
        return self();
    }

    /**
     * Sets the vertical alignment of the icon and text.
     *
     * @param alignment a {@code SwingConstants} alignment
     * @return this button
     */
    default C verticalAlignment(int alignment) {
        ((AbstractButton) this).setVerticalAlignment(alignment);
        return self();
    }

    /**
     * Sets the position of the text relative to the icon.
     *
     * @param position a {@code SwingConstants} position
     * @return this button
     */
    default C horizontalTextPosition(int position) {
        ((AbstractButton) this).setHorizontalTextPosition(position);
        return self();
    }

    /**
     * Sets the vertical position of the text relative to the icon.
     *
     * @param position a {@code SwingConstants} position
     * @return this button
     */
    default C verticalTextPosition(int position) {
        ((AbstractButton) this).setVerticalTextPosition(position);
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
