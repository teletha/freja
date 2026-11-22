
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
        license(MIT);
        versionControlSystem("https://github.com/teletha/freja");

        require("io.github.teletha", "sinobu");
        require("io.github.teletha", "psychopath");
        require("io.github.teletha", "imagify");
        require("com.formdev", "flatlaf");
        require("io.github.teletha", "antibug").atTest();
    }
}