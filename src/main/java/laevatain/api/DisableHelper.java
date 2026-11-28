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

import javax.swing.JComponent;

/**
 * Fluent enabled-state helpers for the ARX components. They let a component take its initial state
 * in the same chained expression which builds it, instead of exposing a mutable setter.
 * <p>
 * Disabling a Swing container does not clear the enabled flag of its children: they stop receiving
 * input through the hierarchy but can still paint as enabled under some look and feels. This helper
 * changes only the component it is called on; apply it to each child when the whole subtree must
 * look disabled.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface DisableHelper<C extends JComponent> {
    /**
     * Sets the disabled state. True disables the component, false enables it.
     * <p>
     * The name deliberately avoids {@code disable()} and {@code enable()}: {@link java.awt.Component}
     * already defines deprecated void methods with those names, and a fluent override with a
     * different return type would not compile.
     * </p>
     *
     * @param disabled the disabled state
     * @return this component
     */
    default C disabled(boolean disabled) {
        return enabled(!disabled);
    }

    /**
     * Sets the enabled state directly; the canonical setter behind {@link #disabled(boolean)}.
     *
     * @param enabled the enabled state
     * @return this component
     */
    default C enabled(boolean enabled) {
        ((JComponent) this).setEnabled(enabled);
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
