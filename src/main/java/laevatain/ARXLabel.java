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

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import kiss.I;
import kiss.Variable;

/**
 * A label whose caption can follow a {@link Variable}, e.g. the value returned by
 * {@link I#translate(String, Object...)}. Assigning a new variable replaces the previous
 * subscription, so
 * the caption stays in sync when the interface language changes.
 */
public final class ARXLabel extends JLabel {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    private final ARXTextBinding tipBinding = new ARXTextBinding();

    public ARXLabel() {
        this((String) null);
    }

    public ARXLabel(String text) {
        super(text);
    }

    public ARXLabel(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXLabel(Icon icon) {
        super(icon);
    }

    public ARXLabel(Icon icon, int horizontalAlignment) {
        super(icon, horizontalAlignment);
    }

    public ARXLabel(String text, int horizontalAlignment) {
        super(text, horizontalAlignment);
    }

    public ARXLabel(Variable<String> text, int horizontalAlignment) {
        super(ARXTextBinding.get(text), horizontalAlignment);
        setText(text);
    }

    public ARXLabel(String text, Icon icon, int horizontalAlignment) {
        super(text, icon, horizontalAlignment);
    }

    public ARXLabel(Variable<String> text, Icon icon, int horizontalAlignment) {
        super(ARXTextBinding.get(text), icon, horizontalAlignment);
        setText(text);
    }

    /** Binds the caption to a live value; passing null clears the caption. */
    public void setText(Variable<String> text) {
        if (text == null) {
            super.setText(null);
        }
        textBinding.bind(text, super::setText);
    }

    /** Binds the tooltip to a live value; passing null clears it. */
    public void setToolTipText(Variable<String> text) {
        if (text == null) {
            super.setToolTipText(null);
        }
        tipBinding.bind(text, super::setToolTipText);
    }

    /** Convenience for the common left-aligned caption. */
    public static ARXLabel of(Variable<String> text) {
        return new ARXLabel(text, SwingConstants.LEFT);
    }
}
