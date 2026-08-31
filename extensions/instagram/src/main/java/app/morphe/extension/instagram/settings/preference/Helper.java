/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings.preference;

import android.content.Context;
import android.preference.Preference;
import java.util.Set;

import app.morphe.extension.instagram.settings.preference.widgets.SwitchPref;
import app.morphe.extension.instagram.settings.preference.widgets.ListPref;
import app.morphe.extension.instagram.settings.preference.widgets.ButtonPref;
import app.morphe.extension.instagram.settings.preference.widgets.EditTextPref;
import app.morphe.extension.instagram.settings.preference.widgets.MultiSelectListPref;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.theme.MaterialYouTheme;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;

import  app.morphe.extension.instagram.patches.devFlags.FlagsSharedPref;

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
        if (Settings.AMOLED_THEME.key.equals(setting.key)) {
            preference.setSwitchInteractionEnabled(
                    MaterialYouTheme.canEnableAmoled(context)
            );
        }
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
        return preference;
    }

    public Preference buttonPreference(String title, String summary, String setting) {
        ButtonPref preference = new ButtonPref(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting);
        return preference;
    }

    public Preference editTextPreference(String title, String summary, StringSetting setting) {
        EditTextPref preference = new EditTextPref(context);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        return preference;
    }

    public Preference editTextNumPreference(String title, String summary, StringSetting setting) {
        EditTextPref preference = (EditTextPref)editTextPreference(title,summary,setting);
        preference.setNumericOnly(true);
        return preference;
    }
    public Preference multiSelectListPref(String title, String summary, StringSetting setting) {
        MultiSelectListPref preference = new MultiSelectListPref(context);
        String key = setting.key;
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(key);
        preference.setInitialValue(key);
        return preference;
    }

    public boolean setValue(Preference preference, Object newValue) {
        String key = preference.getKey();
        try {
            if (newValue != null) {
                Object previousValue = getValue(preference);
                String newValClass = newValue.getClass().getSimpleName();
                boolean saved = false;

                if (newValClass.equals("Boolean")) {
                    Boolean val = (Boolean) newValue;
                    if (Settings.AMOLED_THEME.key.equals(key)) {
                        return MaterialYouTheme.requestAmoledChange(context, val);
                    }
                    if (Settings.MATERIAL_YOU_THEME.key.equals(key)) {
                        return MaterialYouTheme.requestMaterialYouChange(context, val);
                    }
                    saved = SharedPref.setBooleanPref(key, val);
                } else if (newValClass.equals("String")) {
                    String val = (String) newValue;
                    if(key.contains("_")) {
                        saved = SharedPref.setStringPref(key, val);
                    }else{
                        saved = FlagsSharedPref.setStringPref(key, val);
                    }
                } else if (newValClass.equals("HashSet")) {
                    saved = SharedPref.setSetPref(key, (Set) newValue);
                }

                if (saved) {
                    SettingsRestart.markChanged(previousValue, newValue);
                }
            }
            return true;
        } catch (Exception ex) {
            Utils.showToastShort(ex.toString());
            Logger.printException(() -> "Failed setting pref: ", ex);
            return false;
        }
    }

    private Object getValue(Preference preference) {
        if (preference instanceof SwitchPref) {
            return ((SwitchPref) preference).isChecked();
        }
        if (preference instanceof ListPref) {
            return ((ListPref) preference).getValue();
        }
        if (preference instanceof EditTextPref) {
            return ((EditTextPref) preference).getText();
        }
        if (preference instanceof MultiSelectListPref) {
            return ((MultiSelectListPref) preference).getValues();
        }
        return null;
    }
}
