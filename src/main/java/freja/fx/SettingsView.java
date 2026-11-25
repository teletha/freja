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

import viewtify.preference.PreferenceView;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.view.AppearanceSettingView;

/**
 * The settings page of the JavaFX edition.
 * <p>
 * The page itself is a plain host: all sections are regular Views managed by Viewtify's
 * {@link PreferenceView}, which supplies the navigation, search, import and export of preferences.
 * {@link FrejaSettingView} binds the Freja preferences directly, {@link FrejaSystemView} keeps the
 * few application actions which are not preferences.
 * </p>
 */
public final class SettingsView extends View {

    private final FrejaSystemView system;

    private final PreferenceView preferences;

    /** The page is built manually with its owner, unlike the injected child views. */
    public SettingsView(MainView main) {
        this.system = new FrejaSystemView(main);
        // manage() must run before PreferenceView builds its UI, so the sections are prepared here.
        this.preferences = new PreferenceView()
                .manage(AppearanceSettingView.class, FrejaSettingView.class)
                .manage(system)
                .tableOfContents(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(preferences);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        // The managed sections own their own initialization.
    }

    /** Shows the active catalog path on the page. */
    public void setLibraryLocation(String location) {
        system.setLibraryLocation(location);
    }
}
