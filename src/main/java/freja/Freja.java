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

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import freja.scan.LibraryScanner;
import freja.store.Library;
import freja.ui.MainWindow;
import freja.ui.Theme;

/**
 * The application entry point.
 */
public final class Freja {

    /** The product version. */
    public static final String VERSION = "1.0";

    private Freja() {
    }

    /**
     * Starts the desktop application.
     *
     * @param args optional photo folders, plus {@code --library=<path>}
     */
    public static void main(String[] args) {
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

        Path location = database == null ? defaultDatabase() : database;
        Theme.install();

        SwingUtilities.invokeLater(() -> {
            Library library = null;
            LibraryScanner scanner = null;
            try {
                library = new Library(location);
                scanner = new LibraryScanner(library);
                MainWindow window = new MainWindow(library, scanner);
                window.setVisible(true);
                window.start();
                if (!folders.isEmpty()) {
                    window.addFolders(folders);
                }
            } catch (Exception e) {
                if (scanner != null) {
                    scanner.close();
                }
                if (library != null) {
                    library.close();
                }
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Freja を起動できません。\n\n" + e
                        .getMessage() + "\n\nライブラリ: " + location, "Freja", JOptionPane.ERROR_MESSAGE);
            }
        });
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
