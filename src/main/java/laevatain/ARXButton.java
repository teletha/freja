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
import java.awt.Container;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.ui.FlatLineBorder;

import kiss.Disposable;
import kiss.Variable;
import laevatain.api.ComponentHelper;

/**
 * A reusable FlatLaf button with a flat look, rounded corners and a hover style. All colours and
 * the
 * corner radius come from the active FlatLaf theme, so the button follows runtime theme changes.
 */
public final class ARXButton extends JButton implements ComponentHelper<ARXButton> {

    /** Predefined button roles, each mapped to FlatLaf UI defaults. */
    public enum Variant {
        PRIMARY, SECONDARY, TINTED, DANGER
    }

    private static final Insets PADDING = new Insets(3, 6, 3, 6);

    private final Variant variant;

    /**
     * When true, the resting state blends into the enclosing panel and only hover paints a colour.
     */
    private final boolean blendWithParent;

    private boolean hovering;

    /** Toggle buttons use a soft accent fill instead of a check mark to show their active state. */
    private boolean selected;

    /**
     * @param text the button label, or null for an icon-only button
     * @param icon the icon, or null for a text-only button
     * @param variant the role whose FlatLaf colours are used
     * @param action invoked on click
     */
    public ARXButton(String text, Icon icon, Variant variant, Runnable action) {
        // Secondary buttons have no resting fill of their own: they merge into the surrounding
        // panel and only paint FlatLaf's hover colour while the pointer is over them.
        this(text, icon, variant, action, variant == Variant.SECONDARY);
    }

    /**
     * @param text the button label, or null for an icon-only button
     * @param icon the icon, or null for a text-only button
     * @param variant the role whose FlatLaf colours are used
     * @param action invoked on click
     */
    public ARXButton(Variable<String> text, Icon icon, Variant variant, Runnable action) {
        // Secondary buttons have no resting fill of their own: they merge into the surrounding
        // panel and only paint FlatLaf's hover colour while the pointer is over them.
        this(text, icon, variant, action, variant == Variant.SECONDARY);
    }

    /**
     * @param blendWithParent whether the resting background matches the enclosing panel instead of
     *            the FlatLaf button background
     */
    public ARXButton(String text, Icon icon, Variant variant, Runnable action, boolean blendWithParent) {
        this(text == null ? null : Variable.of(text), icon, variant, action, blendWithParent);
    }

    /**
     * @param blendWithParent whether the resting background matches the enclosing panel instead of
     *            the FlatLaf button background
     */
    public ARXButton(Variable<String> text, Icon icon, Variant variant, Runnable action, boolean blendWithParent) {
        super(text == null ? null : text.get(), icon);
        setText(text);
        this.variant = variant;
        this.blendWithParent = blendWithParent;
        setFocusable(false);
        setOpaque(true);
        setContentAreaFilled(true);
        setBorderPainted(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovering = true;
                applyStyle();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovering = false;
                applyStyle();
            }
        });
        addActionListener(event -> action.run());
        applyStyle();
    }

    /** Whether this button is in its active state, painted with the soft accent fill. */
    public boolean isSelected() {
        return selected;
    }

    /** Changes the active state and repaints with the corresponding style. */
    public void setSelected(boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        applyStyle();
    }

    private Disposable textBinding;

    private Disposable tipBinding;

    public void setText(Variable<String> text) {
        if (textBinding != null) {
            textBinding.dispose();
            textBinding = null;
        }
        if (text == null) {
            super.setText(null);
        } else {
            textBinding = text.observing().to(this::setText);
        }
    }

    /** Binds the tooltip to a live value; passing null clears it. */
    public void setToolTipText(Variable<String> text) {
        if (tipBinding != null) {
            tipBinding.dispose();
        }
        if (text == null) {
            super.setToolTipText(null);
            tipBinding = null;
        } else {
            tipBinding = text.observing().to(this::setToolTipText);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // The superclass constructor calls updateUI() before 'variant' is assigned.
        if (variant != null) {
            applyStyle();
        }
    }

    private void applyStyle() {
        Color background = background();
        Color foreground = foreground();
        setBackground(background);
        setForeground(foreground);
        // A zero-width flat line border supplies the arc FlatLaf paints the background with.
        setBorder(new FlatLineBorder(PADDING, background, 0, UIManager.getInt("Button.arc")));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (variant != null) {
            applyStyle();
        }
    }

    private Color background() {
        if (selected) {
            return color(hovering ? "ARXButton.selectedHoverBackground" : "ARXButton.selectedBackground",
                    "ARXButton.tintedBackground");
        }
        if (blendWithParent && !hovering) {
            return enclosingBackground();
        }
        if (variant == Variant.PRIMARY) {
            return color(hovering ? "Button.defaultHoverBackground" : "Button.defaultBackground", "Button.background");
        }
        if (variant == Variant.TINTED) {
            // A soft accent fill: visible as an action, but far lighter than a solid primary.
            return color(hovering ? "ARXButton.tintedHoverBackground" : "ARXButton.tintedBackground", "Button.background");
        }
        if (variant == Variant.TINTED) {
            // A soft accent fill: prominent without the weight of a solid primary button.
            return color(hovering ? "ARXButton.tintedHoverBackground" : "ARXButton.tintedBackground", "Button.background");
        }
        if (hovering && blendWithParent) {
            // Use the shared hover shade supplied by the active theme; fall back to deriving one
            // from the enclosing panel when the key is absent.
            Color themed = UIManager.getColor("ARXButton.hoverBackground");
            return themed != null ? themed : derivedHover(enclosingBackground());
        }
        if (hovering) {
            return color("Button.hoverBackground", "Button.background");
        }
        return color("Button.background", "Button.background");
    }

    private Color foreground() {
        if (selected) {
            return color("ARXButton.selectedForeground", "ARXButton.tintedForeground");
        }
        if (variant == Variant.DANGER) {
            return color("Component.error.borderColor", "Button.foreground");
        }
        if (variant == Variant.PRIMARY) {
            return color(hovering ? "Button.defaultHoverForeground" : "Button.defaultForeground", "Button.foreground");
        }
        if (variant == Variant.TINTED) {
            return color(hovering ? "ARXButton.tintedHoverForeground" : "ARXButton.tintedForeground", "Button.foreground");
        }
        return color(hovering ? "Button.hoverForeground" : "Button.foreground", "Button.foreground");
    }

    /** Finds the first opaque ancestor so a flat button can visually merge into its panel. */
    private Color enclosingBackground() {
        for (Container parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent.isOpaque() && parent.getBackground() != null) {
                return parent.getBackground();
            }
        }
        return color("Panel.background", "Button.background");
    }

    /** Darkens a light base slightly and lightens a dark one, keeping its hue and saturation. */
    private static Color derivedHover(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float brightness = (base.getRed() * 0.299f + base.getGreen() * 0.587f + base.getBlue() * 0.114f) / 255f;
        float delta = brightness > 0.5f ? -0.06f : 0.12f;
        float value = Math.max(0f, Math.min(1f, hsb[2] + delta));
        Color derived = new Color(Color.HSBtoRGB(hsb[0], hsb[1], value));
        return new Color(derived.getRed(), derived.getGreen(), derived.getBlue(), base.getAlpha());
    }

    private static Color color(String key, String fallbackKey) {
        Color value = UIManager.getColor(key);
        return value != null ? value : UIManager.getColor(fallbackKey);
    }
}
