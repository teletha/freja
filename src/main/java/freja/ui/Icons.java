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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;

/**
 * Bundled SVG icons loaded from the classpath. The application stays offline-capable because the
 * assets are shipped under {@code src/main/resources/icons/mdi}. During development, missing icons
 * are fetched once from Iconify and cached in the source resource tree and in the compiled class
 * path, so the next build bundles them and a packaged application never needs the network.
 */
public final class Icons {

    private static final Logger LOG = Logger.getLogger(Icons.class.getName());

    private static final String ICONIFY = "https://api.iconify.design/mdi/";

    private Icons() {
    }

    /** The source resource tree and the live class path; {@code null} when running from a jar. */
    private static final Path[] CACHE_DIRS = cacheDirectories();

    /** In-development placeholders keyed by asset name, size and exact colour instance. */
    private static final Map<DevKey, Placeholder> devCache = new ConcurrentHashMap<>();

    /** One HTTP fetch per asset name, shared by every size/colour placeholder. */
    private static final Map<String, CompletableFuture<Path>> fetchTasks = new ConcurrentHashMap<>();

    /** Background fetches for missing icons; daemon so it cannot keep the JVM alive. */
    private static final ExecutorService fetcher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "freja-icon-fetch");
        thread.setDaemon(true);
        return thread;
    });

    /** Identifies one rendered icon variant; colour identity matters for Theme dynamic colours. */
    private record DevKey(String name, int size, Color color) {
        @Override
        public boolean equals(Object object) {
            return object instanceof DevKey other
                    && size == other.size
                    && name.equals(other.name)
                    && color == other.color;
        }

        @Override
        public int hashCode() {
            return (name.hashCode() * 31 + size) * 31 + System.identityHashCode(color);
        }
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

    /**
     * Loads any bundled Material Design Icons SVG by name. Accepts either {@code "mdi:<name>"} or
     * just {@code "<name>"}.
     * <p>
     * If the SVG is not bundled, the behaviour depends on the mode:
     * </p>
     * <ul>
     * <li><b>Packaged application:</b> returns a neutral fallback icon. No network call is made,
     * even when the working directory happens to be the project root.</li>
     * <li><b>Development:</b> when running from an unpackaged class path that contains a
     * {@code src/main/resources} tree, the icon is fetched once from Iconify, saved both into that
     * tree (for the next build) and into the live class path (for this run), then used. The fetch
     * runs on a background thread so the EDT is not frozen; a placeholder is shown until it
     * completes. Invalid names or failed downloads also return the fallback.</li>
     * </ul>
     * Set {@code -Dfreja.icons.download=false} to force offline behaviour and
     * {@code -Dfreja.icons.endpoint=...} to override the Iconify base URL.
     *
     * @param iconifyName the icon name, with or without an {@code mdi:} prefix
     * @param size the display size in pixels
     * @param color the tint colour (may be a {@link Theme} dynamic colour)
     * @return the loaded icon
     */
    public static Icon of(String iconifyName, int size, Color color) {
        if (iconifyName == null) {
            return fallback(size, color);
        }
        String name;
        if (iconifyName.startsWith("mdi:")) {
            name = iconifyName.substring(4);
        } else {
            name = iconifyName;
        }
        if (!validName(name)) {
            return fallback(size, color);
        }
        if (bundled(name)) {
            return svg(name, size, color);
        }
        if (!devMode()) {
            return fallback(size, color);
        }

        // A file from an earlier fetch (or a previous run) can be used directly. Do not read it
        // while a fetch is still writing it.
        Path cachedFile = cachedFile(name);
        if (cachedFile != null && !fetchTasks.containsKey(name)) {
            return svg(cachedFile.toFile(), size, color);
        }

        DevKey key = new DevKey(name, size, color);
        Placeholder cached = devCache.get(key);
        if (cached != null) {
            return cached;
        }
        Placeholder placeholder = new Placeholder(size);
        Placeholder existing = devCache.putIfAbsent(key, placeholder);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Path> task = fetchTasks.computeIfAbsent(name,
                missing -> CompletableFuture.supplyAsync(() -> fetchToCache(missing), fetcher));
        task.whenComplete((path, error) -> {
            if (path != null) {
                placeholder.setDelegate(svg(path.toFile(), size, color));
            } else {
                placeholder.setDelegate(fallback(size, color));
            }
        });
        return placeholder;
    }

    /**
     * An icon for a navigation row, sized from the shared {@code ARX.navIconSize} UI default. All
     * navigation lists use the same value, so their icons and text columns stay in step.
     *
     * @param iconifyName the icon name, with or without an {@code mdi:} prefix
     * @param color the tint colour
     * @return the icon at the shared navigation size
     */
    public static Icon nav(String iconifyName, Color color) {
        String name = iconifyName == null ? "" : iconifyName;
        return of(name.startsWith("mdi:") ? name : "mdi:" + name, navSize(), color);
    }

    /** A glyph icon at the shared navigation size, for captions which read better as a letter. */
    public static Icon navLetter(String text, Color color) {
        return letter(text, navSize(), color);
    }

    private static int navSize() {
        Object value = UIManager.get("ARX.navIconSize");
        return value instanceof Integer size ? size : UIScale.scale(15);
    }

    /** Returns the first cached SVG for the asset, or null when none exists yet. */
    private static Path cachedFile(String name) {
        if (CACHE_DIRS == null) {
            return null;
        }
        for (Path dir : CACHE_DIRS) {
            Path candidate = dir.resolve(name + ".svg");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static FlatSVGIcon svg(String name, int size, Color color) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/mdi/" + name + ".svg", size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> color));
        return icon;
    }

    private static Icon svg(File file, int size, Color color) {
        try {
            FlatSVGIcon icon = new FlatSVGIcon(file).derive(size, size);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(ignored -> color));
            return icon;
        } catch (Exception e) {
            return fallback(size, color);
        }
    }

    private static boolean bundled(String name) {
        return Icons.class.getResource("/icons/mdi/" + name + ".svg") != null;
    }

    private static boolean validName(String name) {
        return name != null && !name.isEmpty() && name.matches("[a-z0-9-]+");
    }

    private static boolean devMode() {
        return !"false".equals(System.getProperty("freja.icons.download")) && CACHE_DIRS != null;
    }

    /**
     * Locates the directories in which fetched icons are cached. Walking up from the code source
     * (instead of using the working directory) keeps the feature working from any launch location
     * while making sure a jar never starts downloading icons.
     *
     * @return the source resource directory followed by the live class path directory, or
     *         {@code null} when running from a packaged jar
     */
    private static Path[] cacheDirectories() {
        Path[] directories = resolvedCacheDirectories();
        if (directories != null) {
            return directories;
        }
        // An explicit opt-in still works when the class path cannot be resolved to a source tree.
        if ("true".equals(System.getProperty("freja.icons.download"))) {
            Path source = Path.of("src/main/resources/icons/mdi").toAbsolutePath().normalize();
            if (Files.isDirectory(source)) {
                return new Path[]{source};
            }
        }
        return null;
    }

    private static Path[] resolvedCacheDirectories() {
        CodeSource source = Icons.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return null;
        }
        URL location = source.getLocation();
        if (!"file".equalsIgnoreCase(location.getProtocol())) {
            return null;
        }
        Path runtime;
        try {
            runtime = Path.of(location.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
            return null;
        }
        if (Files.isRegularFile(runtime)) {
            return null; // a jar on disk
        }
        Path sourceDir = null;
        for (Path dir = runtime; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources/icons/mdi");
            if (Files.isDirectory(candidate)) {
                sourceDir = candidate;
                break;
            }
        }
        if (sourceDir == null) {
            return null;
        }
        Path runtimeDir = runtime.resolve("icons/mdi");
        return sourceDir.equals(runtimeDir) ? new Path[]{sourceDir} : new Path[]{sourceDir, runtimeDir};
    }

    private static String endpoint() {
        String custom = System.getProperty("freja.icons.endpoint");
        return custom != null ? custom : ICONIFY;
    }

    /** Downloads one SVG and writes it into every cache directory, returning the first copy. */
    private static Path fetchToCache(String name) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint() + name + ".svg"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            String body = new String(response.body(), StandardCharsets.UTF_8);
            if (!body.stripLeading().startsWith("<svg")) {
                throw new IllegalStateException("Response is not an SVG document");
            }
            // Keep the cached files identical in shape to the bundled ones: the size comes from
            // the caller, not from a width/height attribute in the file.
            String svg = Pattern.compile("<svg[^>]*>").matcher(body)
                    .replaceFirst(tag -> tag.group().replaceAll("\\s+(?:width|height)=\"[^\"]*\"", ""));
            byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);

            Path first = null;
            for (Path dir : CACHE_DIRS) {
                try {
                    Files.createDirectories(dir);
                    Path target = dir.resolve(name + ".svg");
                    writeAtomically(target, bytes);
                    if (first == null) {
                        first = target;
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Cannot cache icon ''" + name + "'' in " + dir, e);
                }
            }
            if (first == null) {
                throw new IllegalStateException("No cache directory accepted the icon");
            }
            LOG.log(Level.INFO, "Fetched icon ''{0}'' from Iconify and cached it in {1}",
                    new Object[]{name, first});
            return first;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Cannot fetch icon ''" + name + "'' from Iconify", e);
            return null;
        }
    }

    /** Replaces the target only after the complete file has been written. */
    private static void writeAtomically(Path target, byte[] bytes) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), "icon-", ".svg.tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Icon fallback(int size, Color color) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D gg = (Graphics2D) g.create();
                try {
                    gg.setColor(Color.LIGHT_GRAY);
                    gg.fillRect(x, y, size, size);
                    gg.setColor(Color.GRAY);
                    gg.drawRect(x, y, size - 1, size - 1);
                } finally {
                    gg.dispose();
                }
            }
        };
    }

    public static Icon plus(int size, Color color) {
        return of("mdi:plus", size, color);
    }

    public static Icon close(int size, Color color) {
        return of("mdi:close", size, color);
    }

    /** A trash can, used for operations which remove files from disk. */
    public static Icon trash(int size, Color color) {
        return of("mdi:trash-can", size, color);
    }

    public static Icon star(int size, Color color, boolean filled) {
        return of(filled ? "mdi:star" : "mdi:star-outline", size, color);
    }

    public static Icon folder(int size, Color color) {
        return of("mdi:folder", size, color);
    }

    public static Icon tag(int size, Color color) {
        return of("mdi:tag", size, color);
    }

    /**
     * A glyph icon painted with the configured application font, for the cases where a letter reads
     * better than an outline: the font row shows an "A" in the very typeface it configures.
     */
    public static Icon letter(String text, int size, Color color) {
        // FlatSVGIcon scales its reported size with the display scale. Do the same here, otherwise a
        // glyph beside an SVG ends up smaller and shifts the text of a navigation row to the left.
        int scaled = UIScale.scale(size);
        return new Icon() {
            @Override
            public int getIconWidth() {
                return scaled;
            }

            @Override
            public int getIconHeight() {
                return scaled;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D gg = (Graphics2D) g.create();
                try {
                    gg.translate(x, y);
                    gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    Font font = new Font(freja.ui.Theme.fontFamily(), Font.BOLD, Math.round(scaled * 1.1f));
                    gg.setFont(font);
                    gg.setColor(color);
                    FontMetrics metrics = gg.getFontMetrics();
                    int tx = (scaled - metrics.stringWidth(text)) / 2;
                    int ty = (scaled - metrics.getHeight()) / 2 + metrics.getAscent();
                    gg.drawString(text, tx, ty);
                } finally {
                    gg.dispose();
                }
            }
        };
    }

    /** The theme glyph: an outlined circle with its right half filled, like the contrast symbol. */
    public static Icon contrast(int size, Color color) {
        return of("mdi:contrast-circle", size, color);
    }

    public static Icon settings(int size, Color color) {
        return of("mdi:cog", size, color);
    }

    /** An arrow leaving a box: write the whole user profile to a file. */
    public static Icon exportSettings(int size, Color color) {
        return of("mdi:export", size, color);
    }

    /** An arrow entering a box: restore the whole user profile from a file. */
    public static Icon importSettings(int size, Color color) {
        return of("mdi:import", size, color);
    }

    public static Icon grid(int size, Color color) {
        return of("mdi:view-grid", size, color);
    }

    public static Icon clock(int size, Color color) {
        return of("mdi:clock", size, color);
    }

    public static Icon image(int size, Color color) {
        return of("mdi:image", size, color);
    }

    public static Icon search(int size, Color color) {
        return of("mdi:magnify", size, color);
    }

    public static Icon refresh(int size, Color color) {
        return of("mdi:refresh", size, color);
    }

    public static Icon play(int size, Color color) {
        return of("mdi:play", size, color);
    }

    public static Icon sun(int size, Color color) {
        return of("mdi:weather-sunny", size, color);
    }

    public static Icon moon(int size, Color color) {
        return of("mdi:weather-night", size, color);
    }

    public static Icon pause(int size, Color color) {
        return of("mdi:pause", size, color);
    }

    public static Icon chevron(int size, Color color, boolean left) {
        return of(left ? "mdi:chevron-left" : "mdi:chevron-right", size, color);
    }

    public static Icon check(int size, Color color) {
        return of("mdi:check", size, color);
    }

    /** A clipboard page with a clip at the top, used for copying a file path. */
    public static Icon clipboard(int size, Color color) {
        return of("mdi:content-copy", size, color);
    }

    /** A pencil with an eraser, used for renaming. */
    public static Icon pencil(int size, Color color) {
        return of("mdi:pencil", size, color);
    }

    /** An "A -> Z" glyph for sorting by name. */
    public static Icon sortName(int size, Color color) {
        return of("mdi:sort-alphabetical-ascending", size, color);
    }

    /** A clock with an upward arrow: newest photos first. */
    public static Icon sortNewestFirst(int size, Color color) {
        return of("mdi:sort-calendar-descending", size, color);
    }

    /** A clock with a downward arrow: oldest photos first. */
    public static Icon sortOldestFirst(int size, Color color) {
        return of("mdi:sort-calendar-ascending", size, color);
    }

    /** Three vertical bars of increasing height for sorting by size. */
    public static Icon sortBySize(int size, Color color) {
        return of("mdi:sort-numeric-ascending", size, color);
    }

    /** Two opposed arrows, used as the heading icon for sort-order submenus. */
    public static Icon sortOrder(int size, Color color) {
        return of("mdi:sort", size, color);
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

    /** A temporary icon shown while a missing dev icon is being fetched in the background. */
    private static final class Placeholder implements Icon {

        private final int size;

        private volatile Icon delegate;

        private final Map<Component, Boolean> components = Collections.synchronizedMap(new WeakHashMap<>());

        Placeholder(int size) {
            this.size = size;
        }

        void setDelegate(Icon delegate) {
            this.delegate = delegate;
            SwingUtilities.invokeLater(() -> {
                synchronized (components) {
                    for (Component component : components.keySet()) {
                        if (component != null) {
                            component.repaint();
                        }
                    }
                    components.clear();
                }
            });
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
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Icon icon = delegate;
            if (icon != null) {
                icon.paintIcon(c, g, x, y);
                return;
            }
            if (c != null) {
                components.put(c, Boolean.TRUE);
            }
            Graphics2D gg = (Graphics2D) g.create();
            try {
                gg.setColor(Color.LIGHT_GRAY);
                gg.fillRect(x, y, size, size);
                gg.setColor(Color.GRAY);
                gg.drawRect(x, y, size - 1, size - 1);
            } finally {
                gg.dispose();
            }
        }
    }
}
