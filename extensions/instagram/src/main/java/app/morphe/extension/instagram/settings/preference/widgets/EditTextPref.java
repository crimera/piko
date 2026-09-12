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
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import java.util.Arrays;
import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.Helper;

public class EditTextPref extends EditTextPreference {
    private static final InputFilter SINGLE_LINE_FILTER = (source, start, end, dest, dstart, dend) -> {
        CharSequence input = source.subSequence(start, end);
        String sanitized = removeLineBreaks(input.toString());
        return sanitized.contentEquals(input) ? null : sanitized;
    };
    private static Helper helper;

    private static String removeLineBreaks(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    public EditTextPref(Context context) {
        super(InstagramPreferenceStyle.dialogContext(context));
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
        getEditText().setSingleLine(true);
        InputFilter[] filters = getEditText().getFilters();
        InputFilter[] singleLineFilters = Arrays.copyOf(filters, filters.length + 1);
        singleLineFilters[filters.length] = SINGLE_LINE_FILTER;
        getEditText().setFilters(singleLineFilters);

        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                helper.setValue(preference,newValue);
                //TODO: Implement better soution for summary.
                String summary = (String) newValue;
                if (Settings.CUSTOM_SHARING_DOMAIN.key.equals(preference.getKey())) {
                    summary = Links.customSharingDomainSummary(summary);
                }
                preference.setSummary(summary);
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
