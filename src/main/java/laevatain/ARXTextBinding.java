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

import java.util.function.Consumer;

import kiss.Disposable;
import kiss.Variable;

/**
 * Keeps one live text subscription for a component. Rebinding disposes the previous observer, so a
 * component can switch sources - for example when its language changes - without leaking or being
 * written by two translations at once.
 */
final class ARXTextBinding {

    private Disposable current;

    /** Points the setter at {@code value} now and on every later change. */
    void bind(Variable<String> value, Consumer<String> setter) {
        if (current != null) {
            current.dispose();
            current = null;
        }
        if (value != null) {
            current = value.observing().to(setter::accept);
        }
    }

    /** The current wording, or null while nothing is bound. */
    static String get(Variable<String> value) {
        return value == null ? null : value.get();
    }
}
