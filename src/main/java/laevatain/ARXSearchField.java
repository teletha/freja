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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import javax.swing.Icon;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

import kiss.Variable;

/**
 * A reusable search field with a leading icon, placeholder, clear button and a debounced text
 * callback. Fonts and colours come from the active FlatLaf theme.
 */
public final class ARXSearchField extends JTextField {

    /** Default idle time before a text change is reported to the owner. */
    private static final int DEFAULT_DEBOUNCE_MS = 120;

    /** Breathing room around the leading icon, left and right. */
    private static final int ICON_PAD_LEFT = 4;

    private static final int ICON_PAD_RIGHT = 6;

    private final Timer debounce;

    private final ARXTextBinding placeholderBinding = new ARXTextBinding();

    private final ARXTextBinding textBinding = new ARXTextBinding();

    private Runnable onTextChanged = () -> {
    };

    /**
     * @param placeholder the hint shown while the field is empty
     * @param icon the leading icon, or null
     */
    public ARXSearchField(String placeholder, Icon icon) {
        this(placeholder, icon, DEFAULT_DEBOUNCE_MS);
    }

    /** @param placeholder a live hint which follows the interface language */
    public ARXSearchField(Variable<String> placeholder, Icon icon) {
        this(placeholder, icon, DEFAULT_DEBOUNCE_MS);
    }

    /** @param placeholder a live hint which follows the interface language */
    public ARXSearchField(Variable<String> placeholder, Icon icon, int debounceMillis) {
        this(ARXTextBinding.get(placeholder), icon, debounceMillis);
        setPlaceholder(placeholder);
    }

    public ARXSearchField(String placeholder, Icon icon, int debounceMillis) {
        super();
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        // A search field should read as one at a glance even when the host passes no icon.
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, padded(icon != null ? icon : searchIcon()));
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

    /** A small magnifier tinted with the theme's muted text colour, used when no icon is supplied. */
    private static Icon searchIcon() {
        Color color = UIManager.getColor("TextField.placeholderForeground");
        if (color == null) {
            color = UIManager.getColor("Label.disabledForeground");
        }
        if (color == null) {
            color = new Color(0x808080);
        }
        final Color tint = color;
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(tint);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Float(x + 1, y + 1, 9, 9));
                g.draw(new Line2D.Float(x + 9, y + 9, x + 13, y + 13));
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }

    /** Wraps an icon with transparent side padding so it does not touch the field edge or the text. */
    private static Icon padded(Icon icon) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                icon.paintIcon(component, graphics, x + ICON_PAD_LEFT, y);
            }

            @Override
            public int getIconWidth() {
                return ICON_PAD_LEFT + icon.getIconWidth() + ICON_PAD_RIGHT;
            }

            @Override
            public int getIconHeight() {
                return icon.getIconHeight();
            }
        };
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

    /** Binds the hint to a live value; passing null clears it. */
    public void setPlaceholder(Variable<String> placeholder) {
        if (placeholder == null) {
            setPlaceholder((String) null);
        }
        placeholderBinding.bind(placeholder, value -> setPlaceholder(value));
    }

    /** Binds the field contents to a live value; passing null clears the field. */
    public void setText(Variable<String> text) {
        if (text == null) {
            super.setText(null);
        }
        textBinding.bind(text, super::setText);
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
