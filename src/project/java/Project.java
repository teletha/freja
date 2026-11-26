
/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
import static bee.api.License.*;

public class Project extends bee.api.Project {
    {
        product("io.github.teletha", "freja", "1.0");
        describe("A fast, local-first photo library for the desktop.");
        license(MIT);
        versionControlSystem("https://github.com/teletha/freja");

        require("io.github.teletha", "bun4j");
        require("io.github.teletha", "sinobu");
        require("io.github.teletha", "psychopath");
        require("io.github.teletha", "imagify");
        require("io.github.teletha", "apocrypha");
        require("com.formdev", "flatlaf");
        require("com.formdev", "flatlaf-extras");
        require("com.formdev", "flatlaf-intellij-themes");
        require("org.xerial", "sqlite-jdbc");
        require("org.apache.commons", "commons-compress");
        require("org.tukaani", "xz");
        require("io.github.teletha", "antibug").atTest();
    }
}