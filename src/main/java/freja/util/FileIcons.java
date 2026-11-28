/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.util;

import java.nio.file.Path;
import java.util.Map;

import javax.swing.Icon;

import freja.Icons;

/** File-type icons from the Material Icon Theme set, keyed by extension. */
public final class FileIcons {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("png", "image"), Map.entry("jpg", "image"), Map.entry("jpeg", "image"),
            Map.entry("gif", "image"), Map.entry("bmp", "image"), Map.entry("webp", "image"),
            Map.entry("svg", "svg"), Map.entry("ico", "image"), Map.entry("tif", "image"),
            Map.entry("tiff", "image"), Map.entry("heic", "image"),
            Map.entry("mp4", "video"), Map.entry("mkv", "video"), Map.entry("webm", "video"),
            Map.entry("mov", "video"), Map.entry("avi", "video"), Map.entry("wmv", "video"),
            Map.entry("flv", "video"), Map.entry("m4v", "video"),
            Map.entry("mp3", "audio"), Map.entry("wav", "audio"), Map.entry("flac", "audio"),
            Map.entry("aac", "audio"), Map.entry("ogg", "audio"), Map.entry("m4a", "audio"),
            Map.entry("zip", "zip"), Map.entry("7z", "zip"), Map.entry("rar", "zip"),
            Map.entry("tar", "zip"), Map.entry("gz", "zip"), Map.entry("bz2", "zip"),
            Map.entry("pdf", "pdf"), Map.entry("doc", "word"), Map.entry("docx", "word"),
            Map.entry("xls", "table"), Map.entry("xlsx", "table"), Map.entry("csv", "table"),
            Map.entry("ppt", "powerpoint"), Map.entry("pptx", "powerpoint"),
            Map.entry("txt", "document"), Map.entry("md", "markdown"), Map.entry("rtf", "document"),
            Map.entry("json", "json"), Map.entry("xml", "xml"), Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"), Map.entry("toml", "toml"),
            Map.entry("java", "java"), Map.entry("js", "javascript"), Map.entry("mjs", "javascript"),
            Map.entry("ts", "typescript"), Map.entry("tsx", "react-ts"), Map.entry("jsx", "react"),
            Map.entry("css", "css"), Map.entry("scss", "sass"), Map.entry("less", "less"),
            Map.entry("html", "html"), Map.entry("htm", "html"), Map.entry("vue", "vue"),
            Map.entry("py", "python"), Map.entry("rs", "rust"), Map.entry("go", "go"),
            Map.entry("c", "c"), Map.entry("h", "h"), Map.entry("cpp", "cpp"), Map.entry("hpp", "hpp"),
            Map.entry("cs", "csharp"), Map.entry("php", "php"), Map.entry("rb", "ruby"),
            Map.entry("kt", "kotlin"), Map.entry("kts", "kotlin"), Map.entry("swift", "swift"),
            Map.entry("dart", "dart"), Map.entry("lua", "lua"), Map.entry("sh", "shellcheck"),
            Map.entry("bat", "console"), Map.entry("ps1", "powershell"), Map.entry("sql", "database"),
            Map.entry("exe", "exe"), Map.entry("msi", "exe"), Map.entry("dll", "dll"),
            Map.entry("jar", "jar"), Map.entry("log", "log"), Map.entry("lock", "lock"),
            Map.entry("epub", "epub"));

    private FileIcons() {
    }

    /** Returns the Material Icon Theme icon for a file, or a document icon for unknown types. */
    public static Icon icon(Path path, int size) {
        String name = NAMES.getOrDefault(PathUtil.extension(path), "document");
        return Icons.raw("material-icon-theme:" + name, size);
    }
}
