/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import app.morphe.extension.instagram.settings.preference.Helper;

public class SwitchPref extends SwitchPreference {
    private static Helper helper;

    public SwitchPref(Context context) {
        super(context);
        this.helper = new Helper(context);
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
