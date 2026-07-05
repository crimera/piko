/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.util.AttributeSet;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.Helper;


public class MultiSelectListPref extends MultiSelectListPreference {
    private static Helper helper;

    public MultiSelectListPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }
    
    public MultiSelectListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public MultiSelectListPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        helper = new Helper(context);
        init();
    }

    private void init() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                helper.setValue(preference,newValue);
                return true;
            }
        });
    }
    public void setInitialValue(String key) {
        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.ACTION_BAR_MAIN_FEED.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_main_feed");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_main_feed_val");
        }
        else if (key == Settings.ACTION_BAR_USER_PROFILE.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_user_profile");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_user_profile_val");
        }
        else if (key == Settings.ACTION_BAR_CHAT.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_chat");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_user_profile_val");
        }
        else if (key == Settings.ACTION_BAR_INBOX.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_inbox");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_inbox_val");
        }
        setEntries(entries);
        setEntryValues(entriesValues);
    }
}
