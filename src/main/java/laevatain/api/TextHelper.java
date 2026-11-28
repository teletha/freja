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

import javax.swing.JTextArea;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;

/**
 * Fluent helpers for text components: editing, the caret and the wrapping flags of a text area. The
 * text-area-only settings check the component type and are no-ops on a plain text field, so every
 * {@link JTextComponent} can share this interface.
 *
 * @param <C> the concrete text component type returned by every helper, so chains keep the caller's type
 */
public interface TextHelper<C extends JTextComponent> {
    /**
     * Sets whether the text can be edited. Read-only labels and viewers turn this off.
     *
     * @param editable true to allow editing
     * @return this component
     */
    default C editable(boolean editable) {
        ((JTextComponent) this).setEditable(editable);
        return self();
    }

    /**
     * Replaces the caret. A read-only component can install a caret which never scrolls the viewport.
     *
     * @param caret the caret to install, or null for none
     * @return this component
     */
    default C caret(Caret caret) {
        ((JTextComponent) this).setCaret(caret);
        return self();
    }

    /**
     * Enables word wrapping, but only on a {@link JTextArea}; other text components ignore it.
     *
     * @param wrap true to wrap lines
     * @return this component
     */
    default C lineWrap(boolean wrap) {
        if (this instanceof JTextArea area) {
            area.setLineWrap(wrap);
        }
        return self();
    }

    /**
     * Wraps at word boundaries instead of mid-word, but only on a {@link JTextArea}.
     *
     * @param wrap true to wrap at words
     * @return this component
     */
    default C wrapStyleWord(boolean wrap) {
        if (this instanceof JTextArea area) {
            area.setWrapStyleWord(wrap);
        }
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
