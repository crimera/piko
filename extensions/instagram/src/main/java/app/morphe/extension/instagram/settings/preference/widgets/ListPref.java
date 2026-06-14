/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.instagram.settings.Settings;
import android.preference.Preference;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.constants.Arrays;

public class ListPref extends ListPreference {
    private static Helper helper;

    public ListPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }

    public ListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public ListPref(Context context, AttributeSet attrs, int defStyleAttr) {
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

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        String key = getKey();

        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.CUSTOMISE_STORY_TIMESTAMP.key) {
            entries = Arrays.CUSTOMISE_STORY_TIMESTAMP_KEY;
            entriesValues = Arrays.CUSTOMISE_STORY_TIMESTAMP_VAL;
        } else if (key == Settings.CHANGE_LIKE_ANIMATION.key) {
            entries = Arrays.CHANGE_LIKE_ANIMATION_KEY;
            entriesValues = Arrays.CHANGE_LIKE_ANIMATION_VAL;
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
