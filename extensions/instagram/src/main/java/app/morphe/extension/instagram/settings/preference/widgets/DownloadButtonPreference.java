/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.preference.Preference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.BooleanSetting;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.settings.SettingsStatus;

public class DownloadButtonPreference extends MultiSelectListPref {
    private final List<BooleanSetting> settings = new ArrayList<>();

    public DownloadButtonPreference(Context context) {
        super(context);
        // Keep the existing boolean keys for runtime hooks and settings imports/exports.
        setPersistent(false);
        setKey("piko_download_buttons");
        setTitle(str("piko_download_buttons"));
        setDialogTitle(getTitle());

        List<String> entries = new ArrayList<>();
        if (SettingsStatus.feedDownloadButton) {
            settings.add(Settings.FEED_DOWNLOAD_BUTTON);
            entries.add(str("piko_download_buttons_feed"));
        }
        if (SettingsStatus.reelDownloadButton) {
            settings.add(Settings.REEL_DOWNLOAD_BUTTON);
            entries.add(str("piko_navigation_tab_reels"));
        }
        if (SettingsStatus.storyDownloadButton) {
            settings.add(Settings.STORY_DOWNLOAD_BUTTON);
            entries.add(str("piko_download_buttons_stories"));
        }
        setEntries(entries.toArray(new CharSequence[0]));
        CharSequence[] values = new CharSequence[settings.size()];
        for (int index = 0; index < settings.size(); index++) {
            values[index] = settings.get(index).key;
        }
        setEntryValues(values);
        refreshSelection();
        setOnPreferenceChangeListener(this::saveSelection);
    }

    @Override
    protected void onClick() {
        refreshSelection();
        super.onClick();
    }

    private void refreshSelection() {
        Set<String> selected = new HashSet<>();
        for (BooleanSetting setting : settings) {
            if (SharedPref.getBooleanPref(setting)) selected.add(setting.key);
        }
        setValues(selected);
    }

    private boolean saveSelection(Preference preference, Object value) {
        if (!(value instanceof Set<?>)) return false;
        Set<?> selected = (Set<?>) value;
        for (BooleanSetting setting : settings) {
            boolean previous = SharedPref.getBooleanPref(setting);
            boolean enabled = selected.contains(setting.key);
            if (previous == enabled) continue;
            if (!SharedPref.setBooleanPref(setting.key, enabled)) {
                refreshSelection();
                return false;
            }
            SettingsRestart.markChanged(setting.key, previous, enabled);
        }
        return true;
    }
}
