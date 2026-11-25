/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.fx;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.application.Platform;
import kiss.I;
import laevatain.util.Language;
import psychopath.Locator;
import viewtify.ActivationPolicy;
import viewtify.Theme;
import viewtify.ThemeType;
import viewtify.Viewtify;

/**
 * The application entry point of the JavaFX edition. It shares the SQLite catalog and the scanner
 * with the Swing edition, and only replaces the user interface, built with Viewtify.
 */
public final class FrejaFX {

    /** Loopback port used to detect an already running instance, shared with the Swing edition. */
    private static final int INSTANCE_PORT = 45671;

    private static volatile ServerSocket instanceSocket;

    private static String[] launchArgs = new String[0];

    /** The database location resolved from the command line, or the platform default. */
    private static volatile Path database = defaultDatabase();

    /** Photo folders passed on the command line, registered right after startup. */
    private static volatile List<Path> startupFolders = List.of();

    /** The last main view, kept for the single-instance watcher and the restart flow. */
    private static volatile MainView main;

    private FrejaFX() {
    }

    /**
     * Starts the JavaFX desktop application.
     *
     * @param args optional photo folders, plus {@code --library=<path>}
     */
    @Deprecated
    public static void main(String[] args) {
        launchArgs = args.clone();
        List<Path> folders = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument.equals("--help") || argument.equals("-h")) {
                System.out.println("""
                        Freja FX %s - Picasa ライクなローカル写真ライブラリ (JavaFX / Viewtify 版)

                        使い方:
                          java -cp ... freja.fx.FrejaFX [オプション] [写真フォルダ...]

                        オプション:
                          --library=<path>   ライブラリ (SQLite) の保存先を指定
                          --version          バージョンを表示
                          -h, --help         このヘルプを表示
                        """.formatted(freja.Freja.VERSION));
                return;
            }
            if (argument.equals("--version")) {
                System.out.println(freja.Freja.VERSION);
                return;
            }
            if (argument.startsWith("--library=")) {
                database = Path.of(argument.substring("--library=".length()));
            } else if (argument.startsWith("-")) {
                System.err.println("不明なオプション: " + argument);
            } else {
                folders.add(Path.of(argument));
            }
        }
        startupFolders = List.copyOf(folders);

        if (GraphicsEnvironment.isHeadless() && !I.env("javafx.headless", false)) {
            System.err.println("Freja にはデスクトップ環境が必要です。");
            return;
        }

        if (!"false".equals(System.getProperty("freja.singleInstance", "true")) && !claimInstance()) {
            // A newer launch takes over: ask the old process (Swing or FX) to close, then take the
            // port.
            retireExistingInstance();
            boolean reclaimed = false;
            for (int attempt = 0; attempt < 30 && !reclaimed; attempt++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                reclaimed = claimInstance();
            }
            if (!reclaimed) {
                System.err.println("既存のインスタンスを終了できませんでした。");
            }
        }

        Language.install();

        // Keep this edition's settings beside the Swing one, not mixed with the main-view defaults.
        if (Viewtify.UserPreference.isAbsent()) {
            Viewtify.UserPreference.set(Locator.directory(".freja-fx"));
        }

        Viewtify.application()
                .title("Freja")
                .version(freja.Freja.VERSION)
                .use(ActivationPolicy.Multiple)
                .use(Theme.Dark)
                .use(ThemeType.Flat)
                .onClosing(() -> {
                    MainView view = main;
                    if (view == null || view.confirmShutdown()) {
                        releaseInstance();
                        return true;
                    }
                    return false;
                })
                .onTerminating(() -> {
                    MainView view = main;
                    if (view != null) {
                        view.cleanup();
                    }
                    releaseInstance();
                })
                .activate(MainView.class);
    }

    /** Called by the main view once it is ready. */
    @Deprecated
    static void installed(MainView view) {
        main = view;
    }

    /** @return the running main view, or null before it is ready */
    @Deprecated
    public static MainView main() {
        return main;
    }

    /** @return the database location to open at startup */
    @Deprecated
    public static Path database() {
        return database;
    }

    /** @return photo folders to register right after startup */
    @Deprecated
    public static List<Path> startupFolders() {
        return startupFolders;
    }

    /** @return the platform's default database location, shared with the Swing edition */
    @Deprecated
    public static Path defaultDatabase() {
        return freja.Freja.defaultDatabase();
    }

    /** Restarts the application with the same arguments and JVM options. */
    @Deprecated
    public static void restart() {
        try {
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            for (String option : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (option.startsWith("--enable-native-access") || option.startsWith("--add-opens") || option
                        .startsWith("--add-exports") || option.startsWith("-D")) {
                    command.add(option);
                }
            }
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(FrejaFX.class.getName());
            command.addAll(Arrays.asList(launchArgs));
            new ProcessBuilder(command).start();
            MainView view = main;
            if (view != null) {
                view.requestShutdown();
            } else {
                System.exit(0);
            }
        } catch (IOException e) {
            System.err.println("再起動できませんでした: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Binds the instance port and watches for a newer process asking this one to exit. */
    private static boolean claimInstance() {
        try {
            instanceSocket = new ServerSocket(INSTANCE_PORT, 1, InetAddress.getLoopbackAddress());
            Thread watcher = new Thread(FrejaFX::watchForNewerInstance, "freja-fx-single-instance");
            watcher.setDaemon(true);
            watcher.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void watchForNewerInstance() {
        try {
            Socket request = instanceSocket.accept();
            request.close();
        } catch (IOException e) {
            return;
        }
        Platform.runLater(() -> {
            MainView view = main;
            if (view != null) {
                view.requestShutdown();
            } else {
                System.exit(0);
            }
        });
    }

    /** Asks the instance holding the port to shut down so this one can take over. */
    private static void retireExistingInstance() {
        try (Socket request = new Socket()) {
            request.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), INSTANCE_PORT), 500);
            request.getOutputStream().write('Q');
            request.getOutputStream().flush();
        } catch (IOException ignored) {
            // The port may belong to an unrelated process; starting anyway is the safe fallback.
        }
    }

    /** Gives up the single-instance port. */
    private static void releaseInstance() {
        ServerSocket socket = instanceSocket;
        instanceSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
