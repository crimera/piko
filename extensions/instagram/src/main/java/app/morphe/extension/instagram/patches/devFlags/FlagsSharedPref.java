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
                // Long-typed flags share this same pref file, so their stored
                // value (a number, or empty) won't match either bool state --
                // only treat it as a bool override if it actually looks like one.
                if (!flagState.equals(FlagState.ENABLE.toString()) && !flagState.equals(FlagState.DISABLE.toString())) continue;
                Boolean value = flagState.equals(FlagState.ENABLE.toString()) ? true:false;
                // PikoUtils.logger(key+" : "+Boolean.valueOf(value));
                if (!key.contains(",")) outFlags.put(key, value);
            }
            // A grouped choice takes precedence over older individual overrides, including Default.
            keys = flags.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.contains(",")) continue;
                String state = flags.optString(key);
                if (!state.equals(FlagState.DEFAULT.toString())
                        && !state.equals(FlagState.ENABLE.toString())
                        && !state.equals(FlagState.DISABLE.toString())) continue;
                for (String code : key.split(",")) {
                    if (state.equals(FlagState.DEFAULT.toString())) {
                        outFlags.remove(code);
                    } else {
                        outFlags.put(code, state.equals(FlagState.ENABLE.toString()));
                    }
                }
            }
        } catch (Exception e) {

        }
        return outFlags;
    }

    public static Map<String, Long> getAllLong(){
        Map<String, Long> outFlags = new HashMap();
        try {
            JSONObject flags = INSTANCE.all();
            Iterator<String> keys = flags.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String flagState = (String) flags.get(key);
                try {
                    Long value = Long.parseLong(flagState);
                    outFlags.put(key, value);
                } catch (NumberFormatException ignored) {
                    // Not a long override (empty, or a bool flag's own state) -- skip.
                }
            }
        } catch (Exception e) {

        }
        return outFlags;
    }
}
