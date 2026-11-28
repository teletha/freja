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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * The section heading shared by the library sidebar and the file manager: a label on the left and
 * flat icon actions on the right.
 */
public final class ARXSectionHeader extends JPanel {

    /** One flat action shown at the right end of the heading. */
    public record Action(String text, Icon icon, String tooltip, Runnable action) {

        public Action(Icon icon, String tooltip, Runnable action) {
            this(null, icon, tooltip, action);
        }
    }

    public ARXSectionHeader(String title, Action... actions) {
        this(title, null, actions);
    }

    public ARXSectionHeader(String title, Component detail, Action... actions) {
        setOpaque(false);
        setLayout(new BorderLayout());
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        heading.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(font("ARX.font.section", Font.BOLD, 11));
        label.setForeground(color("ARX.textMuted", new Color(0x7B818B)));
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        heading.add(label);
        if (detail != null) {
            heading.add(detail);
        }
        add(heading, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        for (Action action : actions) {
            buttons.add(headerButton(action));
        }
        add(buttons, BorderLayout.EAST);
    }

    private static JButton headerButton(Action action) {
        JButton button = new JButton(action.text(), action.icon()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (getModel().isRollover()) {
                        g.setColor(color("ARX.hover", new Color(0xE7EAEF)));
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                    }
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        button.setFont(font("ARX.font.label", Font.PLAIN, 13));
        button.setForeground(color("ARX.textMuted", new Color(0x7B818B)));
        button.setToolTipText(action.tooltip());
        button.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        button.addActionListener(event -> action.action().run());
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.repaint();
            }
        });
        return button;
    }

    private static Color color(String key, Color fallback) {
        Object value = UIManager.get(key);
        return value instanceof Color chosen ? chosen : fallback;
    }

    private static Font font(String key, int style, int size) {
        Object value = UIManager.get(key);
        return value instanceof Font chosen ? chosen : new Font(Font.DIALOG, style, size);
    }
}
