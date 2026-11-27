/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.mpv;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Locates the native window behind a Swing component so libmpv can render into it.
 * <p>
 * Only the two {@code FindWindow} variants are needed: the viewer has a unique title, and its
 * heavyweight canvas has the well-known {@code SunAwtCanvas} class name. This avoids reflecting into
 * JDK internals and the extra {@code --add-exports} flag that would require.
 * </p>
 */
public final class Win32Window {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());

    private static final MethodHandle FIND_WINDOW = LINKER.downcallHandle(USER32.findOrThrow("FindWindowW"),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    private static final MethodHandle FIND_WINDOW_EX = LINKER.downcallHandle(USER32.findOrThrow("FindWindowExW"),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private Win32Window() {
    }

    /**
     * Finds a top-level window by its title.
     *
     * @param title the exact window title
     * @return the native window handle, or zero when not found
     */
    public static long window(String title) {
        try (Arena arena = Arena.ofConfined()) {
            return ((MemorySegment) FIND_WINDOW.invokeExact(MemorySegment.NULL, wide(arena, title))).address();
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot find the window " + title, e);
        }
    }

    /**
     * Finds the first child window with the given class name.
     *
     * @param parent the parent window handle
     * @param className the window class, e.g. {@code SunAwtCanvas}
     * @return the native window handle, or zero when not found
     */
    public static long child(long parent, String className) {
        if (parent == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = (MemorySegment) FIND_WINDOW_EX.invokeExact(MemorySegment.ofAddress(parent),
                    MemorySegment.NULL, wide(arena, className), MemorySegment.NULL);
            return handle.address();
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot find the child window " + className, e);
        }
    }

    private static MemorySegment wide(Arena arena, String text) {
        return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
    }
}
