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

import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * Fluent helpers for the settings declared by {@link JLabel}. The interface is bounded to JLabel,
 * the class which owns these methods, so it works on any label subclass; colours, fonts and borders
 * stay in {@link StyleHelper} and {@link LayoutHelper}, which are shared by every component.
 *
 * @param <C> the concrete label type returned by every helper, so chains keep the caller's type
 */
public interface LabelHelper<C extends JLabel> {
    /**
     * Sets the caption. Null clears it.
     *
     * @param text the caption
     * @return this label
     */
    default C text(String text) {
        ((JLabel) this).setText(text);
        return self();
    }

    /**
     * Sets the icon. Null removes it.
     *
     * @param icon the icon
     * @return this label
     */
    default C icon(Icon icon) {
        ((JLabel) this).setIcon(icon);
        return self();
    }

    /**
     * Sets the icon shown while the label is disabled. Null falls back to a greyed normal icon.
     *
     * @param icon the disabled icon
     * @return this label
     */
    default C disabledIcon(Icon icon) {
        ((JLabel) this).setDisabledIcon(icon);
        return self();
    }

    /**
     * Sets where the caption sits relative to the icon.
     *
     * @param alignment a {@code SwingConstants} alignment
     * @return this label
     */
    default C horizontalAlignment(int alignment) {
        ((JLabel) this).setHorizontalAlignment(alignment);
        return self();
    }

    /**
     * Sets the vertical alignment of the contents.
     *
     * @param alignment a {@code SwingConstants} alignment
     * @return this label
     */
    default C verticalAlignment(int alignment) {
        ((JLabel) this).setVerticalAlignment(alignment);
        return self();
    }

    /**
     * Sets where the text sits relative to the icon.
     *
     * @param position a {@code SwingConstants} position
     * @return this label
     */
    default C horizontalTextPosition(int position) {
        ((JLabel) this).setHorizontalTextPosition(position);
        return self();
    }

    /**
     * Sets the vertical position of the text relative to the icon.
     *
     * @param position a {@code SwingConstants} position
     * @return this label
     */
    default C verticalTextPosition(int position) {
        ((JLabel) this).setVerticalTextPosition(position);
        return self();
    }

    /**
     * Sets the gap between the icon and the caption.
     *
     * @param gap the gap in pixels
     * @return this label
     */
    default C iconTextGap(int gap) {
        ((JLabel) this).setIconTextGap(gap);
        return self();
    }

    /**
     * Marks another component as the one this label describes, so clicking the caption focuses it.
     *
     * @param component the labelled component
     * @return this label
     */
    default C labelFor(java.awt.Component component) {
        ((JLabel) this).setLabelFor(component);
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
