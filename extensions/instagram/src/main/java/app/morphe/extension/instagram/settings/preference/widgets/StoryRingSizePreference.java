/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.Pref;

public final class StoryRingSizePreference extends StoryNumberPreference {
    public StoryRingSizePreference(Context context, String title, String description) {
        super(context, Settings.CUSTOMISE_STORY_RING_SIZE, title);
        setSummary(description);
        setDefaultValue(Settings.CUSTOMISE_STORY_RING_SIZE.defaultValue);
    }

    @Override
    public String getText() {
        String value = super.getText();
        return Pref.parseStoryRingSize(value) >= 1 ? value : Settings.CUSTOMISE_STORY_RING_SIZE.defaultValue;
    }

    @Override
    String valueForSave(String input) {
        if (input.isEmpty()) return Settings.CUSTOMISE_STORY_RING_SIZE.defaultValue;
        return Pref.parseStoryRingSize(input) >= 1 ? input : null;
    }
}
