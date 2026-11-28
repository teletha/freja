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

import java.util.ArrayList;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import kiss.Disposable;
import kiss.I;
import kiss.Variable;
import laevatain.api.ComponentHelper;

/**
 * A combo box whose items can follow live translations. Each source string passed to
 * {@link #setSources(String...)} is translated through {@link I#translate(String, Object...)} and the
 * displayed item is replaced whenever that translation changes, while the selected index stays put.
 * <p>
 * The class is generic so it can replace a plain {@link JComboBox} of any item type; the
 * translation helpers only make sense when {@code E} is {@link String}.
 * </p>
 *
 * @param <E> the item type
 */
public final class ARXComboBox<E> extends JComboBox<E> implements ComponentHelper<ARXComboBox<E>> {

    private final List<Disposable> subscriptions = new ArrayList<>();

    public ARXComboBox() {
        super();
    }

    public ARXComboBox(E[] items) {
        super(items);
    }

    public ARXComboBox(ComboBoxModel<E> model) {
        super(model);
    }

    /**
     * Replaces the items with live translations of the given source strings. Only valid when the
     * item type is String, since translated text cannot be inserted into another item type.
     *
     * @param sources the untranslated captions
     */
    @SuppressWarnings("unchecked")
    public void setSources(List<String> sources) {
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
        DefaultComboBoxModel<E> model = (DefaultComboBoxModel<E>) getModel();
        model.removeAllElements();
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            Variable<String> value = I.translate(source);
            model.addElement((E) value.get());
            int item = index;
            subscriptions.add(value.observing().to(ignored -> {
                String shown = value.get();
                if (!shown.equals(model.getElementAt(item))) {
                    int selected = getSelectedIndex();
                    model.removeElementAt(item);
                    model.insertElementAt((E) shown, item);
                    if (selected >= 0 && selected < model.getSize()) {
                        setSelectedIndex(selected);
                    }
                    repaint();
                }
            }));
        }
    }

    /** Replaces the items with live translations of the given source strings. */
    public void setSources(String... sources) {
        setSources(List.of(sources));
    }

    @Override
    public void removeAllItems() {
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
        super.removeAllItems();
    }
}
