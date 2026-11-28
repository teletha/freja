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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * The shell around a sidebar list: a shared section heading above a caller-supplied scroll pane.
 * The list itself stays specialised for each host (library folders or file manager tabs).
 */
public final class ARXSectionListPanel extends JPanel {

    private final JScrollPane scroll;

    /** A panel without a separate heading, for lists whose ARXNavList carries its own section. */
    public ARXSectionListPanel(JScrollPane scroll) {
        setLayout(new BorderLayout());
        setOpaque(false);
        this.scroll = scroll;
        this.scroll.setBorder(BorderFactory.createEmptyBorder());
        Object background = UIManager.get("ARX.navBackground");
        this.scroll.getViewport().setBackground(background instanceof Color chosen
                ? chosen : new Color(0xF1F3F6));
        add(this.scroll, BorderLayout.CENTER);
    }

    public ARXSectionListPanel(String title, JScrollPane scroll, ARXSectionHeader.Action... actions) {
        this(title, null, scroll, actions);
    }

    public ARXSectionListPanel(String title, Component detail, JScrollPane scroll, ARXSectionHeader.Action... actions) {
        this(scroll);
        add(new ARXSectionHeader(title, detail, actions), BorderLayout.NORTH);
    }

    public JScrollPane scroll() {
        return scroll;
    }
}
