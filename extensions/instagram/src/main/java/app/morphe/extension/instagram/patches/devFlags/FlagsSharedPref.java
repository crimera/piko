/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

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

}
