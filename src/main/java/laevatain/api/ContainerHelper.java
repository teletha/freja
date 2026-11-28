/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain.api;

import java.awt.Component;
import java.awt.Container;

/**
 * Fluent helpers for the child list every panel inherits from {@link Container}. The bound is
 * Container, the class which actually implements add and remove, so panels and compound components
 * can share these methods.
 * <p>
 * Swing's {@code add} methods return void and a class method always wins over an interface default,
 * so they cannot be turned into fluent methods under the same name. These helpers use distinct
 * names for the chainable variants instead.
 * </p>
 *
 * @param <C> the concrete container type returned by every helper, so chains keep the caller's type
 */
public interface ContainerHelper<C extends Container> {
    /**
     * Adds components in order, e.g. the rows assembled by a panel constructor.
     *
     * @param components the children to add
     * @return this container
     */
    default C children(Component... components) {
        Container container = (Container) this;
        for (Component component : components) {
            container.add(component);
        }
        return self();
    }

    /**
     * Removes every child. A panel which rebuilds its content calls this before adding new rows.
     *
     * @return this container
     */
    default C removeChildren() {
        ((Container) this).removeAll();
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
