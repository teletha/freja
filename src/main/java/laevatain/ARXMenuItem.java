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
import javax.swing.JMenuItem;

import kiss.Variable;

/**
 * A menu item whose caption can follow a {@link Variable}. Menus are built on demand, so a late
 * translation still reaches an item which is already on screen.
 */
public final class ARXMenuItem extends JMenuItem {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    public ARXMenuItem() {
        this((String) null);
    }

    public ARXMenuItem(String text) {
        super(text);
    }

    public ARXMenuItem(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXMenuItem(Icon icon) {
        super(icon);
    }

    public ARXMenuItem(String text, Icon icon) {
        super(text, icon);
    }

    public ARXMenuItem(Variable<String> text, Icon icon) {
        super(ARXTextBinding.get(text), icon);
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
