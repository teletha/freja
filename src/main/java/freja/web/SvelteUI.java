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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import apocrypha.Response;
import apocrypha.Route;
import bun4j.Bun;
import bun4j.Framework;
import kiss.I;

/**
 * Builds and serves the Svelte part of the browser UI.
 * <p>
 * When the frontend sources ({@code src/main/frontend}) are available next to the application,
 * they are compiled through bun4j on every start. The generated files are served from the data
 * directory through the {@code /svelte/} route. A packaged application has no source tree, so the
 * prebuilt copy under {@code web/svelte} is used as a fallback.
 * </p>
 */
public final class SvelteUI {

    /** The URL prefix of the generated assets. */
    public static final String PREFIX = "/svelte/";

    /** The system property which overrides the frontend source location. */
    public static final String SOURCE_PROPERTY = "freja.frontend";

    private SvelteUI() {
    }

    /**
     * Bundles the frontend sources into the application data directory.
     *
     * @param dataDirectory The writable application directory.
     * @return The directory containing the generated assets, or null when the sources are absent or
     *         the bundle failed (the prebuilt assets are used in that case).
     */
    public static Path bundle(Path dataDirectory) {
        Path source = sourceDirectory();
        if (source == null) {
            I.info("freja", "Svelte sources are not available; using the bundled assets");
            return null;
        }

        Path output = dataDirectory.resolve("svelte");
        try {
            Files.createDirectories(output);
            Bun.bundle(option -> option.directory(source)
                    .entry("src/main.js")
                    .outdir(output.toString())
                    .framework(Framework.SVELTE)
                    .minify(true)
                    .splitting(false)
                    .installPlugins(true)
                    .timeout(Duration.ofSeconds(60))
                    .stdout(line -> I.info("bun4j", line))
                    .stderr(line -> I.warn("bun4j", line)))
                    .requireSuccess();
            return output;
        } catch (RuntimeException | IOException e) {
            I.warn("freja", new IOException("Cannot bundle the Svelte frontend", e));
            return null;
        }
    }

    /**
     * Locates the frontend project. The search walks up from the working directory so launching
     * Freja from a subfolder of the checkout still rebuilds the UI.
     *
     * @return The frontend directory, or null outside a source checkout.
     */
    private static Path sourceDirectory() {
        String configured = System.getProperty(SOURCE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath();
            return Files.isDirectory(path.resolve("src")) ? path : null;
        }

        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            Path candidate = current.resolve("src/main/frontend");
            if (Files.isDirectory(candidate.resolve("src"))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Creates a route which serves one generated asset by its file name.
     *
     * @param directory The bundle output directory.
     * @return The route.
     */
    public static Route route(Path directory) {
        return (path, query) -> {
            String name = path.startsWith("/") ? path.substring(1) : path;
            if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
                return null;
            }

            Path file = directory.resolve(name).normalize();
            String mime = mimeType(name);
            if (mime == null || !file.startsWith(directory) || !Files.isRegularFile(file)) {
                return null;
            }
            return Response.ok(mime, Files.readAllBytes(file)).cache("no-cache");
        };
    }

    /** @return the content type of a generated asset, or null for unknown files */
    private static String mimeType(String name) {
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        } else if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        } else if (name.endsWith(".map")) {
            return "application/json; charset=utf-8";
        }
        return null;
    }
}
