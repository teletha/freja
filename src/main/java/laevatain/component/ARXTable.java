/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain.component;

import javax.swing.JTable;
import javax.swing.table.TableModel;

import laevatain.api.ComponentHelper;

/**
 * A {@link JTable} with the fluent ARX helper API. Behaviour and painting are untouched, so a host
 * can adopt it first and style the table through the helpers later.
 */
public class ARXTable extends JTable implements ComponentHelper<ARXTable> {
    public ARXTable() {
        super();
    }

    public ARXTable(TableModel model) {
        super(model);
    }
}
