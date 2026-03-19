/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings.preference;

import android.content.Context;
import android.preference.Preference;

import app.morphe.extension.instagram.utils.SharedPref;
import app.morphe.extension.instagram.settings.preference.widgets.SwitchPref;
import app.morphe.extension.instagram.settings.preference.widgets.ListPref;
import app.morphe.extension.instagram.settings.preference.widgets.ButtonPref;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;


public class Helper {
    private final Context context;

    public Helper(Context context) {
        this.context = context;
    }

    public Preference switchPreference(String title, String summary, BooleanSetting setting) {
        SwitchPref preference = new SwitchPref(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setSingleLineTitle(false);
        return preference;
    }

    public Preference listPreference(String title, String summary, StringSetting setting) {
        ListPref preference = new ListPref(context);
        String key = setting.key;
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setSingleLineTitle(false);
        return preference;
    }

    public Preference buttonPreference(String title, String summary, String setting) {
        ButtonPref preference = new ButtonPref(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting);
        preference.setSingleLineTitle(false);
        return preference;
    }


    public void setValue(Preference preference, Object newValue) {
        String key = preference.getKey();
        try {
            if (newValue != null) {
                String newValClass = newValue.getClass().getSimpleName();

                if (newValClass.equals("Boolean")) {
                    SharedPref.setBooleanPerf(key, (Boolean) newValue);
                } else if (newValClass.equals("String")) {
                    SharedPref.setStringPref(key, (String) newValue);
                }
            }

        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
            Logger.printException(() -> "Failed setting pref: ", ex);
        }
    }

}