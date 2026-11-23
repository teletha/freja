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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SmartTagsTest {

    @Test
    void readsLeadingBracketGroups() {
        assertEquals(List.of("旅行", "家族"), SmartTags.fromFolderName("（旅行）【家族】2024 写真"));
        assertEquals(List.of("旅行"), SmartTags.fromFolderName("「旅行」のアルバム"));
        assertEquals(List.of("旅行"), SmartTags.fromFolderName("(旅行) album"));
    }

    @Test
    void ignoresBracketsThatAreNotLeading() {
        assertTrue(SmartTags.fromFolderName("旅行（2024）").isEmpty());
        assertTrue(SmartTags.fromFolderName("写真 【旅行】").isEmpty());
    }

    @Test
    void normalisesTagsToHalfWidth() {
        assertEquals(List.of("Trip", "2024"), SmartTags.fromFolderName("(Trip) (2024) photos"));
        assertEquals(List.of("Trip", "2024"), SmartTags.fromFolderName("（Ｔｒｉｐ）（２０２４）写真"));
        assertEquals("ガ", SmartTags.normalize("ガ"));
        assertEquals("ガ", SmartTags.normalize("ｶﾞ"));
        assertEquals("ABC", SmartTags.normalize("ＡＢＣ"));
        assertEquals("旅行", SmartTags.normalize(" 旅行 "));
    }

    @Test
    void ignoresUnclosedAndEmptyGroups() {
        assertTrue(SmartTags.fromFolderName("(旅行 2024").isEmpty());
        assertTrue(SmartTags.fromFolderName("（）写真").isEmpty());
    }
}
