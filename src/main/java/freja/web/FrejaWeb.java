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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import apocrypha.Application;
import apocrypha.ApplicationConfig;
import apocrypha.Window;
import freja.model.PhotoFilter.SortOrder;
import freja.scan.LibraryScanner;
import freja.store.Library;
import kiss.I;
import laevatain.util.Language;

/**
 * The browser edition entry point. Freja ships two user interfaces:
 * {@code freja.Freja} is the Swing edition and this one is the Apocrypha counterpart.
 * <p>
 * The UI is built with the browser: HTML/CSS/JS live in the classpath directory {@code web}, and
 * every cross-boundary call crosses the {@code freja:*} IPC channels. Thumbnails and photo
 * originals are served by the {@code /photo/} route so binary content is delivered with proper
 * content types and cache headers, instead of base64 through IPC. The catalog is opened in the
 * background before the window shows, exactly like the other editions; the renderer only starts
 * browsing once the {@code freja:ready} bootstrap event arrives.
 * </p>
 */
public class FrejaWeb extends Application {

    /** The product version. */
    public static final String VERSION = "1.0";

    /** The name reported by the renderer. */
    public static final String NAME = "Freja";

    /** Loopback port used to detect an already running process, shared with the other editions. */
    private static final int INSTANCE_PORT = 45671;

    /** How long a scan is waited on before the directory is skipped. */
    private static final int DIRECTORY_TIMEOUT_SECONDS = 10;

    /** The active instance, used by the single-instance watcher. */
    private static volatile FrejaWeb current;

    /** @return the running application, or null before it is ready */
    public static FrejaWeb current() {
        return current;
    }

    private final AtomicReference<Library> library = new AtomicReference<>();

    private final AtomicReference<LibraryScanner> scanner = new AtomicReference<>();

    private final AtomicReference<WebHandlers> handlers = new AtomicReference<>();

    private final AtomicReference<WebSettings> settings = new AtomicReference<>();

    private final AtomicReference<Window> window = new AtomicReference<>();

    private final AtomicBoolean stopped = new AtomicBoolean();

    /** The folders passed on the command line, added after the catalog opens. */
    private final List<Path> startupFolders = new ArrayList<>();

    /**
     * The entry point. Starts the desktop application, handing the catalog, the scanner and the
     * browser window over to the UI thread.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        Language.install();
        List<Path> folders = new ArrayList<>();
        for (String arg : args) {
            if (arg != null && !arg.startsWith("-")) {
                folders.add(Path.of(arg));
            }
        }
        if (!claimInstance(folders)) {
            retireExistingInstance();
            waitForInstance();
        }
        Application.launch(FrejaWeb.class, args);
    }

    /** Binds the instance port and remembers the command line folders. */
    private static boolean claimInstance(List<Path> folders) {
        try {
            ServerSocket socket = new ServerSocket(INSTANCE_PORT, 1, InetAddress.getLoopbackAddress());
            socket.setSoTimeout(0);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Asks the instance holding the port to exit so this one can take over. */
    private static void retireExistingInstance() {
        try (Socket request = new Socket()) {
            request.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), INSTANCE_PORT), 1000);
            request.getOutputStream().write('Q');
            request.getOutputStream().flush();
        } catch (IOException e) {
            I.error("freja", new IOException("Failed to retire the previous process", e));
        }
    }

    /** Waits until the old process has released the port. */
    private static void waitForInstance() {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (isPortFree()) {
                return;
            }
        }
    }

    private static boolean isPortFree() {
        try (ServerSocket probe = new ServerSocket(INSTANCE_PORT, 1, InetAddress.getLoopbackAddress())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Configures the application before the webview starts.
     *
     * @param config The mutable configuration.
     */
    @Override
    protected void configure(ApplicationConfig config) {
        config.name(NAME).version(VERSION).resourceRoot("web").entry("index.html").spaFallback(true).devTools(true);
    }

    /**
     * Starts the application. The catalog opens in the background; the IPC handlers and the
     * {@code /photo/} routes are registered up front so they can fetch the library lazily, and the
     * window shows immediately.
     */
    @Override
    protected void onStart() {
        current = this;
        WebSettings opened = new WebSettings(dataDirectory());
        settings.set(opened);

        Thread opener = new Thread(this::openLibrary, "freja-web-open");
        opener.setDaemon(true);
        opener.start();

        WebHandlers registered = new WebHandlers(this);
        handlers.set(registered);
        ipc().register(registered);
        serve("/photo/", new PhotoRoutes(this));

        Path frontend = SvelteUI.bundle(dataDirectory());
        if (frontend != null) {
            serve(SvelteUI.PREFIX, SvelteUI.route(frontend));
        }

        Window frame = createWindow();
        window.set(frame);
        frame.id("main").size(1180, 780).show();

        I.info("freja", "Browser edition started");
    }

    /**
     * Opens the catalog and the scanner off the UI thread, then attaches them and publishes the
     * bootstrap event.
     */
    private void openLibrary() {
        try {
            Path location = defaultDatabase();
            Library opened = new Library(location);
            LibraryScanner started = new LibraryScanner(opened);
            boolean scanOnStartup = settings.get().bool("scanOnStartup");
            boolean sweep = settings.get().bool("sweep");
            started.start(true, scanOnStartup, sweep);
            library.set(opened);
            scanner.set(started);
            attach(started);
        } catch (java.sql.SQLException | RuntimeException e) {
            I.error("freja", new IOException("Cannot open the library", e));
            SwingUtilities.invokeLater(() -> ipc()
                    .emit("freja:fatal", new WebModels.ScannerReport(false, false, 0, 0, 1, 0, "ライブラリを読み込めません: " + e.getMessage(), 0)));
        }
    }

    /**
     * Wires the scanner into the application and hands the bootstrap to the browser. Called once
     * after the catalog opens; the command-line folders are registered here so the renderer can
     * resume browsing from them.
     */
    private void attach(LibraryScanner started) {
        for (String arg : arguments()) {
            if (arg.startsWith("--library=")) {
                startupFolders.add(Path.of(arg.substring("--library=".length())));
            }
        }
        started.addListener(new LibraryScanner.Listener() {
            @Override
            public void onStateChanged(LibraryScanner.State state) {
                publishState(state);
            }

            @Override
            public void onLibraryChanged() {
                publishChanged();
            }
        });
        refreshBootstrap();
    }

    /** Publishes a scanner report to the status bar. */
    private void publishState(LibraryScanner.State state) {
        if (window.get() != null) {
            window.get().emit("freja:state", report(state));
        }
    }

    /** Publishes a change notification to the browser. */
    private void publishChanged() {
        refreshBootstrap();
    }

    /**
     * Converts a scanner state into a report for the status bar.
     *
     * @param state the scanner state, or null
     * @return a report, or null
     */
    public static WebModels.ScannerReport report(LibraryScanner.State state) {
        if (state == null) {
            return null;
        }
        return new WebModels.ScannerReport(state.scanning(), state.watching(), state.processed(), state.total(), state.errors(), state
                .imported(), state.message(), state.elapsed().toMillis());
    }

    /**
     * Rebuilds and publishes the bootstrap event. Called by the handlers after a mutation so the
     * renderer can refresh its view.
     */
    public void refreshBootstrap() {
        WebHandlers registered = handlers.get();
        if (registered != null) {
            registered.invalidate();
        }
        if (window.get() != null) {
            window.get().emit("freja:ready", bootstrap());
        }
    }

    /**
     * Assembles the bootstrap event.
     *
     * @return a bootstrap payload, or null before the catalog opens
     */
    public WebModels.Bootstrap bootstrap() {
        Library opened = library.get();
        if (opened == null) {
            return null;
        }
        return new WebModels.Bootstrap(settings.get().map(), opened.location().toString(), dataDirectory().toString(), settings.get()
                .string("language", "ja"), startupFolders.stream().map(Path::toString).toList(), opened
                        .folders(), opened.tags(), opened.stats(), report(scanner.get() == null ? null : scanner.get().state()));
    }

    /** @return the open catalog, for the handlers which need it */
    public Library library() {
        return library.get();
    }

    /** @return the scanner, or null before it opens */
    public LibraryScanner scanner() {
        return scanner.get();
    }

    /** @return the settings, or null before they load */
    public WebSettings settings() {
        return settings.get();
    }

    /** @return the window, or null before it shows */
    public Window window() {
        return window.get();
    }

    /** @return the folders passed on the command line */
    public List<Path> startupFolders() {
        return List.copyOf(startupFolders);
    }

    /**
     * Applies a changed language to the translation layer and the date formatters.
     *
     * @param code the new language code
     */
    public void applyLanguage(String code) {
        settings.get().replace(Map.of("language", code));
        settings.get().store();
        Language.install(code.isEmpty() ? Language.language() : code);
        refreshBootstrap();
    }

    /**
     * Resolves the navigation mode of the bootstrap settings.
     *
     * @param mode the mode name
     * @return the mode, or ALL for an unknown name
     */
    public SortOrder resolveOrder(String mode) {
        try {
            return SortOrder.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return SortOrder.NAME;
        }
    }

    /**
     * Returns the default catalog path from the {@code freja.library} system property or the
     * per-user database in the local application data directory, shared with the Swing edition.
     *
     * @return the database path
     */
    public static Path defaultDatabase() {
        String property = System.getProperty("freja.library");
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "Freja", "library.db");
        }
        return Path.of(System.getProperty("user.home"), ".freja", "library.db");
    }

    /**
     * Asks the window to close; the close-request hook performs the cleanup.
     */
    public void requestShutdown() {
        if (stopped.compareAndSet(false, true)) {
            quit();
        }
    }

    /**
     * Releases application resources: the catalog and the scanner go with it.
     */
    @Override
    protected void onExit() {
        LibraryScanner current = scanner.getAndSet(null);
        if (current != null) {
            current.close();
        }
        Library opened = library.getAndSet(null);
        if (opened != null) {
            opened.close();
        }
        current = null;
        I.info("freja", "Browser edition stopped");
    }
}
