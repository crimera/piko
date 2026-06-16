/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera;

import android.content.Context;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.crimera.constants.ExtensionStrings;

public class SharedPref {
    private static final Context ctx = Utils.getContext();
    private static PikoSharedPrefCategory sp;

    static {
        if (ctx != null) {
            sp = new PikoSharedPrefCategory(ExtensionStrings.PIKO_SETTINGS);
        }
    }

    public static Boolean getBooleanPref(BooleanSetting setting) {
        Boolean defaultValue = setting.defaultValue;
        if (sp != null) {
            return sp.getBoolean(setting.key, defaultValue);
        }
        return defaultValue;
    }

    public static Boolean setBooleanPref(String key, Boolean val) {
        try {
            if (sp != null) {
                sp.saveBoolean(key, val);
                return true;
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public static Boolean setStringPref(String key, String val) {
        try {
            if (sp != null) {
                sp.saveString(key, val);
                return true;
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public static String getStringPref(String key, String defaultValue) {
        if (sp == null)
            return defaultValue;

        String value = sp.getString(key, defaultValue);
        if (value.isBlank())
            return defaultValue;
        return value;
    }

    public static String getStringPref(StringSetting setting) {
        return SharedPref.getStringPref(setting.key, setting.defaultValue);
    }

}