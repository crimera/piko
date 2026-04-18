/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.utils;

import android.content.Context;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.SharedPrefCategory;
import app.morphe.extension.instagram.constants.Strings;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;

public class SharedPref {

    private static final Context ctx = app.morphe.extension.shared.Utils.getContext();
    private static SharedPrefCategory sp;

    static {
        if(ctx!=null) {
            sp = new SharedPrefCategory(Strings.PIKO_SETTINGS);
        }
    }

    public static Boolean getBooleanPerf(BooleanSetting setting) {
        Boolean defaultValue = setting.defaultValue;
        if(sp!=null) {
            return sp.getBoolean(setting.key, defaultValue);
        }
        return defaultValue;
    }

    public static Boolean setBooleanPerf(String key, Boolean val) {
        try {
            if(sp!=null) {
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
            if(sp!=null) {
                sp.saveString(key, val);
                return true;
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public static String getStringPref(StringSetting setting) {
        String defaultValue = setting.defaultValue;
        if(sp == null)
            return defaultValue;

        String value = sp.getString(setting.key, defaultValue);
        if (value.isBlank())
            return defaultValue;

        return value;
    }



}