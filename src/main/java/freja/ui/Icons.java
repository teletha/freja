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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractButton;
import javax.swing.Icon;

/**
 * Small vector icons drawn with Java2D, so the application does not depend on image assets or on
 * emoji fonts.
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

    private abstract static class Vector implements Icon {

        protected final int size;

        protected final Color color;

        Vector(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setColor(color);
                paint(g);
            } finally {
                g.dispose();
            }
        }

        protected abstract void paint(Graphics2D g);
    }

    public static Icon plus(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float unit = size / 16f;
                g.setStroke(new BasicStroke(1.8f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Float(size * 0.5f, size * 0.22f, size * 0.5f, size * 0.78f));
                g.draw(new java.awt.geom.Line2D.Float(size * 0.22f, size * 0.5f, size * 0.78f, size * 0.5f));
            }
        };
    }

    public static Icon close(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.28f;
                g.setStroke(new BasicStroke(Math.max(1.5f, size / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Float(inset, inset, size - inset, size - inset));
                g.draw(new java.awt.geom.Line2D.Float(size - inset, inset, inset, size - inset));
            }
        };
    }

    public static Icon star(int size, Color color, boolean filled) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                Path2D.Float star = new Path2D.Float();
                double center = size / 2.0;
                double outer = size * 0.45;
                double inner = outer * 0.46;
                for (int i = 0; i < 10; i++) {
                    double radius = i % 2 == 0 ? outer : inner;
                    double angle = -Math.PI / 2 + i * Math.PI / 5;
                    double px = center + Math.cos(angle) * radius;
                    double py = center + Math.sin(angle) * radius;
                    if (i == 0) {
                        star.moveTo(px, py);
                    } else {
                        star.lineTo(px, py);
                    }
                }
                star.closePath();
                if (filled) {
                    g.fill(star);
                } else {
                    g.setStroke(new BasicStroke(Math.max(1.3f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(star);
                }
            }
        };
    }

    public static Icon folder(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float w = size;
                Path2D.Float path = new Path2D.Float();
                path.moveTo(w * 0.1, w * 0.78);
                path.lineTo(w * 0.1, w * 0.28);
                path.quadTo(w * 0.1, w * 0.2, w * 0.18, w * 0.2);
                path.lineTo(w * 0.4, w * 0.2);
                path.lineTo(w * 0.5, w * 0.32);
                path.lineTo(w * 0.82, w * 0.32);
                path.quadTo(w * 0.9, w * 0.32, w * 0.9, w * 0.4);
                path.lineTo(w * 0.9, w * 0.7);
                path.quadTo(w * 0.9, w * 0.78, w * 0.82, w * 0.78);
                path.closePath();
                g.fill(path);
            }
        };
    }

    public static Icon tag(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float w = size;
                Path2D.Float outline = new Path2D.Float();
                outline.moveTo(w * 0.1, w * 0.12);
                outline.lineTo(w * 0.54, w * 0.12);
                outline.lineTo(w * 0.9, w * 0.48);
                outline.quadTo(w * 0.95, w * 0.53, w * 0.9, w * 0.58);
                outline.lineTo(w * 0.58, w * 0.9);
                outline.quadTo(w * 0.53, w * 0.95, w * 0.48, w * 0.9);
                outline.lineTo(w * 0.12, w * 0.54);
                outline.closePath();
                Area shape = new Area(outline);
                shape.subtract(new Area(new Ellipse2D.Float(w * 0.22f, w * 0.24f, w * 0.13f, w * 0.13f)));
                g.fill(shape);
            }
        };
    }

    /**
     * A glyph icon painted with the configured application font, for the cases where a letter reads
     * better than an outline: the font row shows an "A" in the very typeface it configures.
     */
    public static Icon letter(String text, int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Font font = new Font(freja.ui.Theme.fontFamily(), Font.BOLD, Math.round(size * 1.1f));
                g.setFont(font);
                FontMetrics metrics = g.getFontMetrics();
                int x = (size - metrics.stringWidth(text)) / 2;
                int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(text, x, y);
            }
        };
    }

    /** The theme glyph: an outlined circle with its right half filled, like the contrast symbol. */
    public static Icon contrast(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.16f;
                float diameter = size - inset * 2;
                g.setStroke(new BasicStroke(Math.max(1.4f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Float(inset, inset, diameter, diameter));
                g.fill(new Arc2D.Float(inset, inset, diameter, diameter, -90, 180, Arc2D.PIE));
            }
        };
    }

    public static Icon settings(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float w = size;
                Path2D.Float teeth = new Path2D.Float();
                int points = 16;
                for (int i = 0; i < points; i++) {
                    double angle = i * Math.PI * 2 / points - Math.PI / 2;
                    double radius = (i % 2 == 0 ? w * 0.46 : w * 0.34);
                    float x = w / 2 + (float) (Math.cos(angle) * radius);
                    float y = w / 2 + (float) (Math.sin(angle) * radius);
                    if (i == 0) {
                        teeth.moveTo(x, y);
                    } else {
                        teeth.lineTo(x, y);
                    }
                }
                teeth.closePath();
                Area gear = new Area(teeth);
                gear.subtract(new Area(new Ellipse2D.Float(w * 0.34f, w * 0.34f, w * 0.32f, w * 0.32f)));
                g.fill(gear);
            }
        };
    }

    public static Icon grid(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float cell = size * 0.34f;
                float gap = size * 0.12f;
                float start = (size - (cell * 2 + gap)) / 2;
                for (int row = 0; row < 2; row++) {
                    for (int column = 0; column < 2; column++) {
                        g.fill(new RoundRectangle2D.Float(start + column * (cell + gap), start + row * (cell + gap), cell, cell, cell * 0.3f, cell * 0.3f));
                    }
                }
            }
        };
    }

    public static Icon clock(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.16f;
                g.setStroke(new BasicStroke(Math.max(1.4f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Float(inset, inset, size - inset * 2, size - inset * 2));
                float center = size / 2f;
                g.draw(new java.awt.geom.Line2D.Float(center, center, center, size * 0.28f));
                g.draw(new java.awt.geom.Line2D.Float(center, center, size * 0.68f, center + size * 0.1f));
            }
        };
    }

    public static Icon image(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.13f;
                g.setStroke(new BasicStroke(Math.max(1.3f, size / 13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new RoundRectangle2D.Float(inset, inset, size - inset * 2, size - inset * 2, size * 0.2f, size * 0.2f));
                g.fill(new Ellipse2D.Float(size * 0.3f, size * 0.3f, size * 0.13f, size * 0.13f));
                Path2D.Float mountain = new Path2D.Float();
                mountain.moveTo(size * 0.24f, size * 0.72f);
                mountain.lineTo(size * 0.42f, size * 0.5f);
                mountain.lineTo(size * 0.56f, size * 0.64f);
                mountain.lineTo(size * 0.66f, size * 0.53f);
                mountain.lineTo(size * 0.78f, size * 0.72f);
                g.draw(mountain);
            }
        };
    }

    public static Icon search(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.2f;
                float diameter = size * 0.44f;
                g.setStroke(new BasicStroke(Math.max(1.4f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Ellipse2D.Float(inset, inset, diameter, diameter));
                g.draw(new java.awt.geom.Line2D.Float(size * 0.58f, size * 0.58f, size * 0.78f, size * 0.78f));
            }
        };
    }

    public static Icon refresh(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.2f;
                float diameter = size - inset * 2;
                g.setStroke(new BasicStroke(Math.max(1.4f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Arc2D.Float(inset, inset, diameter, diameter, 55, 250, Arc2D.OPEN));
                Path2D.Float arrow = new Path2D.Float();
                arrow.moveTo(size * 0.76f, size * 0.1f);
                arrow.lineTo(size * 0.9f, size * 0.28f);
                arrow.lineTo(size * 0.68f, size * 0.32f);
                arrow.closePath();
                g.fill(arrow);
            }
        };
    }

    public static Icon play(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                Path2D.Float triangle = new Path2D.Float();
                triangle.moveTo(size * 0.3f, size * 0.18f);
                triangle.lineTo(size * 0.82f, size * 0.5f);
                triangle.lineTo(size * 0.3f, size * 0.82f);
                triangle.closePath();
                g.fill(triangle);
            }
        };
    }

    public static Icon sun(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float center = size / 2f;
                float radius = size * 0.19f;
                g.fill(new Ellipse2D.Float(center - radius, center - radius, radius * 2, radius * 2));
                g.setStroke(new BasicStroke(Math.max(1.3f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float inner = size * 0.32f;
                    float outer = size * 0.45f;
                    g.draw(new java.awt.geom.Line2D.Float(center + (float) Math.cos(angle) * inner, center + (float) Math.sin(angle) * inner, center + (float) Math.cos(angle) * outer, center + (float) Math.sin(angle) * outer));
                }
            }
        };
    }

    public static Icon moon(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float inset = size * 0.16f;
                float diameter = size - inset * 2;
                Area moon = new Area(new Ellipse2D.Float(inset, inset, diameter, diameter));
                moon.subtract(new Area(new Ellipse2D.Float(inset + diameter * 0.28f, inset - diameter * 0.12f, diameter * 0.95f, diameter * 0.95f)));
                g.fill(moon);
            }
        };
    }

    public static Icon pause(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float width = size * 0.2f;
                float height = size * 0.56f;
                float top = (size - height) / 2;
                g.fill(new RoundRectangle2D.Float(size * 0.28f, top, width, height, 2, 2));
                g.fill(new RoundRectangle2D.Float(size * 0.52f, top, width, height, 2, 2));
            }
        };
    }

    public static Icon chevron(int size, Color color, boolean left) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(2f, size / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (left) {
                    g.draw(new java.awt.geom.Line2D.Float(size * 0.62f, size * 0.2f, size * 0.34f, size * 0.5f));
                    g.draw(new java.awt.geom.Line2D.Float(size * 0.34f, size * 0.5f, size * 0.62f, size * 0.8f));
                } else {
                    g.draw(new java.awt.geom.Line2D.Float(size * 0.38f, size * 0.2f, size * 0.66f, size * 0.5f));
                    g.draw(new java.awt.geom.Line2D.Float(size * 0.66f, size * 0.5f, size * 0.38f, size * 0.8f));
                }
            }
        };
    }

    public static Icon check(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                g.setStroke(new BasicStroke(Math.max(1.8f, size / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D.Float path = new Path2D.Float();
                path.moveTo(size * 0.22f, size * 0.52f);
                path.lineTo(size * 0.42f, size * 0.72f);
                path.lineTo(size * 0.8f, size * 0.28f);
                g.draw(path);
            }
        };
    }

    /** A clipboard page with a clip at the top, used for copying a file path. */
    public static Icon clipboard(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float unit = size / 16f;
                g.setStroke(new BasicStroke(1.4f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float left = size * 0.22f;
                float right = size * 0.78f;
                float top = size * 0.26f;
                float bottom = size * 0.84f;
                g.draw(new RoundRectangle2D.Float(left, top, right - left, bottom - top, size * 0.12f, size * 0.12f));
                g.fill(new RoundRectangle2D.Float(size * 0.36f, size * 0.16f, size * 0.28f, size * 0.16f, size * 0.06f, size * 0.06f));
                g.draw(new java.awt.geom.Line2D.Float(size * 0.32f, size * 0.48f, size * 0.68f, size * 0.48f));
                g.draw(new java.awt.geom.Line2D.Float(size * 0.32f, size * 0.64f, size * 0.58f, size * 0.64f));
            }
        };
    }

    /** A pencil with an eraser, used for renaming. */
    public static Icon pencil(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float bodyX = size * 0.38f;
                float bodyW = size * 0.24f;
                float bodyY = size * 0.18f;
                float bodyH = size * 0.54f;
                g.fill(new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, size * 0.06f, size * 0.06f));
                Path2D.Float tip = new Path2D.Float();
                tip.moveTo(bodyX, bodyY + bodyH);
                tip.lineTo(bodyX + bodyW, bodyY + bodyH);
                tip.lineTo(size * 0.5f, size * 0.92f);
                tip.closePath();
                g.fill(tip);
                g.fill(new RoundRectangle2D.Float(bodyX, size * 0.08f, bodyW, size * 0.14f, size * 0.05f, size * 0.05f));
            }
        };
    }

    /** An "A -> Z" glyph for sorting by name. */
    public static Icon sortName(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float unit = size / 16f;
                g.setStroke(new BasicStroke(1.4f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float ax = size * 0.24f;
                float ay = size * 0.24f;
                float aHeight = size * 0.48f;
                g.draw(new java.awt.geom.Line2D.Float(ax, ay + aHeight, ax + size * 0.07f, ay));
                g.draw(new java.awt.geom.Line2D.Float(ax + size * 0.07f, ay, ax + size * 0.14f, ay + aHeight));
                g.draw(new java.awt.geom.Line2D.Float(ax + size * 0.03f, ay + aHeight * 0.55f, ax + size * 0.11f, ay + aHeight * 0.55f));
                float zx = size * 0.58f;
                float zWidth = size * 0.22f;
                g.draw(new java.awt.geom.Line2D.Float(zx, ay, zx + zWidth, ay));
                g.draw(new java.awt.geom.Line2D.Float(zx + zWidth, ay, zx, ay + aHeight));
                g.draw(new java.awt.geom.Line2D.Float(zx, ay + aHeight, zx + zWidth, ay + aHeight));
                float arrowY = size * 0.83f;
                g.draw(new java.awt.geom.Line2D.Float(size * 0.22f, arrowY, size * 0.78f, arrowY));
                Path2D.Float head = new Path2D.Float();
                head.moveTo(size * 0.72f, arrowY - size * 0.06f);
                head.lineTo(size * 0.78f, arrowY);
                head.lineTo(size * 0.72f, arrowY + size * 0.06f);
                g.draw(head);
            }
        };
    }

    /** A clock with an upward arrow: newest photos first. */
    public static Icon sortNewestFirst(int size, Color color) {
        return sortDate(size, color, true);
    }

    /** A clock with a downward arrow: oldest photos first. */
    public static Icon sortOldestFirst(int size, Color color) {
        return sortDate(size, color, false);
    }

    private static Icon sortDate(int size, Color color, boolean newestFirst) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float unit = size / 16f;
                g.setStroke(new BasicStroke(1.4f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float inset = size * 0.18f;
                float clockSize = size * 0.58f;
                g.draw(new Ellipse2D.Float(inset, size * 0.5f - clockSize * 0.5f, clockSize, clockSize));
                float cx = inset + clockSize * 0.5f;
                float cy = size * 0.5f;
                g.draw(new java.awt.geom.Line2D.Float(cx, cy, cx, cy - clockSize * 0.28f));
                g.draw(new java.awt.geom.Line2D.Float(cx, cy, cx + clockSize * 0.25f, cy + clockSize * 0.08f));
                float arrowX = size * 0.78f;
                float arrowTop = size * 0.28f;
                float arrowBottom = size * 0.72f;
                float headY = newestFirst ? arrowTop : arrowBottom;
                float tailY = newestFirst ? arrowBottom : arrowTop;
                g.draw(new java.awt.geom.Line2D.Float(arrowX, tailY, arrowX, headY));
                Path2D.Float head = new Path2D.Float();
                if (newestFirst) {
                    head.moveTo(arrowX - size * 0.05f, headY + size * 0.06f);
                    head.lineTo(arrowX, headY);
                    head.lineTo(arrowX + size * 0.05f, headY + size * 0.06f);
                } else {
                    head.moveTo(arrowX - size * 0.05f, headY - size * 0.06f);
                    head.lineTo(arrowX, headY);
                    head.lineTo(arrowX + size * 0.05f, headY - size * 0.06f);
                }
                g.draw(head);
            }
        };
    }

    /** Three vertical bars of increasing height for sorting by size. */
    public static Icon sortBySize(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float barW = size * 0.18f;
                float gap = size * 0.07f;
                float base = size * 0.78f;
                float[] heights = { size * 0.28f, size * 0.44f, size * 0.62f };
                for (int i = 0; i < heights.length; i++) {
                    float x = size * 0.16f + i * (barW + gap);
                    float h = heights[i];
                    g.fill(new RoundRectangle2D.Float(x, base - h, barW, h, barW * 0.3f, barW * 0.3f));
                }
            }
        };
    }

    /** Two opposed arrows, used as the heading icon for sort-order submenus. */
    public static Icon sortOrder(int size, Color color) {
        return new Vector(size, color) {
            @Override
            protected void paint(Graphics2D g) {
                float unit = size / 16f;
                g.setStroke(new BasicStroke(1.5f * unit, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                float cx = size * 0.5f;
                float top = size * 0.16f;
                float bottom = size * 0.84f;
                g.draw(new java.awt.geom.Line2D.Float(cx, top + size * 0.12f, cx, bottom - size * 0.12f));
                Path2D.Float up = new Path2D.Float();
                up.moveTo(cx - size * 0.14f, top + size * 0.14f);
                up.lineTo(cx, top);
                up.lineTo(cx + size * 0.14f, top + size * 0.14f);
                g.draw(up);
                Path2D.Float down = new Path2D.Float();
                down.moveTo(cx - size * 0.14f, bottom - size * 0.14f);
                down.lineTo(cx, bottom);
                down.lineTo(cx + size * 0.14f, bottom - size * 0.14f);
                g.draw(down);
            }
        };
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
