/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.preference.Preference;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.instagram.settings.preference.Helper;

public class EditTextPref extends EditTextPreference {
    private static Helper helper;

    public EditTextPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }
    public EditTextPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public EditTextPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        helper = new Helper(context);
        init();
    }
    public void setNumericOnly(boolean numericOnly) {
        if (numericOnly) {
            getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
            getEditText().setSingleLine(true);
        } else {
            getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
        }
    }

    private void init() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                helper.setValue(preference,newValue);
                preference.setSummary((String) newValue);
                return true;
            }
        });
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
