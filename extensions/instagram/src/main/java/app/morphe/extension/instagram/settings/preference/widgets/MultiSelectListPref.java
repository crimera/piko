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
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.widgets.NavigationSettingsAccessPolicy.ActionBar;
import app.morphe.extension.instagram.utils.Pref;

import java.util.HashSet;
import java.util.Set;


public class MultiSelectListPref extends MultiSelectListPreference {
    private static Helper helper;

    public MultiSelectListPref(Context context) {
        super(InstagramPreferenceStyle.dialogContext(context));
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
                ActionBar changed = actionBarForKey(preference.getKey());
                HashSet<String> proposed = changed == null ? null : copyStringSet(newValue);
                if (changed != null && needsSettingsLockoutWarning(changed, proposed)) {
                    NavigationSettingsAccessWarning.show(getContext(), () -> {
                        if (proposed == null) return false;
                        if (!helper.setValue(preference, proposed)) return false;
                        setValues(proposed);
                        return true;
                    });
                    return false;
                }
                helper.setValue(preference,newValue);
                return true;
            }
        });
    }

    private static boolean needsSettingsLockoutWarning(
            ActionBar changed,
            Set<String> proposed
    ) {
        try {
            return proposed == null || !NavigationSettingsAccessPolicy
                    .hasDirectAccessAfterActionBarChange(
                            NavigationBarPatch.loadConfig().visible(),
                            changed,
                            proposed.contains(Constants.AB_SETTINGS_ICON)
                    );
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static ActionBar actionBarForKey(String key) {
        if (Settings.ACTION_BAR_MAIN_FEED.key.equals(key)) return ActionBar.MAIN_FEED;
        if (Settings.ACTION_BAR_USER_PROFILE.key.equals(key)) return ActionBar.PROFILE;
        if (Settings.ACTION_BAR_INBOX.key.equals(key)) return ActionBar.INBOX;
        if (Settings.ACTION_BAR_CHAT.key.equals(key)) return ActionBar.CHAT;
        return null;
    }

    private static HashSet<String> copyStringSet(Object value) {
        if (!(value instanceof Set<?>)) return null;

        HashSet<String> copy = new HashSet<>();
        for (Object entry : (Set<?>) value) {
            if (!(entry instanceof String)) return null;
            copy.add((String) entry);
        }
        return copy;
    }

    public void setInitialValue(String key) {
        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        // Migrate legacy action visibility before the preference reads its saved selection.
        if (key == Settings.ACTION_BAR_MAIN_FEED.key) {
            Pref.mainFeedActionBarButtons();
            entries = ResourceUtils.getStringArray("piko_array_action_bar_main_feed");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_main_feed_val");
        }
        else if (key == Settings.ACTION_BAR_USER_PROFILE.key) {
            Pref.userProfileActionBarButtons();
            entries = ResourceUtils.getStringArray("piko_array_action_bar_user_profile");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_user_profile_val");
        }
        else if (key == Settings.ACTION_BAR_CHAT.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_chat");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_chat_val");
        }
        else if (key == Settings.ACTION_BAR_INBOX.key) {
            entries = ResourceUtils.getStringArray("piko_array_action_bar_inbox");
            entriesValues = ResourceUtils.getStringArray("piko_array_action_bar_inbox_val");
        }
        else if (key == Settings.FILTER_STORY_BY_TYPE.key) {
            entries = ResourceUtils.getStringArray("piko_array_reel_type");
            entriesValues = ResourceUtils.getStringArray("piko_array_reel_type_val");
        }
        else if (key == Settings.FILTER_STORY_BY_USER_TYPE.key) {
            entries = ResourceUtils.getStringArray("piko_array_user_type");
            entriesValues = ResourceUtils.getStringArray("piko_array_user_type_val");
        }
        setEntries(entries);
        setEntryValues(entriesValues);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return InstagramPreferenceStyle.createPreferenceView(getContext(), InstagramPreferenceStyle.TRAILING_CHEVRON);
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
    }
}
