/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

import org.json.JSONObject;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;
import app.morphe.extension.crimera.settings.StringSetting;

import app.morphe.extension.instagram.constants.Constants;

public class FlagsSharedPref extends BaseSharedPref {

    private static final FlagsSharedPref INSTANCE = new FlagsSharedPref();

    public FlagsSharedPref() {
        super(Constants.REC_FLAGS);
    }

    // Static Wrapper Delegates
    public static String getStringPref(StringSetting setting) {
        return INSTANCE.getString(setting);
    }

    public static Boolean setStringPref(String key, String val) {
        return INSTANCE.setString(key, val);
    }

    public static boolean flush() {
        return INSTANCE.flushPreferences();
    }

    public static Map<String, Boolean> getAll(){
        Map<String, Boolean> outFlags = new HashMap();
        try {
            JSONObject flags = INSTANCE.all();
            Iterator<String> keys = flags.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String flagState = (String) flags.get(key);
                if (flagState.equals(FlagState.DEFAULT.toString())) continue;
                Boolean value = flagState.equals(FlagState.ENABLE.toString()) ? true:false;
                PikoUtils.logger(key+" : "+Boolean.valueOf(value));
                outFlags.put(key, value);
            }
        } catch (Exception e) {

        }
        return outFlags;
    }
}
