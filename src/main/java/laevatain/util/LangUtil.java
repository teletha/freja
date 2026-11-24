/*
 * Copyright (C) 2026 The ARX Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package laevatain.util;

import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import kiss.I;

/**
 * The localisation bridge for ARX components.
 * <p>
 * Interface text is written in the source as plain English and passed through {@link I#translate},
 * which resolves it against the language held in {@link I#Lang}: a sentence seen for the first time
 * is translated online once and stored in a local bundle, later requests come from that cache. The
 * result is a {@link kiss.Variable}, so widgets bound here follow a language change immediately and
 * without restarting the application.
 */
public final class LangUtil {

    /** A language the interface can be shown in. */
    public record Language(String code, String name) {
    }

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(LangUtil.class);

    private static final String LANGUAGE_KEY = "language";

    /**
     * The offered languages. Their names are written in their own language, the way every operating
     * system lists them, so a bad translation can never hide the option the user is looking for.
     * The first entry is the language the source sentences are written in.
     */
    public static final List<Language> LANGUAGES = List
            .of(new Language("en", "English"), new Language("ja", "日本語"), new Language("ko", "한국어"), new Language("zh-CN", "简体中文"), new Language("zh-TW", "繁體中文"), new Language("de", "Deutsch"), new Language("fr", "Français"), new Language("es", "Español"), new Language("it", "Italiano"), new Language("pt", "Português"), new Language("ru", "Русский"), new Language("nl", "Nederlands"));

    private LangUtil() {
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
    }
}
