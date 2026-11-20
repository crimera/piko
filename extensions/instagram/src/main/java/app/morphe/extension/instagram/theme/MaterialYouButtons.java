/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Material You colours for components that resolve their colours in code rather than through the
 * overlay: the grey secondary buttons (Edit profile, Share profile...), the default story ring
 * gradient and note bubbles.
 */
public final class MaterialYouButtons {

    private MaterialYouButtons() {
    }

    /**
     * Injected at the end of the SECONDARY IgdsButton painter's setup. The painter fills
     * its Paint from a ColorStateList it keeps and re-applies on every drawable state change, and
     * that list was resolved before the Material You overlay reached it, so both the Paint and the
     * list are replaced with the attribute's current (themed) colour. Pressed and disabled states
     * only change the Paint's alpha, which is kept.
     */
    public static void onSecondaryPainter(Object painter, Resources resources, TextView label) {
        if (painter == null || label == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !MaterialYouTheme.isMaterialYouEnabled()) {
            return;
        }
        try {
            Paint paint = paintOf(painter);
            if (paint == null) return;
            Context context = label.getContext();
            int stale = paint.getColor();
            int color = themeColor(context, "igds_color_secondary_button_panavision");
            if (color == 0 || (color | 0xff000000) == (stale | 0xff000000)) {
                int id = id(context, "piko_dynamic_secondary_container", "color");
                if (id == 0) return;
                color = context.getColor(id);
            }
            for (Field field : painterFields(painter.getClass())) {
                if (field.getType() != ColorStateList.class) continue;
                ColorStateList list = (ColorStateList) field.get(painter);
                if (list != null && list.getDefaultColor() == stale) {
                    field.set(painter, ColorStateList.valueOf(color));
                }
            }
            int alpha = paint.getAlpha();
            paint.setColor(color);
            paint.setAlpha(alpha);
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to theme a secondary button", t);
        }
    }

    /**
     * Injected at the returns of the static ring colour resolver (X.05iJ.A03(attr, fallback) in
     * 452), which turns the igds_color_gradient_* attributes into the hex strings of the default
     * story ring gradient once, at class init.
     */
    public static String ringColor(int attribute, int fallback, String hex) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !MaterialYouTheme.isMaterialYouEnabled()) {
            return hex;
        }
        try {
            Context context = Utils.getContext();
            String name = context.getResources().getResourceEntryName(attribute);
            String ring;
            switch (name) {
                case "igds_color_gradient_yellow": ring = "piko_dynamic_ring_1"; break;
                case "igds_color_gradient_orange": ring = "piko_dynamic_ring_2"; break;
                case "igds_color_gradient_pink": ring = "piko_dynamic_ring_4"; break;
                case "igds_color_gradient_lavender": ring = "piko_dynamic_ring_5"; break;
                default: return hex;
            }
            int id = id(context, ring, "color");
            if (id == 0) return hex;
            return Integer.toHexString(systemTone(context, ring, context.getColor(id)));
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to theme the story ring", t);
            return hex;
        }
    }

    /**
     * The ring aliases resolve through the Material You overlay, which the application context
     * the ring colours are read from may not have; read the system tone they stand for instead.
     */
    private static int systemTone(Context context, String alias, int fallback) {
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        String tone;
        switch (alias) {
            case "piko_dynamic_ring_1": tone = night ? "system_accent3_100" : "system_accent3_300"; break;
            case "piko_dynamic_ring_2": tone = night ? "system_accent3_200" : "system_accent3_500"; break;
            case "piko_dynamic_ring_4": tone = night ? "system_accent1_300" : "system_accent1_500"; break;
            case "piko_dynamic_ring_5": tone = night ? "system_accent1_400" : "system_accent1_700"; break;
            default: return fallback;
        }
        int id = id(context, tone, "color");
        return id == 0 ? fallback : context.getColor(id);
    }

    /**
     * Injected at the returns of NoteBubbleView.A0E(Context): the bubble fill. Notes without a
     * custom colour get the page background in light mode, so they vanish into it; those are
     * given the container tone. Custom note colours from the server are kept.
     */
    public static int noteBubbleColor(Context context, int color) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || !MaterialYouTheme.isMaterialYouEnabled()) {
            return color;
        }
        try {
            if (color != themeColor(context, "igds_color_primary_background")
                    && color != themeColor(context, "igds_color_elevated_highlight_background")) {
                return color;
            }
            int id = id(context, "piko_dynamic_secondary_container", "color");
            return id == 0 ? color : context.getColor(id);
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to theme a note bubble", t);
            return color;
        }
    }

    private static final Map<String, Integer> ids = new ConcurrentHashMap<>();

    /** Resource ids by type and name, looked up once: getIdentifier is a string search. */
    private static int id(Context context, String name, String type) {
        String key = type + "/" + name;
        Integer cached = ids.get(key);
        if (cached != null) return cached;
        String pkg = name.startsWith("system_") ? "android" : context.getPackageName();
        int id = context.getResources().getIdentifier(name, type, pkg);
        ids.put(key, id);
        return id;
    }

    private static int themeColor(Context context, String attribute) {
        int attr = id(context, attribute, "attr");
        if (attr == 0) return 0;
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) return 0;
        return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
    }

    private static final Map<Class<?>, Field[]> painterFields = new ConcurrentHashMap<>();

    /** The painter's Paint field first, then its ColorStateList fields; reflected once per class. */
    private static Field[] painterFields(Class<?> type) {
        Field[] cached = painterFields.get(type);
        if (cached != null) return cached;
        List<Field> paints = new ArrayList<>();
        List<Field> lists = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() == Paint.class) {
                    field.setAccessible(true);
                    paints.add(field);
                } else if (field.getType() == ColorStateList.class) {
                    field.setAccessible(true);
                    lists.add(field);
                }
            }
        }
        paints.addAll(lists);
        Field[] fields = paints.toArray(new Field[0]);
        painterFields.put(type, fields);
        return fields;
    }

    private static Paint paintOf(Object painter) throws IllegalAccessException {
        for (Field field : painterFields(painter.getClass())) {
            if (field.getType() == Paint.class) {
                Object value = field.get(painter);
                if (value != null) return (Paint) value;
            }
        }
        return null;
    }
}
