package app.revanced.integrations.twitter.settings.widgets;

import android.content.Context;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.util.AttributeSet;


public class SwitchPref extends SwitchPreference {
    private static Helper helper;

    public SwitchPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }

    public SwitchPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public SwitchPref(Context context, AttributeSet attrs, int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        helper = new Helper(context);
        init();
    }

    private void init() {
        // Set the OnPreferenceChangeListener
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                helper.setValue(preference,newValue);
                return true;
            }
        });
    }
}
