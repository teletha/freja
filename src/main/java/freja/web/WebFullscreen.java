/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.web;

import java.awt.Rectangle;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Locale;

import apocrypha.Window;
import kiss.I;

/**
 * Native fullscreen control for the browser edition's photo viewer.
 * <p>
 * The application is built against apocrypha without the optional avaje-webview API on every
 * classpath, so this helper deliberately avoids {@code Window.browser()}. The private native
 * handle is read reflectively and the window is resized with the Win32 API. The original style and
 * bounds are saved on entry and restored on exit, which brings back the decorated application
 * window instead of leaving a borderless one behind.
 * </p>
 */
final class WebFullscreen {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final int GWL_STYLE = -16;

    /** WS_OVERLAPPEDWINDOW: the whole standard frame, removed while fullscreen. */
    private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;

    private static final int SWP_NOZORDER = 0x0004;

    private static final int SWP_FRAMECHANGED = 0x0020;

    private static final int SWP_SHOWWINDOW = 0x0040;

    private static final int SM_CXSCREEN = 0;

    private static final int SM_CYSCREEN = 1;

    private static final int NO_STYLE = Integer.MIN_VALUE;

    private static final MethodHandle GetWindowLongW;

    private static final MethodHandle SetWindowLongW;

    private static final MethodHandle SetWindowPos;

    private static final MethodHandle GetSystemMetrics;

    private boolean active;

    private Rectangle bounds;

    private int style = NO_STYLE;

    static {
        MethodHandle get = null;
        MethodHandle set = null;
        MethodHandle pos = null;
        MethodHandle metrics = null;
        if (WINDOWS) {
            try {
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                Linker linker = Linker.nativeLinker();
                get = linker.downcallHandle(user32.find("GetWindowLongW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                set = linker.downcallHandle(user32.find("SetWindowLongW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                pos = linker.downcallHandle(user32.find("SetWindowPos").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                metrics = linker.downcallHandle(user32.find("GetSystemMetrics").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            } catch (Throwable e) {
                I.error("freja", e);
            }
        }
        GetWindowLongW = get;
        SetWindowLongW = set;
        SetWindowPos = pos;
        GetSystemMetrics = metrics;
    }

    WebFullscreen() {
    }

    /** @return whether the window is currently fullscreen because of the viewer */
    boolean isActive() {
        return active;
    }

    /** Enters fullscreen, remembering how to return to the normal window. */
    void enter(Window window) {
        if (window == null || active) {
            return;
        }
        bounds = window.bounds();
        long handle = handle(window);
        if (handle == 0 || GetWindowLongW == null) {
            // No native access: maximize as the closest available approximation.
            window.maximize();
            active = true;
            return;
        }
        try {
            MemorySegment hwnd = MemorySegment.ofAddress(handle);
            style = (int) GetWindowLongW.invokeExact(hwnd, GWL_STYLE);
            // invokeExact needs the exact return type at the call site: assigning to int avoids
            // javac compiling this as a void call, which throws WrongMethodTypeException.
            int ignoredStyle = (int) SetWindowLongW.invokeExact(hwnd, GWL_STYLE, style & ~WS_OVERLAPPEDWINDOW);
            int width = (int) GetSystemMetrics.invokeExact(SM_CXSCREEN);
            int height = (int) GetSystemMetrics.invokeExact(SM_CYSCREEN);
            int ignoredPos = (int) SetWindowPos.invokeExact(hwnd, MemorySegment.NULL, 0, 0, width, height, SWP_NOZORDER | SWP_FRAMECHANGED | SWP_SHOWWINDOW);
        } catch (Throwable e) {
            I.error("freja", e);
            style = NO_STYLE;
        }
        active = true;
    }

    /** Restores the window which was visible before the viewer opened. */
    void exit(Window window) {
        if (window == null || !active) {
            return;
        }
        active = false;
        long handle = handle(window);
        if (style != NO_STYLE && handle != 0 && SetWindowLongW != null && bounds != null) {
            try {
                MemorySegment hwnd = MemorySegment.ofAddress(handle);
                int ignoredStyle = (int) SetWindowLongW.invokeExact(hwnd, GWL_STYLE, style);
                int ignoredPos = (int) SetWindowPos.invokeExact(hwnd, MemorySegment.NULL, bounds.x, bounds.y, bounds.width, bounds.height, SWP_NOZORDER | SWP_FRAMECHANGED | SWP_SHOWWINDOW);
                // Keep apocrypha's cached normal bounds in sync with the restored window.
                window.bounds(bounds.x, bounds.y, bounds.width, bounds.height);
                style = NO_STYLE;
                return;
            } catch (Throwable e) {
                I.error("freja", e);
            }
        }
        // Non-Windows platforms and failed native calls still restore the geometry.
        window.setResizable(true);
        if (bounds != null) {
            window.bounds(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        style = NO_STYLE;
    }

    /** Reads apocrypha's private native handle without referring to avaje-webview's Webview type. */
    private static long handle(Window window) {
        try {
            Method method = Window.class.getDeclaredMethod("handle");
            method.setAccessible(true);
            return (long) method.invoke(window);
        } catch (Throwable e) {
            I.error("freja", e);
            return 0;
        }
    }
}
