/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * The Foreign Function &amp; Memory bindings of the libmpv client API.
 * <p>
 * This class is intentionally a thin layer: it maps the handful of C functions Freja needs and
 * leaves policy (events, error handling, lifecycle) to {@link Mpv}. All calls are made through the
 * global arena so the loaded library stays available for the lifetime of the JVM.
 * </p>
 */
final class MpvBindings {

    /** {@code typedef struct mpv_event { int; int; uint64_t; void*; } mpv_event;} */
    static final MemoryLayout EVENT_LAYOUT = MemoryLayout.structLayout(JAVA_INT.withName("event_id"),
            JAVA_INT.withName("error"), ValueLayout.JAVA_LONG.withName("reply_userdata"), ADDRESS.withName("data"));

    // mpv_event starts with int event_id, int error, uint64_t reply_userdata, void* data.
    static final long EVENT_ID = 0;

    static final long EVENT_ERROR = 4;

    private final Linker linker = Linker.nativeLinker();

    private final MethodHandle version;
    private final MethodHandle create;
    private final MethodHandle initialize;
    private final MethodHandle setString;
    private final MethodHandle setOption;
    private final MethodHandle setLong;
    private final MethodHandle getString;
    private final MethodHandle free;
    private final MethodHandle command;
    private final MethodHandle waitEvent;
    private final MethodHandle terminate;
    private final MethodHandle errorString;

    MpvBindings(Path dll) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(dll, Arena.global());
        ValueLayout cLong = (ValueLayout) linker.canonicalLayouts().get("long");

        version = link(lookup, "mpv_client_api_version", FunctionDescriptor.of(cLong));
        create = link(lookup, "mpv_create", FunctionDescriptor.of(ADDRESS));
        initialize = link(lookup, "mpv_initialize", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        setString = link(lookup, "mpv_set_property_string", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        setOption = link(lookup, "mpv_set_option_string", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        setLong = link(lookup, "mpv_set_property", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        getString = link(lookup, "mpv_get_property_string", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
        free = link(lookup, "mpv_free", FunctionDescriptor.ofVoid(ADDRESS));
        command = link(lookup, "mpv_command", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        waitEvent = link(lookup, "mpv_wait_event", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_DOUBLE));
        terminate = link(lookup, "mpv_terminate_destroy", FunctionDescriptor.ofVoid(ADDRESS));
        errorString = link(lookup, "mpv_error_string", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    }

    private MethodHandle link(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(lookup.findOrThrow(name), descriptor);
    }

    long version() throws Throwable {
        return ((Number) version.invoke()).longValue();
    }

    MemorySegment create() throws Throwable {
        return (MemorySegment) create.invokeExact();
    }

    int initialize(MemorySegment handle) throws Throwable {
        return (int) initialize.invokeExact(handle);
    }

    int setString(MemorySegment handle, String name, String value, Arena arena) throws Throwable {
        return (int) setString.invokeExact(handle, arena.allocateFrom(name), arena.allocateFrom(value));
    }

    int setOption(MemorySegment handle, String name, String value, Arena arena) throws Throwable {
        return (int) setOption.invokeExact(handle, arena.allocateFrom(name), arena.allocateFrom(value));
    }

    int setLong(MemorySegment handle, String name, long value, Arena arena) throws Throwable {
        MemorySegment pointer = arena.allocate(ValueLayout.JAVA_LONG);
        pointer.set(ValueLayout.JAVA_LONG, 0, value);
        return (int) setLong.invokeExact(handle, arena.allocateFrom(name), 4, pointer);
    }

    String getString(MemorySegment handle, String name, Arena arena) throws Throwable {
        MemorySegment result = (MemorySegment) getString.invokeExact(handle, arena.allocateFrom(name));
        if (result.equals(MemorySegment.NULL)) {
            return null;
        }
        String value = result.reinterpret(Long.MAX_VALUE).getString(0);
        free.invokeExact(result);
        return value;
    }

    int command(MemorySegment handle, Arena arena, String... args) throws Throwable {
        MemorySegment array = arena.allocate(ADDRESS, (long) args.length + 1);
        for (int i = 0; i < args.length; i++) {
            array.setAtIndex(ADDRESS, i, arena.allocateFrom(args[i]));
        }
        array.setAtIndex(ADDRESS, args.length, MemorySegment.NULL);
        return (int) command.invokeExact(handle, array);
    }

    MemorySegment waitEvent(MemorySegment handle, double timeout) throws Throwable {
        return (MemorySegment) waitEvent.invokeExact(handle, timeout);
    }

    void terminate(MemorySegment handle) throws Throwable {
        terminate.invokeExact(handle);
    }

    String error(int code) throws Throwable {
        MemorySegment message = (MemorySegment) errorString.invokeExact(code);
        return message.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
