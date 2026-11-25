/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import freja.model.PhotoFilter.SortOrder;
import kiss.Variable;
import viewtify.preference.Preferences;
import viewtify.style.FormStyles;
import viewtify.ui.UICheckSwitch;
import viewtify.ui.UIComboBox;
import viewtify.ui.UISpinner;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;

/**
 * The Freja-specific section of the Viewtify {@link viewtify.preference.PreferenceView}.
 * Every control is bound directly to a {@link freja.fx.FrejaSetting} preference, so changes are
 * persisted and distributed through the usual Viewtify preference lifecycle.
 */
public final class FrejaSettingView extends View {

    private UICheckSwitch scanOnStartup;

    private UICheckSwitch sweep;

    private UICheckSwitch inspectorVisible;

    private UISpinner<Integer> thumbSize;

    private UIComboBox<SortOrder> sort;

    @Override
    public Variable<String> title() {
        return en("Freja");
    }

    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, () -> {
                    form(en("Scan on startup"), FormStyles.Column3, scanOnStartup);
                    form(en("Periodic sweep"), FormStyles.Column3, sweep);
                    form(en("Show inspector"), FormStyles.Column3, inspectorVisible);
                    form(en("Thumbnail size"), FormStyles.Column3, thumbSize);
                    form(en("Sort order"), FormStyles.Column5, sort);
                });
            }
        };
    }

    @Override
    protected void initialize() {
        FrejaSetting setting = Preferences.of(FrejaSetting.class);

        scanOnStartup.sync(setting.scanOnStartup);
        sweep.sync(setting.sweep);
        inspectorVisible.sync(setting.inspectorVisible);
        thumbSize.items(120, 220, value -> value).format(value -> value + " px").sync(setting.thumbSize);
        sort.items(SortOrder.values()).render(order -> order.label()).sync(setting.sort);
    }
}
