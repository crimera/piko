/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.widgets;

import android.content.Context;
import android.graphics.Color;
import android.preference.Preference;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.Nullable;

import java.util.Set;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;
import app.morphe.extension.twitter.Utils;

public class Helper {
    private final Context context;

    public Helper(Context context) {
        this.context = context;
    }


    public Preference editTextPreference(String title, String summary, StringSetting setting) {
        EditTextPref preference = new EditTextPref(context);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        preference.setSingleLineTitle(false);
        return preference;
    }

    public Preference editTextNumPreference(String title, String summary, StringSetting setting) {
        EditTextPref preference = (EditTextPref)editTextPreference(title,summary,setting);
        preference.setNumericOnly(true);
        preference.setSingleLineTitle(false);
        return preference;
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

    public Preference buttonPreference(String title, String summary, String setting) {
        ButtonPref preference = new ButtonPref(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting);
        preference.setSingleLineTitle(false);
        return preference;
    }

    public Preference buttonPreference(String title, String summary, String setting, @Nullable String iconName, @Nullable String color) {
        ButtonPref preference = new ButtonPref(context, iconName);

        if (color != null) {
            Spannable span = new SpannableString(title);
            span.setSpan(new ForegroundColorSpan(Color.parseColor(color)), 0, title.length(), 0);
            preference.setTitle(span);
        } else {
            preference.setTitle(title);
        }
        preference.setSummary(summary);
        preference.setKey(setting);
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

    public Preference multiSelectListPref(String title, String summary, StringSetting setting) {
        MultiSelectListPref preference = new MultiSelectListPref(context);
        String key = setting.key;
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(key);
        preference.setInitialValue(key);
        preference.setSingleLineTitle(false);
        return preference;
    }

    public void setValue(Preference preference, Object newValue) {
        String key = preference.getKey();
        try {
            if (newValue != null) {
                String newValClass = newValue.getClass().getSimpleName();

                if (newValClass.equals("Boolean")) {
                    Utils.setBooleanPref(key, (Boolean) newValue);
                } else if (newValClass.equals("String")) {
                    Utils.setStringPref(key, (String) newValue);
                } else if (newValClass.equals("HashSet")) {
                    Utils.setSetPref(key, (Set) newValue);
                }
            }

        } catch (Exception ex) {
            app.morphe.extension.crimera.PikoUtils.toast(ex.toString());
            app.morphe.extension.crimera.PikoUtils.logger(ex);
        }
    }


}