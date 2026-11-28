/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain;

import javax.swing.JList;
import javax.swing.ListModel;

import laevatain.api.ComponentHelper;

/**
 * A {@link JList} with the fluent ARX helper API. It keeps every Swing default, so hosts which paint
 * their own rows can replace a plain list without changing its behaviour.
 *
 * @param <E> the element type shown by the list
 */
public class ARXList<E> extends JList<E> implements ComponentHelper<ARXList<E>> {
    public ARXList() {
        super();
    }

    public ARXList(ListModel<E> model) {
        super(model);
    }

    public ARXList(E[] elements) {
        super(elements);
    }
}
