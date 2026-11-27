/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja;

import java.awt.EventQueue;
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

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import freja.media.scan.LibraryScanner;
import freja.media.store.Library;
import laevatain.util.Language;

/**
 * The application entry point.
 */
public final class Freja {

    /** The product version. */
    public static final String VERSION = "1.0";

    /** Loopback port used to detect an already running instance. */
    private static final int INSTANCE_PORT = 45671;

    private static volatile ServerSocket instanceSocket;

    private static volatile MainWindow window;

    private static String[] launchArgs = new String[0];

    private Freja() {
    }

    /** Binds the instance port and watches for a newer process asking this one to exit. */
    private static boolean claimInstance() {
        try {
            instanceSocket = new ServerSocket(INSTANCE_PORT, 1, InetAddress.getLoopbackAddress());
            Thread watcher = new Thread(Freja::watchForNewerInstance, "freja-single-instance");
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
        SwingUtilities.invokeLater(() -> {
            MainWindow current = window;
            if (current != null) {
                current.requestShutdown();
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

    /** Restarts the application with the same arguments and JVM options. */
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
            command.add(Freja.class.getName());
            command.addAll(Arrays.asList(launchArgs));
            new ProcessBuilder(command).start();
            if (window != null) {
                SwingUtilities.invokeLater(window::requestShutdown);
            } else {
                System.exit(0);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(window, "再起動できませんでした: " + e.getMessage(), "再起動", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Starts the desktop application.
     *
     * @param args optional photo folders, plus {@code --library=<path>}
     */
    public static void main(String[] args) {
        launchArgs = args.clone();
        Path database = null;
        List<Path> folders = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument.equals("--help") || argument.equals("-h")) {
                System.out.println("""
                        Freja %s - Picasa ライクなローカル写真ライブラリ

                        使い方:
                          java -jar freja.jar [オプション] [写真フォルダ...]

                        オプション:
                          --library=<path>   ライブラリ (SQLite) の保存先を指定
                          --version          バージョンを表示
                          -h, --help         このヘルプを表示
                        """.formatted(VERSION));
                return;
            }
            if (argument.equals("--version")) {
                System.out.println(VERSION);
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

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Freja はデスクトップ環境が必要です。");
            return;
        }

        if (!claimInstance()) {
            // A newer launch takes over: ask the old process to close, then take its port.
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

        Path location = database == null ? defaultDatabase() : database;
        Language.install();
        Theme.install();

        // Opening SQLite and creating the scanner must never happen on the event dispatch thread:
        // the library now fails fast when SQL is attempted there. Build them on a daemon bootstrap
        // thread and hand only the (SQL-free) UI construction to Swing.
        Thread bootstrap = new Thread(() -> {
            Library library = null;
            LibraryScanner scanner = null;
            try {
                library = new Library(location);
                Library opened = library;
                opened.setUiThreadCheck(EventQueue::isDispatchThread);
                scanner = new LibraryScanner(opened);
                LibraryScanner started = scanner;
                Library toClose = opened;
                LibraryScanner scannerToClose = started;
                SwingUtilities.invokeLater(() -> {
                    try {
                        window = new MainWindow(opened, started);
                        window.setVisible(true);
                        window.start();
                        if (!folders.isEmpty()) {
                            window.addFolders(folders);
                        }
                    } catch (Exception e) {
                        // library.close() itself runs SQL, so the cleanup must stay off the EDT.
                        Thread cleanup = new Thread(() -> {
                            if (scannerToClose != null) {
                                scannerToClose.close();
                            }
                            if (toClose != null) {
                                toClose.close();
                            }
                        }, "freja-startup-cleanup");
                        cleanup.setDaemon(true);
                        cleanup.start();
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null, "Freja を起動できません。\n\n" + e
                                .getMessage() + "\n\nライブラリ: " + location, "Freja", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                if (scanner != null) {
                    scanner.close();
                }
                if (library != null) {
                    library.close();
                }
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Freja を起動できません。\n\n" + e
                        .getMessage() + "\n\nライブラリ: " + location, "Freja", JOptionPane.ERROR_MESSAGE));
            }
        }, "freja-startup");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    /**
     * Returns the platform's default database location.
     *
     * @return the SQLite file path
     */
    public static Path defaultDatabase() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "Freja", "library.db");
        }
        return Path.of(System.getProperty("user.home"), ".freja", "library.db");
    }
}
