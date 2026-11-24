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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.UIManager;

/** A Windows-style on/off switch whose colours follow the active FlatLaf theme. */
public final class ARXToggle extends JComponent {

    private static final int WIDTH = 40;

    private static final int HEIGHT = 20;

    private final List<ActionListener> listeners = new ArrayList<>();

    private boolean selected;

    private boolean hovered;

    public ARXToggle(boolean selected) {
        this.selected = selected;
        setOpaque(false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                setSelected(!selected);
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean value) {
        if (selected == value) {
            return;
        }
        selected = value;
        repaint();
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, selected ? "on" : "off");
        for (ActionListener listener : listeners) {
            listener.actionPerformed(event);
        }
    }

    public void addActionListener(ActionListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - HEIGHT) / 2;
            Color accent = UIManager.getColor("Button.defaultBackground");
            Color off = UIManager.getColor("Button.borderColor");
            g.setColor(selected ? (accent == null ? new Color(0x2E6BE6) : accent)
                    : (off == null ? new Color(0xC8CDD6) : off));
            g.fillRoundRect(0, y, WIDTH, HEIGHT, HEIGHT, HEIGHT);
            int knob = HEIGHT - 6;
            int x = selected ? WIDTH - knob - 3 : 3;
            g.setColor(Color.WHITE);
            g.fillOval(x, y + 3, knob, knob);
            if (hovered) {
                g.setColor(new Color(0, 0, 0, 18));
                g.drawRoundRect(0, y, WIDTH, HEIGHT, HEIGHT, HEIGHT);
            }
        } finally {
            g.dispose();
        }
    }
}
