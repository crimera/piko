/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

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

abstract class StoryNumberPreference extends EditTextPref implements TextWatcher {
    StoryNumberPreference(Context context, StringSetting setting, String title) {
        super(context);
        setKey(setting.key);
        setTitle(title);
        setDialogTitle(title);
        setNumericOnly(true);
        getEditText().setHint(setting.defaultValue);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        getEditText().addTextChangedListener(this);
        afterTextChanged(getEditText().getText());
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable text) {
        boolean canSave = valueForSave(text.toString()) != null;
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(canSave);
    }

    abstract String valueForSave(String input);

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        getEditText().removeTextChangedListener(this);
        if (positiveResult) {
            String value = valueForSave(getEditText().getText().toString());
            if (value == null) {
                super.onDialogClosed(false);
                return;
            }
            getEditText().setText(value);
        }
        super.onDialogClosed(positiveResult);
    }
}
