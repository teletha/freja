/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.Icon;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * A reusable search field with a leading icon, placeholder, clear button and a debounced text
 * callback. Fonts and colours come from the active FlatLaf theme.
 */
public final class ARXSearchField extends JTextField {

    /** Default idle time before a text change is reported to the owner. */
    private static final int DEFAULT_DEBOUNCE_MS = 120;

    private final Timer debounce;

    private Runnable onTextChanged = () -> {
    };

    /**
     * @param placeholder the hint shown while the field is empty
     * @param icon the leading icon, or null
     */
    public ARXSearchField(String placeholder, Icon icon) {
        this(placeholder, icon, DEFAULT_DEBOUNCE_MS);
    }

    public ARXSearchField(String placeholder, Icon icon, int debounceMillis) {
        super();
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        if (icon != null) {
            putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, icon);
        }
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

        // Built in two steps: a listener passed inside the field's own initializer would be
        // reading debounce before it exists.
        debounce = new Timer(Math.max(0, debounceMillis), null);
        debounce.setRepeats(false);
        debounce.addActionListener(event -> {
            // While an input method owns the text - Japanese keystrokes before they are committed -
            // touching the page rebuilds the layout around this field and the IME drops the
            // composition, which is what made the edit vanish after a single character. Poll again
            // instead of firing in the middle of it.
            if (isComposing()) {
                debounce.restart();
                return;
            }
            onTextChanged.run();
        });
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                debounce.restart();
            }
        });
        // Swing beeps when Backspace/Delete is pressed on an empty text field.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (getText().isEmpty() && (event.getKeyCode() == KeyEvent.VK_BACK_SPACE
                        || event.getKeyCode() == KeyEvent.VK_DELETE)) {
                    event.consume();
                }
            }
        });
    }

    /** Sets the callback invoked after the user stops typing. */
    public void onTextChanged(Runnable callback) {
        onTextChanged = callback == null ? () -> {
        } : callback;
    }

    /** Replaces the hint, so the placeholder can follow the interface language. */
    public void setPlaceholder(String placeholder) {
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        repaint();
    }

    /** Returns the trimmed query text. */
    public String query() {
        return getText() == null ? "" : getText().trim();
    }

    /** Focuses the field and selects any text in it, the way a search shortcut expects. */
    public void reveal() {
        if (isShowing()) {
            requestFocusInWindow();
        }
        selectAll();
    }

    /**
     * Whether an input method is composing text in this field right now: Swing marks the in-flight
     * run with {@code ComposedTextAttribute}, which is the same signal the underline is drawn
     * from. Read from the document rather than tracked in a flag, so it cannot go stale when the
     * user deletes text, clicks away or cancels a conversion.
     */
    private boolean isComposing() {
        if (!(getDocument() instanceof javax.swing.text.StyledDocument document)) {
            return false;
        }
        for (int offset = 0, length = document.getLength(); offset < length; offset++) {
            if (document.getCharacterElement(offset).getAttributes()
                    .getAttribute(javax.swing.text.StyleConstants.ComposedTextAttribute) != null) {
                return true;
            }
        }
        return false;
    }
}
