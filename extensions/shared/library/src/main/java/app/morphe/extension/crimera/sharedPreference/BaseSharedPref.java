/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.sharedPreference;

import android.content.Context;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.crimera.constants.ExtensionStrings;

public abstract class BaseSharedPref {

    protected final PikoSharedPrefCategory sp;

    protected BaseSharedPref(Context context, String sharedPrefName) {
        Context ctx = Utils.getContext();
        this.sp = (ctx != null) ? new PikoSharedPrefCategory(sharedPrefName) : null;
    }

    protected BaseSharedPref(String sharedPrefName) {
        this(Utils.getContext(), sharedPrefName);
    }

    public Boolean getBoolean(BooleanSetting setting) {
        Boolean defaultValue = setting.defaultValue;
        if (sp != null) {
            return sp.getBoolean(setting.key, defaultValue);
        }
        return defaultValue;
    }

    public Boolean setBoolean(String key, Boolean val) {
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

    public Boolean setString(String key, String val) {
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

    public String getString(String key, String defaultValue) {
        if (sp == null)
            return defaultValue;

        String value = sp.getString(key, defaultValue);
        if (value.isBlank())
            return defaultValue;
        return value;
    }

    public String getString(StringSetting setting) {
        return SharedPref.getStringPref(setting.key, setting.defaultValue);
    }

    public Set<String> getSet(StringSetting stringSetting) {
        Set<String> defVal = new HashSet();
        if (sp != null) {
            return sp.getSet(stringSetting.key, defVal);
        }
        return defVal;
    }

    public Boolean setSet(String key, Set<String> value) {
        try {
            if (sp != null) {
                sp.saveSet(key, value);
                return true;
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public boolean clear() {
        try {
            if (sp != null) {
                sp.clearAll();
                return true;
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    protected boolean flushPreferences() {
        try {
            if (sp != null) {
                return sp.preferences.edit().commit();
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return false;
    }

    public JSONObject all() {
        try {
            if (sp != null) {
                return sp.getAll();
            }
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
        }
        return null;
    }

}
