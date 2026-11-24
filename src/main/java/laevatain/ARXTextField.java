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

import javax.swing.JTextField;

import kiss.Variable;

/**
 * A text field whose contents can follow a {@link Variable}. Useful for values which are edited in
 * place and also change elsewhere, such as the current name of a folder.
 */
public final class ARXTextField extends JTextField {

    private final ARXTextBinding textBinding = new ARXTextBinding();

    public ARXTextField() {
        super();
    }

    public ARXTextField(String text) {
        super(text);
    }

    public ARXTextField(Variable<String> text) {
        super(ARXTextBinding.get(text));
        setText(text);
    }

    public ARXTextField(int columns) {
        super(columns);
    }

    public ARXTextField(String text, int columns) {
        super(text, columns);
    }

    public ARXTextField(Variable<String> text, int columns) {
        super(ARXTextBinding.get(text), columns);
        setText(text);
    }

    /** Binds the field contents to a live value; passing null clears the field. */
    public void setText(Variable<String> text) {
        if (text == null) {
            super.setText(null);
        }
        textBinding.bind(text, super::setText);
    }
}
