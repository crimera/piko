/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.StringSetting;
import app.morphe.extension.instagram.utils.Pref;

public final class StoryItemCountPreference extends StoryNumberPreference {
    private final StringSetting setting;
    private final String description;

    public StoryItemCountPreference(Context context, StringSetting setting, String title, String description) {
        super(context, setting, title);
        this.setting = setting;
        this.description = description;
    }

    @Override
    public CharSequence getSummary() {
        return description;
    }

    @Override
    public String getText() {
        return Integer.toString(Pref.storyItemCount(setting));
    }

    @Override
    String valueForSave(String input) {
        int count = Pref.parseStoryItemCount(input.isEmpty() ? setting.defaultValue : input, -1);
        if (count < 1) return null;

        if (setting == Settings.FILTER_STORY_MIN_STORY_ITEMS) {
            if (count > Pref.storyItemCount(Settings.FILTER_STORY_MAX_STORY_ITEMS)) return null;
        } else if (count < Pref.storyItemCount(Settings.FILTER_STORY_MIN_STORY_ITEMS)) {
            return null;
        }
        String value = Integer.toString(count);
        return value.equals(setting.defaultValue) ? "" : value;
    }
}
