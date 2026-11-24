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

import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import kiss.Disposable;
import kiss.I;
import kiss.Variable;

/**
 * A combo box whose items follow live translations. Each source string is translated through
 * {@link I#translate(String, Object...)} and the displayed item is replaced whenever that
 * translation changes,
 * while the selected index stays put.
 */
public final class ARXComboBox extends JComboBox<String> {

    private final List<Disposable> subscriptions = new ArrayList<>();

    public ARXComboBox() {
        super();
    }

    /** Replaces the items with live translations of the given source strings. */
    public void setSources(List<String> sources) {
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) getModel();
        model.removeAllElements();
        for (int index = 0; index < sources.size(); index++) {
            String source = sources.get(index);
            Variable<String> value = I.translate(source);
            model.addElement(value.get());
            int item = index;
            subscriptions.add(value.observing().to(ignored -> {
                String shown = value.get();
                if (!shown.equals(model.getElementAt(item))) {
                    int selected = getSelectedIndex();
                    model.removeElementAt(item);
                    model.insertElementAt(shown, item);
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
