/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.store;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * Tests the small EXIF reader with a hand-built TIFF block.
 */
class ExifTest {

    @Test
    void parsesCommonTags() {
        byte[] tiff = tiff();
        Exif.Data data = Exif.parseTiff(tiff);
        assertEquals("Acme Camera", data.make());
        assertEquals("Model X", data.model());
        assertEquals(6, data.orientation());
        assertEquals("Prime 50", data.lens());
        assertEquals(2.8, data.aperture());
        assertNotNull(data.capturedAt());
        long expected = LocalDateTime.of(2024, 5, 6, 7, 8, 9).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expected, data.capturedAt());
    }

    @Test
    void rejectsMalformedBlocks() {
        assertSame(Exif.Data.EMPTY, Exif.parseTiff(null));
        assertSame(Exif.Data.EMPTY, Exif.parseTiff(new byte[] {1, 2, 3}));
        assertSame(Exif.Data.EMPTY, Exif.parseTiff("not a tiff".getBytes()));
    }

    /**
     * Builds a little endian TIFF with two IFDs.
     */
    private static byte[] tiff() {
        byte[] data = new byte[170];
        data[0] = 'I';
        data[1] = 'I';
        u16(data, 2, 42);
        u32(data, 4, 8);

        // IFD0
        int ifd0 = 8;
        u16(data, ifd0, 4);
        entry(data, ifd0 + 2, 0x010F, 2, 12, 62);
        entry(data, ifd0 + 2 + 12, 0x0110, 2, 8, 74);
        entryInlineShort(data, ifd0 + 2 + 24, 0x0112, 6);
        entry(data, ifd0 + 2 + 36, 0x8769, 4, 1, 84);
        u32(data, ifd0 + 2 + 48, 0);

        ascii(data, 62, "Acme Camera");
        ascii(data, 74, "Model X");

        // Exif IFD
        int exif = 84;
        u16(data, exif, 3);
        entry(data, exif + 2, 0x9003, 2, 20, 128);
        entry(data, exif + 14, 0xA434, 2, 9, 148);
        entry(data, exif + 26, 0x829D, 5, 1, 157);
        u32(data, exif + 38, 0);

        ascii(data, 128, "2024:05:06 07:08:09");
        ascii(data, 148, "Prime 50");
        u32(data, 157, 28);
        u32(data, 161, 10);
        return data;
    }

    private static void entry(byte[] data, int offset, int tag, int type, int count, int valueOffset) {
        u16(data, offset, tag);
        u16(data, offset + 2, type);
        u32(data, offset + 4, count);
        u32(data, offset + 8, valueOffset);
    }

    private static void entryInlineShort(byte[] data, int offset, int tag, int value) {
        u16(data, offset, tag);
        u16(data, offset + 2, 3);
        u32(data, offset + 4, 1);
        u16(data, offset + 8, value);
    }

    private static void ascii(byte[] data, int offset, String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, offset, bytes.length);
    }

    private static void u16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void u32(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }
}
