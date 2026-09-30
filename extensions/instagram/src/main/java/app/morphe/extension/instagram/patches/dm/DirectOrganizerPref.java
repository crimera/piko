/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;

public class DirectOrganizerPref extends BaseSharedPref {

    private static final DirectOrganizerPref INSTANCE = new DirectOrganizerPref();

    private DirectOrganizerPref() {
        super("piko_dm");
    }

    public static String getStringPref(String key) {
        return INSTANCE.getString(key, "");
    }

    public static Boolean setStringPref(String key, String value) {
        return INSTANCE.setString(key, value);
    }

    // BaseSharedPref has no int accessors, so the value is stored as a string.
    public static int getIntPref(String key, int defaultValue) {
        try {
            return Integer.parseInt(INSTANCE.getString(key, ""));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static Boolean setIntPref(String key, int value) {
        return INSTANCE.setString(key, String.valueOf(value));
    }
}
