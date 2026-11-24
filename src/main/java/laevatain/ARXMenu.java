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

import javax.swing.JMenu;

import kiss.Variable;

/**
 * A menu whose caption can follow a {@link Variable}, mirroring {@link ARXMenuItem}.
 */
public final class ARXMenu extends JMenu {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    public ARXMenu() {
        this((String) null);
    }

    public ARXMenu(String text) {
        super(text);
    }

    public ARXMenu(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXMenu(String text, boolean tearOff) {
        super(text, tearOff);
    }

    public ARXMenu(Variable<String> text, boolean tearOff) {
        super(ARXTextBinding.get(text), tearOff);
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
