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

import java.util.List;

import laevatain.util.Language;
import stylist.Style;
import stylist.StyleDSL;
import viewtify.Theme;
import viewtify.ThemeType;
import viewtify.ui.UIButton;
import viewtify.ui.UICheckSwitch;
import viewtify.ui.UIComboBox;
import viewtify.ui.UILabel;
import viewtify.ui.UIScrollPane;
import viewtify.ui.UISlider;
import viewtify.ui.View;
import viewtify.ui.ViewDSL;
import viewtify.ui.view.AppearanceSetting;
import viewtify.ui.helper.User;
import viewtify.preference.Preferences;

/**
 * The settings page: appearance, scan behaviour, grid zoom and language. This replaces the Swing
 * {@code SettingsPanel} (which tuned FlatLaf) with Viewtify's own theme system and the
 * {@link FrejaSetting} preferences store.
 */
public final class SettingsView extends View {

    private final MainView main;

    private UIScrollPane scroll;

    private UILabel heading;

    private UIComboBox<Theme> theme;

    private UIComboBox<ThemeType> themeType;

    private UIComboBox<String> language;

    private UISlider thumbSize;

    private UICheckSwitch scanOnStartup;

    private UICheckSwitch sweepScan;

    private UIButton manageFolders;

    private UIButton aboutButton;

    private UIButton restartButton;

    private UILabel libraryLocation;

    /** The page is built manually with its owner, unlike the injected child views. */
    public SettingsView(MainView main) {
        this.main = main;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ViewDSL declareUI() {
        return new ViewDSL() {
            {
                $(scroll, style.page, () -> {
                    $(vbox, style.body, () -> {
                        $(heading);
                        title(en("Appearance"), en("Theme, palette and typeface live here."));
                        form(en("Theme"), theme);
                        form(en("Palette style"), themeType);
                        $(hsep);
                        title(en("Library"), en("Scanning and watched folders."));
                        form(en("Scan on startup"), scanOnStartup);
                        form(en("Periodic sweep"), sweepScan);
                        form(en("Watched folders"), manageFolders);
                        $(hsep);
                        title(en("Grid"), en("Thumbnail size for the photo wall."));
                        form(en("Thumbnail size"), thumbSize);
                        $(hsep);
                        title(en("Language"), en("Interface text is written in English and translated automatically; results are cached for offline use."));
                        form(en("Display language"), language);
                        $(hsep);
                        title(en("System"), en("Application information and restart."));
                        $(hbox, style.actions, () -> {
                            $(aboutButton);
                            $(restartButton);
                        });
                        $(libraryLocation);
                    });
                });
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        FrejaSetting setting = Preferences.of(FrejaSetting.class);
        AppearanceSetting appearance = Preferences.of(AppearanceSetting.class);

        heading.text(en("Settings")).style(style.heading);
        theme.items(Theme.values()).render(t -> t.name());
        theme.sync(appearance.theme);
        themeType.items(ThemeType.values()).render(t -> t.name());
        themeType.sync(appearance.themeType);

        List<String> names = Language.SUPPORTS.stream().map(Language.Supported::name).toList();
        language.items(names).render(name -> name);
        language.value(Language.name());
        language.observe().to(name -> {
            for (Language.Supported supported : Language.SUPPORTS) {
                if (supported.name().equals(name)) {
                    Language.install(supported.code());
                    main.status(en("Language changed"));
                    return;
                }
            }
        });

        scanOnStartup.text(en("Run a scan when Freja starts"));
        scanOnStartup.value(setting.scanOnStartup.get());
        scanOnStartup.when(User.Action, event -> {
            setting.scanOnStartup.set(scanOnStartup.is(true));
            setting.store();
        });
        sweepScan.text(en("Sweep for missed changes every 5 minutes"));
        sweepScan.value(setting.sweep.get());
        sweepScan.when(User.Action, event -> {
            setting.sweep.set(sweepScan.is(true));
            setting.store();
        });

        manageFolders.text(en("Manage watched folders…"));
        manageFolders.when(User.Action, event -> main.openScanFolders());

        thumbSize.range(120, 220).step(10).format(size -> size + " px");
        thumbSize.value((double) setting.thumbSize.get());
        thumbSize.observe().to(size -> {
            int px = (int) Math.round(size);
            setting.thumbSize.set(px);
            main.setThumbSize(px);
        });

        aboutButton.text(en("About Freja"));
        aboutButton.when(User.Action, event -> main.about());
        restartButton.text(en("Restart"));
        restartButton.when(User.Action, event -> FrejaFX.restart());
        libraryLocation.text("—");
        libraryLocation.style(style.muted);
        scroll.ui().setFitToWidth(true);
    }

    /** Shows the active catalog path on the page. */
    public void setLibraryLocation(String location) {
        libraryLocation.text("ライブラリ: " + location);
    }

    /** Visual language of the page. */
    interface style extends StyleDSL {

        Style page = () -> background.color("-fx-background");

        Style body = () -> {
            padding.vertical(24, px).horizontal(60, px);
            display.maxWidth(920, px);
        };

        Style heading = () -> {
            font.size(24, px).weight(700);
            padding.bottom(12, px);
        };

        Style actions = () -> {
            display.minHeight(8, px);
        };

        Style muted = () -> {
            font.size(11, px).color("-fx-mid-text-color");
            margin.top(10, px);
        };
    }
}
