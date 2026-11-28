/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain.component;

import javax.swing.Icon;
import javax.swing.JRadioButtonMenuItem;

import kiss.Variable;
import laevatain.ARXTextBinding;
import laevatain.api.ComponentHelper;

/** A radio menu item whose caption can follow a {@link Variable}, mirroring {@link ARXMenuItem}. */
public final class ARXRadioButtonMenuItem extends JRadioButtonMenuItem implements ComponentHelper<ARXRadioButtonMenuItem> {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    public ARXRadioButtonMenuItem() {
        this((String) null);
    }

    public ARXRadioButtonMenuItem(String text) {
        super(text);
    }

    public ARXRadioButtonMenuItem(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXRadioButtonMenuItem(String text, boolean selected) {
        super(text, selected);
    }

    public ARXRadioButtonMenuItem(Variable<String> text, boolean selected) {
        super(ARXTextBinding.get(text), selected);
        setText(text);
    }

    public ARXRadioButtonMenuItem(Icon icon) {
        super(icon);
    }

    public ARXRadioButtonMenuItem(String text, Icon icon, boolean selected) {
        super(text, icon, selected);
    }

    public ARXRadioButtonMenuItem(Variable<String> text, Icon icon, boolean selected) {
        super(ARXTextBinding.get(text), icon, selected);
        setText(text);
    }

    /** Binds the caption to a live value; passing null clears it. */
    public void setText(Variable<String> text) {
        if (text == null) {
            super.setText(null);
        }
        textBinding.bind(text, super::setText);
    }
}
