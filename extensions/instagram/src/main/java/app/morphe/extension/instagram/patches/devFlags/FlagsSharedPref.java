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

import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;
import app.morphe.extension.crimera.settings.BooleanSetting;

import app.morphe.extension.instagram.constants.Constants;

public class FlagsSharedPref extends BaseSharedPref {

    private static final FlagsSharedPref INSTANCE = new FlagsSharedPref();

    public FlagsSharedPref() {
        super(Constants.REC_FLAGS);
    }

    // Static Wrapper Delegates
    public static Boolean getBooleanPref(BooleanSetting setting) {
        return INSTANCE.getBoolean(setting);
    }

    public static Boolean setBooleanPref(String key, Boolean val) {
        return INSTANCE.setBoolean(key, val);
    }

    public static Map<String, Boolean> getAll(){
        Map<String, Boolean> outFlags = new HashMap();
        try {
            JSONObject flags = INSTANCE.all();
            Iterator<String> keys = flags.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Boolean value = (Boolean) flags.get(key);
                outFlags.put(key, value);
            }
        } catch (Exception e) {

        }
        return outFlags;
    }
}
