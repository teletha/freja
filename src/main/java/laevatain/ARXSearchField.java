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

        debounce = new Timer(Math.max(0, debounceMillis), event -> onTextChanged.run());
        debounce.setRepeats(false);
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

    /** Returns the trimmed query text. */
    public String query() {
        return getText() == null ? "" : getText().trim();
    }
}
