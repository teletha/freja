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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/**
 * Small vector icons drawn with Java2D, so the application does not depend on image assets or on
 * emoji fonts.
 */
public final class Icons {

    private Icons() {
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
}
