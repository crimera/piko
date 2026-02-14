package app.revanced.extension.twitter.settings.widgets;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.preference.Preference;
import android.text.InputType;

public class EditTextPref extends EditTextPreference {
    private static Helper helper;
    private boolean numericOnly = false;

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
        this.numericOnly = numericOnly;
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
}