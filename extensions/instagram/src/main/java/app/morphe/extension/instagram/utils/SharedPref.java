package app.morphe.extension.instagram.utils;

import android.content.Context;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.SharedPrefCategory;
import app.morphe.extension.instagram.constants.Strings;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;

public class SharedPref {

    private static final Context ctx = app.morphe.extension.shared.Utils.getContext();
    private static final SharedPrefCategory sp = new SharedPrefCategory(Strings.PIKO_SETTINGS);

    public static Boolean getBooleanPerf(BooleanSetting setting) {
        return sp.getBoolean(setting.key, setting.defaultValue);
    }

    public static Boolean setBooleanPerf(String key, Boolean val) {
        try {
            sp.saveBoolean(key, val);
            return true;
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public static Boolean setStringPref(String key, String val) {
        try {
            sp.saveString(key, val);
            return true;
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public static String getStringPref(StringSetting setting) {
        String value = sp.getString(setting.key, setting.defaultValue);
        if (value.isBlank()) {
            return setting.defaultValue;
        }
        return value;
    }



}