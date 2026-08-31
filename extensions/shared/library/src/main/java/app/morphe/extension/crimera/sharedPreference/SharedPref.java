/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.sharedPreference;

import java.util.Set;
import java.util.HashSet;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;
import app.morphe.extension.crimera.constants.ExtensionStrings;

public class SharedPref extends BaseSharedPref {
    private static final SharedPref INSTANCE = new SharedPref();

    public SharedPref() {
        super(ExtensionStrings.PIKO_SETTINGS);
    }

    // Static Wrapper Delegates
    public static Boolean getBooleanPref(BooleanSetting setting) {
        return INSTANCE.getBoolean(setting);
    }

    public static Boolean setBooleanPref(String key, Boolean val) {
        return INSTANCE.setBoolean(key, val);
    }

    public static String getStringPref(String key, String defaultValue) {
        return INSTANCE.getString(key, defaultValue);
    }

    public static String getStringPref(StringSetting setting) {
        return INSTANCE.getString(setting);
    }

    public static boolean setStringPref(String key, String defaultValue) {
        return INSTANCE.setString(key, defaultValue);
    }

    public static Set<String> getSetPref(StringSetting setting) {
        return INSTANCE.getSet(setting);
    }

    public static Boolean setSetPref(String key, Set<String> value) {
        return INSTANCE.setSet(key, value);
    }

    public static boolean clearAll() {
        return INSTANCE.clear();
    }

    public static boolean flush() {
        return INSTANCE.flushPreferences();
    }
}
