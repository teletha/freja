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
 * A convenience bundle of every fluent component helper. A component which only needs a subset can
 * implement the individual interfaces; this one is for controls built entirely in a constructor
 * chain, where listing six interfaces would bury the actual layout code.
 * <p>
 * No method is redeclared here: all settings remain in their focused interfaces so each one keeps
 * its own documentation and can be reused on its own.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface ComponentHelper<C extends JComponent>
        extends LayoutHelper<C>, StyleHelper<C>, DisableHelper<C>, SizeHelper<C>, InteractionHelper<C> {
}
