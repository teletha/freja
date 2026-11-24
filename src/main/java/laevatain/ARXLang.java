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
import java.util.prefs.Preferences;

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
    public static final List<Language> LANGUAGES = List
            .of(new Language("en", "English"), new Language("ja", "日本語"), new Language("ko", "한국어"), new Language("zh-CN", "简体中文"), new Language("zh-TW", "繁體中文"), new Language("de", "Deutsch"), new Language("fr", "Français"), new Language("es", "Español"), new Language("it", "Italiano"), new Language("pt", "Português"), new Language("ru", "Русский"), new Language("nl", "Nederlands"));


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
            ((java.util.Properties) cache.get(null)).setProperty("LangDirectory", directory.toAbsolutePath().toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A future sinobu layout may not expose the cache; the relative folder still works.
        }
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
     * Switches the interface language, remembers the choice and notifies the listeners.
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
     * A sentence in the current language, kept up to date. The source wording is used until a
     * translation is available, so a missing or not-yet-fetched translation can never leave a bound
     * component with empty text. A late answer from a previous language is discarded.
     */
    public static Variable<String> text(String sentence) {
        Variable<String> out = Variable.of(sentence);
        Disposable[] pending = new Disposable[1];
        I.Lang.observing().to(code -> {
            if (pending[0] != null) {
                pending[0].dispose();
                pending[0] = null;
            }
            pending[0] = I.translate(sentence).observing()
                    .to(value -> out.set(value == null || value.isBlank() ? sentence : value));
        });
        return out;
    }

    /**
     * The wording available right now: the cached translation when there is one, otherwise the
     * English source.
     */
    public static String of(String sentence) {
        String term = I.translate(sentence).get();
        return term == null || term.isBlank() ? sentence : term;
    }
}
