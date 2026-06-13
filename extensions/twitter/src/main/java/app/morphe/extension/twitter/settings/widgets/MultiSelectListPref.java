/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.widgets;

import android.content.Context;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.util.AttributeSet;

import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.settings.Settings;


public class MultiSelectListPref extends MultiSelectListPreference {
    private static Helper helper;
    private static final Map<String, String> SETTINGS_RESOURCE_MAP = new HashMap<>();

    static {
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_PROFILE_TABS.key, "piko_array_profiletabs");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_SIDEBAR_TABS.key, "piko_array_sidebar");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_NAVBAR_TABS.key, "piko_array_navbar");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_INLINE_TABS.key, "piko_array_inlinetabs");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_EXPLORE_TABS.key, "piko_array_exploretabs");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_SEARCH_TYPE_AHEAD.key, "piko_array_search_type_ahead");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_SEARCH_TABS.key, "piko_array_searchtabs");
        SETTINGS_RESOURCE_MAP.put(Settings.CUSTOM_NOTIFICATION_TABS.key, "piko_array_notificationtabs");
    }

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
        String resourceBaseName = SETTINGS_RESOURCE_MAP.get(key);
        if (resourceBaseName != null) {
            setEntries(ResourceUtils.getStringArray(resourceBaseName));
            setEntryValues(ResourceUtils.getStringArray(resourceBaseName + "_val"));
        } else {
            setEntries(new CharSequence[]{});
            setEntryValues(new CharSequence[]{});
        }
    }
}
