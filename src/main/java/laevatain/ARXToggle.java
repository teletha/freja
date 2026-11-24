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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * A Windows-style on/off switch. The state is delegated to Swing's toggle button model, so
 * pressing, releasing, keyboard focus and the active look and feel all drive it the way they do for
 * any other button; the component only paints the Fluent pill and glides the knob between the two
 * positions.
 */
public final class ARXToggle extends JToggleButton {

    private static final int WIDTH = 40;

    private static final int HEIGHT = 20;

    private static final int KNOB = HEIGHT - 6;

    /** The distance the knob travels between the off and the on position. */
    private static final int TRAVEL = WIDTH - KNOB - 6;

    private static final int FRAME_MS = 10;

    /** The share of the remaining distance taken per frame, which gives a short eased glide. */
    private static final float EASING = 0.3f;

    private final Timer animator;

    /** 0 when off, 1 when on, and animated in between. */
    private float position;

    public ARXToggle(boolean selected) {
        super("", selected);
        position = selected ? 1f : 0f;
        animator = new Timer(FRAME_MS, event -> glide());
        setRolloverEnabled(true);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Any state change (pointer, keyboard, or code) restarts the glide towards the model.
        addChangeListener(event -> {
            if (Math.abs((isSelected() ? 1f : 0f) - position) > 0.01f) {
                animator.restart();
            }
        });
        flatten();
    }

    @Override
    public void updateUI() {
        // Switching the theme reinstalls the look and feel chrome on this button; the switch paints
        // itself, so drop the background, border and focus fill again after every install.
        super.updateUI();
        flatten();
    }

    @Override
    public void setSelected(boolean value) {
        boolean changed = isSelected() != value;
        super.setSelected(value);
        if (changed) {
            animator.restart();
        }
    }

    private void flatten() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    /** Advances the knob a fraction closer to its target and stops as soon as it lands. */
    private void glide() {
        float target = isSelected() ? 1f : 0f;
        float distance = target - position;
        if (Math.abs(distance) < 0.02f) {
            position = target;
            animator.stop();
        } else {
            position = position + distance * EASING;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - HEIGHT) / 2;
            Color accent = themed("Button.defaultBackground", new Color(0x2E6BE6));
            Color off = themed("Button.borderColor", new Color(0xC8CDD6));
            g.setColor(blend(off, accent, position));
            g.fillRoundRect(0, y, WIDTH, HEIGHT, HEIGHT, HEIGHT);
            // Fluent stretches the knob slightly while it is travelling.
            float travelling = 1f - Math.abs(position * 2f - 1f);
            int width = KNOB + Math.round(travelling * 3f);
            int centre = 3 + KNOB / 2 + Math.round(TRAVEL * position);
            g.setColor(Color.WHITE);
            g.fillOval(centre - width / 2, y + 3, width, KNOB);
            if (getModel().isRollover()) {
                g.setColor(new Color(0, 0, 0, 18));
                g.drawRoundRect(0, y, WIDTH, HEIGHT, HEIGHT, HEIGHT);
            }
        } finally {
            g.dispose();
        }
    }

    private static Color themed(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    private static Color blend(Color first, Color second, float ratio) {
        float value = Math.max(0f, Math.min(1f, ratio));
        return new Color(mix(first.getRed(), second.getRed(), value),
                mix(first.getGreen(), second.getGreen(), value),
                mix(first.getBlue(), second.getBlue(), value));
    }

    private static int mix(int first, int second, float ratio) {
        return Math.round(first + (second - first) * ratio);
    }
}
