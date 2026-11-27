/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package freja.media.store;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A small, dependency-free reader for the EXIF block of a JPEG file.
 * <p>
 * The application only needs a handful of tags, so this reads the TIFF structure directly instead
 * of pulling in a general purpose metadata library. Anything malformed is treated as absent: a
 * damaged camera file must never stop the library from being scanned.
 * </p>
 */
public final class Exif {

    /** EXIF tag: camera manufacturer. */
    private static final int TAG_MAKE = 0x010F;

    /** EXIF tag: camera model. */
    private static final int TAG_MODEL = 0x0110;

    /** EXIF tag: orientation. */
    private static final int TAG_ORIENTATION = 0x0112;

    /** EXIF tag: file date and time. */
    private static final int TAG_DATETIME = 0x0132;

    /** EXIF tag: pointer to the Exif sub-IFD. */
    private static final int TAG_EXIF_IFD = 0x8769;

    /** EXIF tag: original capture date and time. */
    private static final int TAG_DATETIME_ORIGINAL = 0x9003;

    /** EXIF tag: digitized date and time. */
    private static final int TAG_DATETIME_DIGITIZED = 0x9004;

    /** EXIF tag: lens model. */
    private static final int TAG_LENS_MODEL = 0xA434;

    /** EXIF tag: exposure time as a rational. */
    private static final int TAG_EXPOSURE_TIME = 0x829A;

    /** EXIF tag: aperture as an f-number rational. */
    private static final int TAG_FNUMBER = 0x829D;

    /** EXIF tag: ISO speed. */
    private static final int TAG_ISO = 0x8827;

    /** EXIF tag: focal length as a rational. */
    private static final int TAG_FOCAL_LENGTH = 0x920A;

    /** The date format written by nearly every camera. */
    private static final DateTimeFormatter EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    /** The parsed values. All fields are optional. */
    public record Data(String make, String model, String lens, Long capturedAt, int orientation, String exposure,
            Double aperture, Integer iso, Double focalLength) {

        /** The empty result. */
        public static final Data EMPTY = new Data(null, null, null, null, 1, null, null, null, null);

        public Data {
            make = blankToNull(make);
            model = blankToNull(model);
            lens = blankToNull(lens);
            exposure = blankToNull(exposure);
            if (orientation < 1 || orientation > 8) {
                orientation = 1;
            }
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        /**
         * Returns a compact camera label.
         *
         * @return for example "SONY ILCE-7M3", or null when the file carries no camera name
         */
        public String camera() {
            if (make == null) {
                return model;
            }
            if (model == null) {
                return make;
            }
            return model.regionMatches(true, 0, make, 0, make.length()) ? model : make + " " + model;
        }
    }

    /** A TIFF IFD entry. */
    private record Entry(int type, int count, long valueOffset, int entryOffset) {
    }

    private Exif() {
    }

    /**
     * Reads the EXIF block of a file.
     *
     * @param file an image file
     * @return the metadata, or {@link Data#EMPTY} when the file is not a JPEG or has no EXIF block
     */
    public static Data read(Path file) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 64 * 1024))) {
            return scan(in);
        } catch (IOException | RuntimeException e) {
            return Data.EMPTY;
        }
    }

    /**
     * Reads the EXIF block from an in-memory JPEG, which is what a scan already has after reading
     * the file for decoding. It avoids opening the same file a second time.
     *
     * @param data a complete JPEG file
     * @return the metadata, or {@link Data#EMPTY} when there is no usable EXIF block
     */
    public static Data read(byte[] data) {
        if (data == null || data.length < 2) {
            return Data.EMPTY;
        }
        return scan(new DataInputStream(new ByteArrayInputStream(data)));
    }

    private static Data scan(DataInputStream in) {
        try {
            if (in.readUnsignedShort() != 0xFFD8) {
                return Data.EMPTY;
            }
            while (true) {
                int marker = in.read();
                if (marker < 0) {
                    return Data.EMPTY;
                }
                while (marker == 0xFF) {
                    marker = in.read();
                    if (marker < 0) {
                        return Data.EMPTY;
                    }
                }
                // Markers without a payload.
                if (marker == 0x01 || marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD7)) {
                    continue;
                }
                // Start of scan or end of image: no more metadata follows.
                if (marker == 0xDA || marker == 0xD9) {
                    return Data.EMPTY;
                }
                int length = in.readUnsignedShort();
                if (length < 2) {
                    return Data.EMPTY;
                }
                int payload = length - 2;
                if (marker == 0xE1 && payload >= 6) {
                    byte[] data = new byte[payload];
                    in.readFully(data);
                    if (data[0] == 'E' && data[1] == 'x' && data[2] == 'i' && data[3] == 'f' && data[4] == 0 && data[5] == 0) {
                        return parseTiff(Arrays.copyOfRange(data, 6, data.length));
                    }
                } else {
                    in.skipNBytes(payload);
                }
            }
        } catch (IOException | RuntimeException e) {
            return Data.EMPTY;
        }
    }

    /**
     * Parses a TIFF/EXIF block.
     *
     * @param tiff the bytes beginning at the TIFF header
     * @return the parsed metadata, or {@link Data#EMPTY} for a malformed block
     */
    static Data parseTiff(byte[] tiff) {
        try {
            if (tiff == null || tiff.length < 8) {
                return Data.EMPTY;
            }
            boolean little;
            if (tiff[0] == 'I' && tiff[1] == 'I') {
                little = true;
            } else if (tiff[0] == 'M' && tiff[1] == 'M') {
                little = false;
            } else {
                return Data.EMPTY;
            }
            if (u16(tiff, 2, little) != 42) {
                return Data.EMPTY;
            }
            Map<Integer, Entry> ifd0 = readIfd(tiff, u32(tiff, 4, little), little);
            if (ifd0 == null) {
                return Data.EMPTY;
            }
            Map<Integer, Entry> exif = ifd0.containsKey(TAG_EXIF_IFD) ? readIfd(tiff, number(tiff, ifd0.get(TAG_EXIF_IFD), little), little) : null;

            String make = string(tiff, ifd0.get(TAG_MAKE), little);
            String model = string(tiff, ifd0.get(TAG_MODEL), little);
            int orientation = (int) number(tiff, ifd0.get(TAG_ORIENTATION), little);

            String lens = exif == null ? null : string(tiff, exif.get(TAG_LENS_MODEL), little);
            String date = exif == null ? null : string(tiff, exif.get(TAG_DATETIME_ORIGINAL), little);
            if (date == null && exif != null) {
                date = string(tiff, exif.get(TAG_DATETIME_DIGITIZED), little);
            }
            if (date == null) {
                date = string(tiff, ifd0.get(TAG_DATETIME), little);
            }
            Long capturedAt = parseDate(date);

            Double exposureSeconds = exif == null ? null : rational(tiff, exif.get(TAG_EXPOSURE_TIME), little);
            Double aperture = exif == null ? null : rational(tiff, exif.get(TAG_FNUMBER), little);
            long isoValue = exif == null ? 0 : number(tiff, exif.get(TAG_ISO), little);
            Double focalLength = exif == null ? null : rational(tiff, exif.get(TAG_FOCAL_LENGTH), little);

            return new Data(make, model, lens, capturedAt, orientation, formatExposure(exposureSeconds), round(aperture), isoValue > 0 && isoValue < 1_000_000 ? (int) isoValue : null, round(focalLength));
        } catch (RuntimeException e) {
            return Data.EMPTY;
        }
    }

    /**
     * Reads one IFD.
     *
     * @param data the TIFF bytes
     * @param offset the offset of the IFD, relative to the TIFF header
     * @param little whether the byte order is little endian
     * @return the entries keyed by tag, or null for an invalid offset
     */
    private static Map<Integer, Entry> readIfd(byte[] data, long offset, boolean little) {
        if (offset < 8 || offset + 2 > data.length) {
            return null;
        }
        int at = (int) offset;
        int count = u16(data, at, little);
        if (count < 0 || count > 2048 || at + 2L + count * 12L + 4 > data.length) {
            return null;
        }
        Map<Integer, Entry> entries = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            int entryOffset = at + 2 + i * 12;
            int tag = u16(data, entryOffset, little);
            int type = u16(data, entryOffset + 2, little);
            long valueCount = u32(data, entryOffset + 4, little);
            long valueOffset = u32(data, entryOffset + 8, little);
            if (type >= 1 && type <= 12 && valueCount > 0 && valueCount <= 1_000_000) {
                entries.putIfAbsent(tag, new Entry(type, (int) valueCount, valueOffset, entryOffset));
            }
        }
        return entries;
    }

    /**
     * Reads an ASCII value.
     */
    private static String string(byte[] data, Entry entry, boolean little) {
        if (entry == null || entry.type != 2) {
            return null;
        }
        int start = entry.count <= 4 ? entry.entryOffset + 8 : safeOffset(data, entry.valueOffset);
        if (start < 0) {
            return null;
        }
        int length = Math.min(entry.count, data.length - start);
        if (length <= 0) {
            return null;
        }
        String value = new String(data, start, length, StandardCharsets.UTF_8);
        int zero = value.indexOf('\0');
        if (zero >= 0) {
            value = value.substring(0, zero);
        }
        return value.trim();
    }

    /**
     * Reads the first numeric value of an entry.
     */
    private static long number(byte[] data, Entry entry, boolean little) {
        if (entry == null) {
            return 0;
        }
        int start = entry.count * sizeOf(entry.type) <= 4 ? entry.entryOffset + 8 : safeOffset(data, entry.valueOffset);
        if (start < 0) {
            return 0;
        }
        return switch (entry.type) {
        case 1, 6, 7 -> start < data.length ? data[start] & 0xFF : 0;
        case 3 -> start + 2 <= data.length ? u16(data, start, little) : 0;
        case 4 -> start + 4 <= data.length ? u32(data, start, little) : 0;
        case 8 -> start + 2 <= data.length ? (short) u16(data, start, little) : 0;
        case 9 -> start + 4 <= data.length ? s32(data, start, little) : 0;
        default -> 0;
        };
    }

    /**
     * Reads a rational value.
     */
    private static Double rational(byte[] data, Entry entry, boolean little) {
        if (entry == null || (entry.type != 5 && entry.type != 10)) {
            return null;
        }
        int start = entry.count * 8 <= 4 ? entry.entryOffset + 8 : safeOffset(data, entry.valueOffset);
        if (start < 0 || start + 8 > data.length) {
            return null;
        }
        long numerator = entry.type == 5 ? u32(data, start, little) : s32(data, start, little);
        long denominator = entry.type == 5 ? u32(data, start + 4, little) : s32(data, start + 4, little);
        if (denominator == 0) {
            return null;
        }
        return (double) numerator / denominator;
    }

    private static int safeOffset(byte[] data, long value) {
        return value < 0 || value >= data.length ? -1 : (int) value;
    }

    private static int sizeOf(int type) {
        return switch (type) {
        case 1, 2, 6, 7 -> 1;
        case 3, 8 -> 2;
        case 4, 9, 11 -> 4;
        case 5, 10, 12 -> 8;
        default -> 0;
        };
    }

    private static Long parseDate(String value) {
        if (value == null || value.length() < 19) {
            return null;
        }
        try {
            LocalDateTime time = LocalDateTime.parse(value.substring(0, 19), EXIF_DATE);
            return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String formatExposure(Double seconds) {
        if (seconds == null || seconds <= 0 || !Double.isFinite(seconds)) {
            return null;
        }
        if (seconds < 1) {
            return "1/" + Math.max(1, Math.round(1 / seconds)) + " 秒";
        }
        return (Math.round(seconds * 10) / 10.0) + " 秒";
    }

    private static Double round(Double value) {
        return value == null || !Double.isFinite(value) ? null : Math.round(value * 10) / 10.0;
    }

    private static int u16(byte[] data, int offset, boolean little) {
        if (offset < 0 || offset + 2 > data.length) {
            return 0;
        }
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return little ? b0 | (b1 << 8) : (b0 << 8) | b1;
    }

    private static long u32(byte[] data, int offset, boolean little) {
        if (offset < 0 || offset + 4 > data.length) {
            return 0;
        }
        long b0 = data[offset] & 0xFFL;
        long b1 = data[offset + 1] & 0xFFL;
        long b2 = data[offset + 2] & 0xFFL;
        long b3 = data[offset + 3] & 0xFFL;
        return little ? b0 | (b1 << 8) | (b2 << 16) | (b3 << 24) : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private static int s32(byte[] data, int offset, boolean little) {
        return (int) u32(data, offset, little);
    }
}
