/*
 * Copyright (C) 2026 The ARX Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

import kiss.Disposable;
import kiss.I;
import kiss.Variable;

/**
 * The localisation bridge for ARX components.
 * <p>
 * Interface text is written in the source as plain English and passed through {@link I#translate},
 * which resolves it against the language held in {@link I#Lang}: a sentence seen for the first time
 * is translated online once and stored in a local bundle, later requests come from that cache. The
 * result is a {@link Variable}, so widgets bound here follow a language change immediately and
 * without restarting the application.
 * <p>
 * Values are inserted with {@link String#format} placeholders ({@code %d}, {@code %s}) via
 * {@link #dynamic}: mustache braces cannot be used, because the translation step substitutes those
 * away before the text reaches the caller.
 */
public final class ARXLang {

    /** A language the interface can be shown in. */
    public record Language(String code, String name) {
    }

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(ARXLang.class);

    private static final String LANGUAGE_KEY = "language";

    /**
     * The offered languages. Their names are written in their own language, the way every operating
     * system lists them, so a bad translation can never hide the option the user is looking for.
     * The first entry is the language the source sentences are written in.
     */
    public static final List<Language> LANGUAGES = List.of(
            new Language("en", "English"),
            new Language("ja", "日本語"),
            new Language("ko", "한국어"),
            new Language("zh-CN", "简体中文"),
            new Language("zh-TW", "繁體中文"),
            new Language("de", "Deutsch"),
            new Language("fr", "Français"),
            new Language("es", "Español"),
            new Language("it", "Italiano"),
            new Language("pt", "Português"),
            new Language("ru", "Русский"),
            new Language("nl", "Nederlands"));

    /**
     * Machine translation is faithful but not idiomatic, so sentences whose wording matters are
     * pinned per language here instead of being left to the service.
     */
    private static final Map<String, Map<String, String>> GLOSSARY = Map.of("ja", Map.ofEntries(
            // Navigation and section titles.
            Map.entry("Library", "ライブラリ"),
            Map.entry("Folders", "フォルダ"),
            Map.entry("All photos", "すべての写真"),
            Map.entry("Favorites", "お気に入り"),
            Map.entry("Recent photos", "最近の写真"),
            Map.entry("No folders are registered", "登録されたフォルダはありません"),
            Map.entry("Settings", "設定"),
            Map.entry("Appearance", "外観"),
            Map.entry("Fonts", "フォント"),
            Map.entry("Language", "言語"),
            Map.entry("System", "システム"),
            // Folder context menu.
            Map.entry("Sort order", "表示順"),
            Map.entry("By name", "名前順"),
            Map.entry("Newest first", "日時順 (新しい順)"),
            Map.entry("Oldest first", "日時順 (古い順)"),
            Map.entry("By size", "サイズ順"),
            Map.entry("Rename", "名前を変更"),
            Map.entry("Edit tags…", "タグを編集…"),
            Map.entry("Rescan", "再スキャン"),
            Map.entry("Open in Explorer", "エクスプローラーで開く"),
            Map.entry("Remove from library", "ライブラリから削除"),
            Map.entry("Tags", "タグ"),
            Map.entry("Manage tags", "タグを管理"),
            Map.entry("Manage watched folders", "監視フォルダを管理"),
            Map.entry("(auto-detected)", "(自動検出)"),
            // Appearance page.
            Map.entry("Light", "ライト"),
            Map.entry("Dark", "ダーク"),
            Map.entry("All themes", "すべてのテーマ"),
            Map.entry("Light themes", "ライト系"),
            Map.entry("Dark themes", "ダーク系"),
            Map.entry("More themes", "さらに見る"),
            Map.entry("Fewer themes", "代表のみ表示"),
            Map.entry("Filter", "絞り込み"),
            Map.entry("Choose how the application is coloured.", "アプリの配色を選択します。"),
            Map.entry("Show the representative palettes only", "代表的な配色に絞って表示します"),
            Map.entry("Narrow the candidates by brightness", "明るさに応じて候補を絞り込みます"),
            // Fonts page.
            Map.entry("Adjust the typeface and its base size.", "文字の書体と大きさを調整します。"),
            Map.entry("Font family", "フォント ファミリー"),
            Map.entry("Typeface to use", "使用する書体"),
            Map.entry("Font size", "フォント サイズ"),
            Map.entry("Base size in points", "文字の基準サイズ（ポイント）"),
            Map.entry("Preview", "プレビュー"),
            Map.entry("How the current settings look", "現在の設定の確認結果"),
            // Language page.
            Map.entry("Pick the language of the interface.", "表示言語を選択します。"),
            Map.entry("Display language", "表示言語"),
            Map.entry("Interface text is written in English and translated automatically; "
                    + "results are cached for offline use",
                    "表示文は英語で記述され、自動的に翻訳されます。翻訳結果はオフライン用に保存されます"),
            Map.entry("Current locale", "現在のロケール"),
            Map.entry("Used for dates, numbers and sorting", "日付・数値・並び順に使用されます"),
            // System page.
            Map.entry("Update checks, application information and restart.",
                    "アップデート確認とアプリの情報、再起動を管理します。"),
            Map.entry("Check for updates at startup", "起動時にアップデートを確認する"),
            Map.entry("Look for a newer version when Freja starts", "起動時に新しいバージョンを確認します"),
            Map.entry("Check for updates", "アップデートの確認"),
            Map.entry("Compare against the latest release", "最新バージョンを確認します"),
            Map.entry("Restart the application", "アプリケーションを再起動"),
            Map.entry("Apply the settings and start again", "設定を反映してアプリを再起動します"),
            Map.entry("Restart", "再起動する"),
            Map.entry("Check now", "アップデートを確認"),
            Map.entry("The update check is not available yet.", "アップデート確認機能は現在準備中です。"),
            Map.entry("Update", "アップデート"),
            Map.entry("On", "オン"),
            Map.entry("Off", "オフ"),
            // Information rows: the service turns "Application" into 応用, which is not the word.
            Map.entry("Current theme", "現在のテーマ"),
            Map.entry("Application", "アプリケーション"),
            Map.entry("Operating system", "オペレーティングシステム"),
            Map.entry("Java runtime", "Javaランタイム")));

    /** Listeners rebuilt text can call when the language changes. */
    private static final List<Runnable> listeners = new ArrayList<>();

    private ARXLang() {
    }

    /**
     * Pins where sinobu keeps its translation bundles. sinobu bakes its default - a {@code lang}
     * folder relative to the working directory - into the environment cache while {@code kiss.I} is
     * initializing, so handing it a default here would arrive too late; the resolved value is
     * written into that cache directly instead. An explicit {@code LangDirectory} environment
     * variable always wins.
     *
     * @param directory the folder which will hold {@code <language>.json}
     */
    public static void bundleDirectory(java.nio.file.Path directory) {
        if (directory == null || System.getenv("LangDirectory") != null) {
            return;
        }
        try {
            java.lang.reflect.Field cache = I.class.getDeclaredField("env");
            cache.setAccessible(true);
            ((java.util.Properties) cache.get(null)).setProperty("LangDirectory",
                    directory.toAbsolutePath().toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A future sinobu layout may not expose the cache; the relative folder still works.
        }
    }

    /**
     * The folder currently holding the translation bundles. A plain read: sinobu's two-argument
     * lookup stores its default when the value is missing, so asking this first would lock the
     * cache to the relative folder and make {@link #bundleDirectory(java.nio.file.Path)} too late.
     */
    public static java.nio.file.Path bundleDirectory() {
        String value = I.env("LangDirectory");
        return java.nio.file.Path.of(value == null ? "lang" : value);
    }

    /**
     * Applies the saved language. Without a saved choice the operating system's language stands,
     * which is what {@link I#Lang} already holds.
     */
    public static void install() {
        String code = PREFERENCES.get(LANGUAGE_KEY, null);
        if (code != null && !code.isBlank()) {
            I.Lang.set(code);
        }
    }

    /** The active language code, in the form the translation service expects. */
    public static String language() {
        String code = I.Lang.get();
        if (code == null || code.isBlank()) {
            return Locale.getDefault().getLanguage();
        }
        return code;
    }

    /** The locale matching the active language, for dates, numbers and collation. */
    public static Locale locale() {
        Locale locale = Locale.forLanguageTag(language());
        return locale.getLanguage().isEmpty() ? Locale.getDefault() : locale;
    }

    /** The active locale in its own words, e.g. 日本語 (日本) or English (United States). */
    public static String displayName() {
        Locale locale = locale();
        return locale.getDisplayName(locale);
    }

    /**
     * Switches the interface language, remembers the choice and notifies the listeners. Widgets
     * bound through {@link #bind} update themselves as the underlying variable emits.
     */
    public static void setLanguage(String code) {
        if (code == null || code.equals(language())) {
            return;
        }
        PREFERENCES.put(LANGUAGE_KEY, code);
        I.Lang.set(code);
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    /**
     * Registers a callback for language changes, for text assembled from values which cannot be
     * bound as a variable, such as a popup menu built on demand.
     */
    public static void onChange(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** The display name of a language code, in its own language. */
    public static String name(String code) {
        for (Language language : LANGUAGES) {
            if (language.code().equals(code)) {
                return language.name();
            }
        }
        return code;
    }

    /**
     * A sentence in the current language, kept up to date. A glossary entry wins outright; anything
     * not pinned goes to the translation service, and a late answer from a previous language is
     * discarded so it cannot overwrite the new one.
     */
    public static Variable<String> text(String sentence) {
        Variable<String> out = Variable.empty();
        Disposable[] pending = new Disposable[1];
        I.Lang.observing().to(code -> {
            if (pending[0] != null) {
                pending[0].dispose();
                pending[0] = null;
            }
            String term = glossary(code).get(sentence);
            if (term != null) {
                out.set(term);
            } else {
                pending[0] = I.translate(sentence).observing().to(out::set);
            }
        });
        return out;
    }

    /**
     * The wording available right now: the glossary entry when there is one, otherwise the cached
     * translation, or the English source for a sentence never fetched.
     */
    public static String of(String sentence) {
        String term = glossary(language()).get(sentence);
        return term != null ? term : I.translate(sentence).get();
    }

    private static Map<String, String> glossary(String code) {
        Map<String, String> terms = GLOSSARY.get(code);
        return terms == null ? Map.of() : terms;
    }

    /**
     * A sentence whose {@code %d} / {@code %s} placeholders are filled after translation, so the
     * wording can move in every language while the values stay live.
     */
    public static Variable<String> dynamic(String sentence, Object... arguments) {
        return text(sentence).map(v -> String.format(locale(), v, arguments));
    }

    /**
     * Feeds a sentence into a setter now and on every later change. The returned subscription can
     * be ignored for widgets that live as long as the window, or disposed with an owner.
     */
    public static Disposable on(String sentence, Consumer<String> setter) {
        return text(sentence).observing().to(setter::accept);
    }

    /** Creates a label whose text follows the language. */
    public static JLabel label(String sentence) {
        return bind(new JLabel(), sentence);
    }

    /** Binds a sentence to a button's text and returns the button. */
    public static <T extends AbstractButton> T bind(T button, String sentence) {
        on(sentence, button::setText);
        return button;
    }

    /** Binds a sentence to a label's text and returns it. */
    public static <T extends JLabel> T bind(T label, String sentence) {
        on(sentence, label::setText);
        return label;
    }

    /** Binds a sentence to a component's tooltip and returns the component. */
    public static <T extends JComponent> T tooltip(T component, String sentence) {
        on(sentence, component::setToolTipText);
        return component;
    }

    /** Formats a value with the active locale, so numbers and dates follow the language too. */
    public static String format(String pattern, Object... arguments) {
        return String.format(locale(), pattern, arguments);
    }
}
