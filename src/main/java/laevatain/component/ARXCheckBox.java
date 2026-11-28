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
import javax.swing.JCheckBox;

import kiss.Variable;
import laevatain.ARXTextBinding;
import laevatain.api.ComponentHelper;

/**
 * A check box whose caption can follow a {@link Variable}, so a translated option keeps its wording
 * when the interface language changes.
 */
public final class ARXCheckBox extends JCheckBox implements ComponentHelper<ARXCheckBox> {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    public ARXCheckBox() {
        this((String) null);
    }

    public ARXCheckBox(String text) {
        super(text);
    }

    public ARXCheckBox(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXCheckBox(String text, boolean selected) {
        super(text, selected);
    }

    public ARXCheckBox(Variable<String> text, boolean selected) {
        super(ARXTextBinding.get(text), selected);
        setText(text);
    }

    public ARXCheckBox(Icon icon) {
        super(icon);
    }

    public ARXCheckBox(Icon icon, boolean selected) {
        super(icon, selected);
    }

    public ARXCheckBox(String text, Icon icon, boolean selected) {
        super(text, icon, selected);
    }

    public ARXCheckBox(Variable<String> text, Icon icon, boolean selected) {
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
