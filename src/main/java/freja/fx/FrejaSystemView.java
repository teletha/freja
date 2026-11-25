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

import kiss.Variable;
import viewtify.style.FormStyles;
import viewtify.ui.UIButton;
import viewtify.ui.UILabel;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.helper.User;

/**
 * Application actions which are not preferences, shown as the last section of the Viewtify
 * preference page.
 */
public final class FrejaSystemView extends View {

    private final MainView main;

    private UIButton manageFolders;

    private UIButton about;

    private UIButton restart;

    private UILabel libraryLocation;

    public FrejaSystemView(MainView main) {
        this.main = main;
    }

    @Override
    public Variable<String> title() {
        return en("System");
    }

    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(vbox, () -> {
                    form(en("Watched folders"), FormStyles.Column5, manageFolders);
                    form(en("Application"), FormStyles.Column5, about, restart);
                    form(en("Library"), FormStyles.Column5, libraryLocation);
                });
            }
        };
    }

    @Override
    protected void initialize() {
        manageFolders.text(en("Manage watched folders…")).when(User.Action, event -> main.openScanFolders());
        about.text(en("About Freja")).when(User.Action, event -> main.about());
        restart.text(en("Restart")).when(User.Action, event -> FrejaFX.restart());
        libraryLocation.text("—");
    }

    /** Shows the active catalog path. */
    public void setLibraryLocation(String location) {
        libraryLocation.text("ライブラリ: " + location);
    }
}
