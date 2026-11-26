/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.AbstractButton;
import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Bundled SVG icons loaded from the classpath. The application stays offline-capable because the
 * assets are shipped under {@code src/main/resources/icons/mdi}.
 */
public final class Icons {

    private Icons() {
    }

    /**
     * The application image: a four-colour pinwheel with a small house at its centre, reminiscent
     * of Picasa. Drawn once into a bitmap because windows need an {@link Image}, not an {@link Icon}.
     *
     * @return a 128x128 application image
     */
    public static Image applicationImage() {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color[] colors = {
                    new Color(0x2E6BE6), // blue
                    new Color(0x3FAE6A), // green
                    new Color(0xF2C53D), // yellow
                    new Color(0xE4634F)  // red
            };
            for (int i = 0; i < colors.length; i++) {
                AffineTransform original = g.getTransform();
                g.rotate(i * Math.PI / 2, size / 2.0, size / 2.0);
                Path2D petal = new Path2D.Double();
                petal.moveTo(64, 64);
                petal.curveTo(72, 42, 84, 22, 108, 26);
                petal.curveTo(108, 48, 94, 62, 64, 64);
                petal.closePath();
                g.setColor(colors[i]);
                g.fill(petal);
                g.setTransform(original);
            }
            // A small white disc with a blue house in the middle.
            g.setColor(new Color(0xF7F8FA));
            g.fill(new Ellipse2D.Float(50, 50, 28, 28));
            g.setColor(new Color(0x2E6BE6));
            g.fill(new RoundRectangle2D.Float(58, 62, 12, 11, 1, 1));
            Path2D roof = new Path2D.Double();
            roof.moveTo(55, 63);
            roof.lineTo(64, 54);
            roof.lineTo(73, 63);
            roof.closePath();
            g.fill(roof);
            g.setColor(new Color(0xF7F8FA));
            g.fill(new RoundRectangle2D.Float(62, 67, 4, 6, 1, 1));
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Loads any bundled Material Design Icons SVG by name. Accepts either {@code "mdi:<name>"} or
     * just {@code "<name>"}.
     *
     * @param iconifyName the icon name, with or without an {@code mdi:} prefix
     * @param size the display size in pixels
     * @param color the tint colour (may be a {@link Theme} dynamic colour)
     * @return the loaded icon
     */
    public static Icon of(String iconifyName, int size, Color color) {
        String name = iconifyName;
        if (name.startsWith("mdi:")) {
            name = name.substring(4);
        }
        return svg(name, size, color);
    }

    private static Icon svg(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/mdi/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> color));
        return icon;
    }

    public static Icon plus(int size, Color color) {
        return svg("plus", size, color);
    }

    public static Icon close(int size, Color color) {
        return svg("close", size, color);
    }

    public static Icon star(int size, Color color, boolean filled) {
        return svg(filled ? "star" : "star-outline", size, color);
    }

    public static Icon folder(int size, Color color) {
        return svg("folder", size, color);
    }

    public static Icon tag(int size, Color color) {
        return svg("tag", size, color);
    }

    /**
     * A glyph icon painted with the configured application font, for the cases where a letter reads
     * better than an outline: the font row shows an "A" in the very typeface it configures.
     */
    public static Icon letter(String text, int size, Color color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                Graphics2D gg = (Graphics2D) g.create();
                try {
                    gg.translate(x, y);
                    gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    Font font = new Font(freja.ui.Theme.fontFamily(), Font.BOLD, Math.round(size * 1.1f));
                    gg.setFont(font);
                    gg.setColor(color);
                    FontMetrics metrics = gg.getFontMetrics();
                    int tx = (size - metrics.stringWidth(text)) / 2;
                    int ty = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                    gg.drawString(text, tx, ty);
                } finally {
                    gg.dispose();
                }
            }
        };
    }

    /** The theme glyph: an outlined circle with its right half filled, like the contrast symbol. */
    public static Icon contrast(int size, Color color) {
        return svg("contrast-circle", size, color);
    }

    public static Icon settings(int size, Color color) {
        return svg("cog", size, color);
    }

    public static Icon grid(int size, Color color) {
        return svg("view-grid", size, color);
    }

    public static Icon clock(int size, Color color) {
        return svg("clock", size, color);
    }

    public static Icon image(int size, Color color) {
        return svg("image", size, color);
    }

    public static Icon search(int size, Color color) {
        return svg("magnify", size, color);
    }

    public static Icon refresh(int size, Color color) {
        return svg("refresh", size, color);
    }

    public static Icon play(int size, Color color) {
        return svg("play", size, color);
    }

    public static Icon sun(int size, Color color) {
        return svg("weather-sunny", size, color);
    }

    public static Icon moon(int size, Color color) {
        return svg("weather-night", size, color);
    }

    public static Icon pause(int size, Color color) {
        return svg("pause", size, color);
    }

    public static Icon chevron(int size, Color color, boolean left) {
        return svg(left ? "chevron-left" : "chevron-right", size, color);
    }

    public static Icon check(int size, Color color) {
        return svg("check", size, color);
    }

    /** A clipboard page with a clip at the top, used for copying a file path. */
    public static Icon clipboard(int size, Color color) {
        return svg("content-copy", size, color);
    }

    /** A pencil with an eraser, used for renaming. */
    public static Icon pencil(int size, Color color) {
        return svg("pencil", size, color);
    }

    /** An "A -> Z" glyph for sorting by name. */
    public static Icon sortName(int size, Color color) {
        return svg("sort-alphabetical-ascending", size, color);
    }

    /** A clock with an upward arrow: newest photos first. */
    public static Icon sortNewestFirst(int size, Color color) {
        return svg("sort-calendar-descending", size, color);
    }

    /** A clock with a downward arrow: oldest photos first. */
    public static Icon sortOldestFirst(int size, Color color) {
        return svg("sort-calendar-ascending", size, color);
    }

    /** Three vertical bars of increasing height for sorting by size. */
    public static Icon sortBySize(int size, Color color) {
        return svg("sort-numeric-ascending", size, color);
    }

    /** Two opposed arrows, used as the heading icon for sort-order submenus. */
    public static Icon sortOrder(int size, Color color) {
        return svg("sort", size, color);
    }

    /**
     * Styles a menu item so the current selection is readable without overlapping the leading icon.
     * FlatLaf suppresses the native check/radio mark when a custom icon is set, so the selected row
     * is shown with a bold, accent-coloured label instead.
     */
    public static void markSelected(AbstractButton item, boolean selected) {
        Font font = item.getFont();
        if (selected) {
            item.setFont(font != null ? font.deriveFont(Font.BOLD) : Theme.font(Font.BOLD, 13));
            item.setForeground(Theme.ACCENT);
        } else {
            if (font != null) {
                item.setFont(font.deriveFont(Font.PLAIN));
            }
            item.setForeground(null);
        }
    }
}
