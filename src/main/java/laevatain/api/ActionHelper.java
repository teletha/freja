/*
 * Copyright (C) 2026 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package laevatain.api;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentListener;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionListener;

/**
 * Fluent listener registration. The {@code addXxxListener} methods are declared by
 * {@link Component} and its subclasses and all return void, so a class method would win over an
 * interface default with the same signature; the helpers therefore use {@code onXxx} names instead.
 * <p>
 * Listeners which only some components support are guarded by type checks and are no-ops on
 * unsupported components, so one interface can be shared by every component.
 * </p>
 *
 * @param <C> the concrete component type returned by every helper, so chains keep the caller's type
 */
public interface ActionHelper<C extends Component> {
    /**
     * Registers an action which ignores the event. Supported on buttons, text fields and combo boxes;
     * other components ignore it.
     *
     * @param action the action to run
     * @return this component
     */
    default C onAction(Runnable action) {
        ActionListener listener = event -> action.run();
        if (this instanceof AbstractButton button) {
            button.addActionListener(listener);
        } else if (this instanceof JTextField field) {
            field.addActionListener(listener);
        } else if (this instanceof JComboBox<?> combo) {
            combo.addActionListener(listener);
        }
        return self();
    }

    /**
     * Registers an action which receives the event. Supported on the same components as
     * {@link #onAction(Runnable)}.
     *
     * @param action the action to run
     * @return this component
     */
    default C onAction(Consumer<ActionEvent> action) {
        ActionListener listener = action::accept;
        if (this instanceof AbstractButton button) {
            button.addActionListener(listener);
        } else if (this instanceof JTextField field) {
            field.addActionListener(listener);
        } else if (this instanceof JComboBox<?> combo) {
            combo.addActionListener(listener);
        }
        return self();
    }

    /**
     * Registers a raw mouse listener, declared by {@link Component}.
     *
     * @param listener the listener
     * @return this component
     */
    default C onMouse(MouseListener listener) {
        ((Component) this).addMouseListener(listener);
        return self();
    }

    /**
     * Registers a raw mouse-motion listener, declared by {@link Component}.
     *
     * @param listener the listener
     * @return this component
     */
    default C onMouseMotion(MouseMotionListener listener) {
        ((Component) this).addMouseMotionListener(listener);
        return self();
    }

    /**
     * Runs a handler when the component is clicked.
     *
     * @param handler the handler
     * @return this component
     */
    default C onClick(Consumer<MouseEvent> handler) {
        return onMouse(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler on every press, e.g. to report navigation on the press instead of a synthetic
     * click which a dragged pointer would swallow.
     *
     * @param handler the handler
     * @return this component
     */
    default C onPressed(Consumer<MouseEvent> handler) {
        return onMouse(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler on every release.
     *
     * @param handler the handler
     * @return this component
     */
    default C onReleased(Consumer<MouseEvent> handler) {
        return onMouse(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler when the pointer enters the component.
     *
     * @param handler the handler
     * @return this component
     */
    default C onEntered(Consumer<MouseEvent> handler) {
        return onMouse(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler when the pointer leaves the component.
     *
     * @param handler the handler
     * @return this component
     */
    default C onExited(Consumer<MouseEvent> handler) {
        return onMouse(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler when a key goes down.
     *
     * @param handler the handler
     * @return this component
     */
    default C onKeyPressed(Consumer<KeyEvent> handler) {
        return onKey(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Runs a handler when a key goes up.
     *
     * @param handler the handler
     * @return this component
     */
    default C onKeyReleased(Consumer<KeyEvent> handler) {
        return onKey(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent event) {
                handler.accept(event);
            }
        });
    }

    /**
     * Registers a raw key listener, declared by {@link Component}.
     *
     * @param listener the listener
     * @return this component
     */
    default C onKey(KeyListener listener) {
        ((Component) this).addKeyListener(listener);
        return self();
    }

    /**
     * Registers a raw focus listener, declared by {@link Component}.
     *
     * @param listener the listener
     * @return this component
     */
    default C onFocus(FocusListener listener) {
        ((Component) this).addFocusListener(listener);
        return self();
    }

    /**
     * Registers a raw component listener, declared by {@link Component}.
     *
     * @param listener the listener
     * @return this component
     */
    default C onComponent(ComponentListener listener) {
        ((Component) this).addComponentListener(listener);
        return self();
    }

    /**
     * Registers a change listener on the components which support one. A change listener belongs to
     * buttons, sliders, tabbed panes and spinners rather than to {@link Component} itself.
     *
     * @param listener the listener
     * @return this component
     */
    default C onChange(ChangeListener listener) {
        if (this instanceof AbstractButton button) {
            button.addChangeListener(listener);
        } else if (this instanceof JSlider slider) {
            slider.addChangeListener(listener);
        } else if (this instanceof JTabbedPane pane) {
            pane.addChangeListener(listener);
        } else if (this instanceof JSpinner spinner) {
            spinner.addChangeListener(listener);
        }
        return self();
    }

    /**
     * Registers a list-selection listener on a {@link JList}; a no-op on other components.
     *
     * @param listener the listener
     * @return this component
     */
    default C onSelection(ListSelectionListener listener) {
        if (this instanceof JList<?> list) {
            list.addListSelectionListener(listener);
        }
        return self();
    }

    @SuppressWarnings("unchecked")
    private C self() {
        return (C) this;
    }
}
