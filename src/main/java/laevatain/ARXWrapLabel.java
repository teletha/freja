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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;

import laevatain.api.ComponentHelper;
import laevatain.api.TextHelper;

/**
 * A label which breaks its text into lines instead of asking for as much width as the longest line
 * needs. A plain {@code JLabel} never wraps, so a long sentence widens the whole column without
 * limit.
 * <p>
 * The breaking itself is left to Swing: this is a read-only text area with word wrapping turned on,
 * which measures its height against the width it actually has. The component is only dressed up as
 * a label - transparent, borderless, unfocusable - its maximum height pinned, because a vertical
 * {@code BoxLayout} would otherwise stretch it across the leftover room in the row, and its caret
 * made deaf to the viewport, because a caret otherwise drags the scroll pane around.
 */
public final class ARXWrapLabel extends JTextArea
        implements ComponentHelper<ARXWrapLabel>, TextHelper<ARXWrapLabel> {

    /** The width asked for before the label knows how much room it will actually get. */
    private static final int CAPPED_WIDTH = 460;

    /**
     * A caret which never asks the enclosing scroll pane to follow it. {@code DefaultCaret} calls
     * {@code scrollRectToVisible} every time it is repainted, and {@code BasicTextUI} switches the
     * {@code autoscrolls} client property back on whenever a look and feel is (re)installed, so a
     * read-only label dragged the settings document around - straight to the last wrapped
     * description - whenever the theme changed. A caret which is not a {@code UIResource} survives
     * that re-install, which is what keeps the fix in place.
     */
    private static final class StillCaret extends DefaultCaret {

        @Override
        protected void adjustVisibility(Rectangle n) {
            // Deliberately empty: this label is never edited, so its caret has no reason to move
            // the viewport anywhere.
        }

        @Override
        public void paint(Graphics graphics) {
            // Nothing to draw: a caption does not show a caret.
        }
    }

    public ARXWrapLabel() {
        lineWrap(true).wrapStyleWord(true).editable(false).focusable(false).opaque(false)
                .border(BorderFactory.createEmptyBorder())
                .cursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
                .alignmentX(Component.LEFT_ALIGNMENT);
        DefaultCaret caret = new StillCaret();
        caret.setBlinkRate(0);
        caret.setVisible(false);
        caret.setSelectionVisible(false);
        caret(caret);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                // Wrapping happens at the width on hand, so a resize changes the height it needs.
                revalidate();
                repaint();
            }
        });
    }

    /** Replaces the wording. */
    public void setSource(String text) {
        setText(text == null ? "" : text);
        revalidate();
        repaint();
    }

    /** The wording as it was given. */
    public String source() {
        return getText();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (getWidth() <= 0 && size.width > CAPPED_WIDTH) {
            // Not measured yet: do not ask for more width than a settings column will ever give.
            size.width = CAPPED_WIDTH;
        }
        return size;
    }

    @Override
    public Dimension getMaximumSize() {
        // BoxLayout Y_AXIS stretches a child up to its maximum height, which would leave the text
        // floating in an empty box. Keep the height rigid and let the width grow.
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /**
     * Re-measures after a font change. {@code JTextArea} caches its size, and the settings rows set
     * the font after construction.
     */
    @Override
    public void setFont(java.awt.Font font) {
        super.setFont(font);
        if (SwingUtilities.isEventDispatchThread()) {
            revalidate();
            repaint();
        }
    }
}
