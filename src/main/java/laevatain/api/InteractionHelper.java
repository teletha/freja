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

import java.awt.Cursor;
import javax.swing.JComponent;

/**
 * Fluent helpers for the small interaction settings every control needs: pointer shape, tooltip,
 * keyboard focus and a component name. Keeping them in one interface lets a constructor describe a
 * control in a single expression instead of scattering setters through the body.
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface InteractionHelper<C extends JComponent> {
    /**
     * Sets a client property, e.g. a FlatLaf styling key. Keeping this in the chain lets a field
     * initializer describe all of its static settings in one expression.
     *
     * @param key the property key
     * @param value the property value, or null to clear it
     * @return this component
     */
    default C clientProperty(Object key, Object value) {
        ((JComponent) this).putClientProperty(key, value);
        return self();
    }

    /**
     * Sets the pointer shape. Null restores the parent's cursor.
     *
     * @param cursor the cursor to show over this component
     * @return this component
     */
    default C cursor(Cursor cursor) {
        ((JComponent) this).setCursor(cursor);
        return self();
    }

    /**
     * Shows the hand pointer used by clickable rows and buttons.
     *
     * @return this component
     */
    default C handCursor() {
        return cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Sets the tooltip text. Null removes the tooltip and unregisters its timer.
     *
     * @param text the tooltip, or null for none
     * @return this component
     */
    default C tooltip(String text) {
        ((JComponent) this).setToolTipText(text);
        return self();
    }

    /**
     * Sets whether the component can take the keyboard focus. Display-only labels and renderers
     * should refuse it so Tab lands only on actionable controls.
     *
     * @param focusable true to accept focus
     * @return this component
     */
    default C focusable(boolean focusable) {
        ((JComponent) this).setFocusable(focusable);
        return self();
    }

    /**
     * Sets the component name used by accessibility tools and UI tests.
     *
     * @param name the name, or null for none
     * @return this component
     */
    default C name(String name) {
        ((JComponent) this).setName(name);
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
