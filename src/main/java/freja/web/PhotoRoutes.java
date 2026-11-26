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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import apocrypha.Response;
import apocrypha.Route;
import freja.model.Photo;
import freja.store.Archives;
import freja.store.Library;
import freja.util.ImageTransforms;
import imagify.ImageFormat;
import imagify.ImageWriter;
import imagify.Imagify;
import kiss.I;

/**
 * The {@code /photo/} route of the Web edition.
 * <p>
 * Thumbnails and photo originals are binary content, so they cross the boundary over HTTP instead
 * of base64 through IPC. The renderer references them with an ordinary URL
 * ({@code /photo/{id}/thumb?v={modifiedAt}}), which lets the browser cache, lazy-load and bust the
 * entries itself; the route only maps identifiers to catalog rows and file bytes. Formats the
 * browser cannot display are transcoded to JPEG on the fly.
 * </p>
 */
public final class PhotoRoutes implements Route {

    private final FrejaWeb app;

    /**
     * Creates the routes.
     *
     * @param app the running application, or null before it is ready
     */
    public PhotoRoutes(FrejaWeb app) {
        this.app = app;
    }

    /**
     * Handle one request.
     *
     * @param path the decoded request path below the registered prefix, starting with a slash
     * @param query the raw query string, or null for none
     * @return a response, or null to fall through to the classpath resources
     */
    @Override
    public Response handle(String path, String query) {
        Library library = app == null ? null : app.library();
        if (library == null) {
            return Response.notFound();
        }
        String[] parts = path.split("/");
        if (parts.length < 3) {
            return Response.notFound();
        }
        long id;
        try {
            id = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return Response.notFound();
        }
        String action = parts[2];
        if ("thumb".equals(action)) {
            return thumbnail(library, id);
        }
        if ("original".equals(action)) {
            return original(library, id);
        }
        return Response.notFound();
    }

    /**
     * Reads one cached thumbnail.
     *
     * @param library the catalog
     * @param id the photo identifier
     * @return a response, or null when the thumbnail is absent
     */
    private Response thumbnail(Library library, long id) {
        Optional<Library.Thumbnail> thumbnail = library.thumbnail(id);
        if (thumbnail.isPresent()) {
            return Response.ok("image/webp", thumbnail.get().data()).cache("public, max-age=86400");
        }
        // Photos imported before thumbnailing, or entries whose thumbnail was invalidated, still
        // deserve a visible card. Decode the original once and let the browser keep the result.
        Optional<Photo> photo = library.photo(id);
        if (photo.isEmpty()) {
            return Response.notFound();
        }
        Photo item = photo.get();
        Path file = item.path();
        if (!item.archived() && (file == null || !Files.isRegularFile(file))) {
            return Response.notFound();
        }
        try {
            BufferedImage decoded = Imagify.read(Archives.read(item)).toBufferedImage();
            BufferedImage oriented = ImageTransforms.orient(decoded, item.orientation());
            BufferedImage scaled = ImageTransforms.scale(oriented, 320);
            byte[] jpeg = ImageWriter.toBytes(scaled, ImageFormat.JPEG, 0.85);
            return Response.ok("image/jpeg", jpeg).cache("public, max-age=3600");
        } catch (IOException | RuntimeException | Error e) {
            I.error("freja", new IOException("Cannot create a thumbnail for " + file, e));
            return Response.notFound();
        }
    }

    /**
     * Reads one photo original, transcoding formats the browser cannot display.
     *
     * @param library the catalog
     * @param id the photo identifier
     * @return a response, or null when the file is missing
     */
    private Response original(Library library, long id) {
        Optional<Photo> photo = library.photo(id);
        if (photo.isEmpty()) {
            return Response.notFound();
        }
        Photo item = photo.get();
        Path file = item.path();
        if (!item.archived() && (file == null || !Files.isRegularFile(file))) {
            return Response.notFound();
        }
        try {
            byte[] encoded = Archives.read(item);
            String extension = item.extension() == null ? "" : item.extension().toLowerCase();
            String mime = mime(extension);
            if (mime != null) {
                return Response.ok(mime, encoded).cache("public, max-age=86400");
            }
            // Formats without a browser codec: decode, rotate and hand back a JPEG.
            BufferedImage decoded = Imagify.read(encoded).toBufferedImage();
            BufferedImage oriented = ImageTransforms.orient(decoded, item.orientation());
            byte[] jpeg = ImageWriter.toBytes(oriented, ImageFormat.JPEG, 0.92);
            return Response.ok("image/jpeg", jpeg).cache("public, max-age=86400");
        } catch (IOException | RuntimeException | Error e) {
            I.error("freja", new IOException("Cannot read the original " + file, e));
            return Response.notFound();
        }
    }

    private String mime(String extension) {
        if (extension == null) {
            return null;
        }
        if (extension.equals("jpg") || extension.equals("jpeg")) {
            return "image/jpeg";
        }
        if (extension.equals("png")) {
            return "image/png";
        }
        if (extension.equals("webp")) {
            return "image/webp";
        }
        if (extension.equals("gif")) {
            return "image/gif";
        }
        if (extension.equals("bmp")) {
            return "image/bmp";
        }
        if (extension.equals("svg")) {
            return "image/svg+xml";
        }
        if (extension.equals("apng")) {
            return "image/apng";
        }
        if (extension.equals("avif")) {
            return "image/avif";
        }
        if (extension.equals("ico")) {
            return "image/x-icon";
        }
        return null;
    }
}
