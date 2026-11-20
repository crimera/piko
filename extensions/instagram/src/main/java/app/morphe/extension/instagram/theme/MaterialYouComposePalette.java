/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.theme;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.SparseIntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Recolours Instagram's cached Compose Prism palettes with Material You tones.
 *
 * The palettes are plain objects of packed Compose colours ({@code argb << 32}), built from the
 * Prism grey and blue scales. Only their background was themed before, so Compose buttons, search
 * fields and secondary text kept Instagram's neutral greys, which sit badly on a tinted background.
 * Every field holding one of those scale values is rewritten here to the Material You tone of the
 * same role. A grey means a different role in light and dark (f8f9f9 is a surface in light and the
 * text colour in dark), so the table is picked by the background the palette is drawn on.
 */
public final class MaterialYouComposePalette {

    private MaterialYouComposePalette() {
    }

    // Material 3 roles (Android 14+) as used by the system Settings app, with tonal fallbacks.
    private static final int[][] LIGHT = {
            // Cards, fields and buttons sit on surface_bright over the surface_container page.
            {0xfcfcfd, role("surface_bright_light", "system_accent1_10")},
            {0xf8f9f9, role("surface_bright_light", "system_accent2_100")},
            {0xf3f5f7, role("surface_bright_light", "system_accent2_100")},
            {0xf0f2f5, role("surface_bright_light", "system_accent2_100")},
            {0xe9edf0, role("surface_container_high_light", "system_accent2_100")},
            {0xe7eaee, role("surface_container_highest_light", "system_accent2_200")},
            // Dividers, outlines and secondary text.
            {0xdce0e5, role("outline_variant_light", "system_neutral2_200")},
            {0xdbdfe4, role("outline_variant_light", "system_neutral2_200")},
            {0xc5cbd3, role("outline_variant_light", "system_neutral2_300")},
            {0xa2a9b4, role("outline_light", "system_neutral2_400")},
            {0xa2aab4, role("outline_light", "system_neutral2_400")},
            {0x838a93, role("outline_light", "system_neutral2_500")},
            {0x838993, role("outline_light", "system_neutral2_500")},
            {0x686f78, role("on_surface_variant_light", "system_neutral2_600")},
            {0x6f7680, role("on_surface_variant_light", "system_neutral2_600")},
            {0x5e646d, role("on_surface_variant_light", "system_neutral2_700")},
            {0x57606b, role("on_surface_variant_light", "system_neutral2_700")},
            // Dark greys are text and icons on a light background.
            {0x48505b, role("on_surface_variant_light", "system_neutral2_800")},
            {0x363c44, role("on_surface_light", "system_neutral1_900")},
            {0x2b3036, role("on_surface_light", "system_neutral1_900")},
            {0x25292e, role("on_surface_light", "system_neutral1_900")},
            {0x212328, role("on_surface_light", "system_neutral1_900")},
            {0x191c1f, role("on_surface_light", "system_neutral1_900")},
            {0x0c1014, role("on_surface_light", "system_neutral1_900")},
            // Instagram blue is the primary colour.
            {0x0095f6, role("primary_light", "system_accent1_600")},
            {0x007fe0, role("primary_light", "system_accent1_600")},
            {0x0b6ed3, role("on_primary_container_light", "system_accent1_700")},
            {0x016dcb, role("on_primary_container_light", "system_accent1_700")},
    };

    private static final int[][] DARK = {
            // Page, then cards and fields on surface_bright like the Settings app.
            {0x0c1014, role("surface_container_dark", "system_neutral1_900")},
            {0x191c1f, role("surface_container_high_dark", "system_neutral2_900")},
            {0x212328, role("surface_bright_dark", "system_accent2_800")},
            {0x25292e, role("surface_bright_dark", "system_accent2_800")},
            {0x2b3036, role("surface_bright_dark", "system_accent2_800")},
            {0x363c44, role("outline_variant_dark", "system_accent2_700")},
            {0x48505b, role("outline_variant_dark", "system_neutral2_700")},
            {0x57606b, role("outline_dark", "system_neutral2_600")},
            {0x5e646d, role("outline_dark", "system_neutral2_600")},
            // Secondary text and icons.
            {0x686f78, role("outline_dark", "system_neutral2_500")},
            {0x6f7680, role("outline_dark", "system_neutral2_500")},
            {0x838a93, role("on_surface_variant_dark", "system_neutral2_400")},
            {0x838993, role("on_surface_variant_dark", "system_neutral2_400")},
            {0xa2a9b4, role("on_surface_variant_dark", "system_neutral2_300")},
            {0xa2aab4, role("on_surface_variant_dark", "system_neutral2_300")},
            {0xc5cbd3, role("on_surface_variant_dark", "system_neutral2_200")},
            {0xdce0e5, role("on_surface_variant_dark", "system_neutral2_200")},
            {0xdbdfe4, role("on_surface_variant_dark", "system_neutral2_200")},
            {0xe7eaee, role("on_surface_dark", "system_neutral2_100")},
            {0xe9edf0, role("on_surface_dark", "system_neutral2_100")},
            {0xf0f2f5, role("on_surface_dark", "system_neutral2_100")},
            // Light greys are the text colour on a dark background.
            {0xf3f5f7, role("on_surface_dark", "system_neutral1_50")},
            {0xf8f9f9, role("on_surface_dark", "system_neutral1_50")},
            {0xfcfcfd, role("on_surface_dark", "system_neutral1_50")},
            {0x0095f6, role("primary_dark", "system_accent1_200")},
            {0x007fe0, role("primary_dark", "system_accent1_200")},
            {0x0b6ed3, role("primary_dark", "system_accent1_300")},
            {0x016dcb, role("primary_dark", "system_accent1_300")},
    };

    /** A Material 3 role colour on Android 14+, else the tonal palette colour. */
    private static int role(String role, String fallback) {
        if (Build.VERSION.SDK_INT >= 34) {
            int value = tone("system_" + role);
            if (value != 0) return value;
        }
        return tone(fallback);
    }

    private static SparseIntArray lightMap;
    private static SparseIntArray darkMap;
    private static Class<?> fieldsClass;
    private static List<Field> colorFields;

    /** A system palette colour by name, resolved when the tables are built; 0 when unavailable. */
    private static int tone(String name) {
        try {
            Context context = Utils.getContext();
            int id = context.getResources().getIdentifier(name, "color", "android");
            return id == 0 ? 0 : context.getColor(id) & 0xffffff;
        } catch (Exception e) {
            return 0;
        }
    }

    private static SparseIntArray build(int[][] table) {
        SparseIntArray map = new SparseIntArray(table.length);
        for (int[] row : table) {
            if (row[1] != 0 || row[0] == 0) {
                map.put(row[0], row[1]);
            }
        }
        return map;
    }

    /**
     * Injected into the Compose palette bridge after the background is written.
     *
     * @param palette    a cached Prism palette.
     * @param background the Material You background the palette was given.
     */
    public static void remap(Object palette, int background) {
        if (palette == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !MaterialYouTheme.isMaterialYouEnabled()) {
            return;
        }
        try {
            boolean dark = luminance(background) < 0.4;
            SparseIntArray map;
            synchronized (MaterialYouComposePalette.class) {
                if (lightMap == null) {
                    lightMap = build(LIGHT);
                    darkMap = build(DARK);
                }
                map = dark ? darkMap : lightMap;
                if (dark && MaterialYouTheme.isAmoledEnabled()) {
                    // Pure black AMOLED: the page and its near-black surfaces stay black.
                    map = amoledMap();
                }
                if (fieldsClass != palette.getClass()) {
                    colorFields = longFields(palette.getClass());
                    fieldsClass = palette.getClass();
                }
            }
            for (Field field : colorFields) {
                long packed = field.getLong(palette);
                // sRGB Compose colours keep the colour space id (0) in the low bits.
                if ((packed & 0xffffffffL) != 0) continue;
                int argb = (int) (packed >>> 32);
                int alpha = argb >>> 24;
                int replacement = map.get(argb & 0xffffff, -1);
                if (replacement < 0) continue;
                long next = ((long) ((alpha << 24) | replacement)) << 32;
                field.setLong(palette, next);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to apply Material You to a Compose palette", t);
        }
    }

    private static SparseIntArray amoledMap;

    private static SparseIntArray amoledMap() {
        if (amoledMap == null) {
            SparseIntArray map = darkMap.clone();
            map.put(0x0c1014, 0x000000);
            map.put(0x191c1f, 0x000000);
            amoledMap = map;
        }
        return amoledMap;
    }

    private static List<Field> longFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == long.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static double luminance(int argb) {
        return (0.299 * Color.red(argb) + 0.587 * Color.green(argb) + 0.114 * Color.blue(argb)) / 255.0;
    }
}
