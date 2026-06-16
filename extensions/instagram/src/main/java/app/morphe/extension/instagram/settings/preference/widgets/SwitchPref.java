/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.preference.SwitchPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.instagram.settings.preference.Helper;

public class SwitchPref extends SwitchPreference {
    private static Helper helper;
    private View rowView;
    private InstagramPreferenceStyle.SwitchView switchView;
    private boolean hasPendingAnimation;
    private boolean pendingFromChecked;
    private boolean pendingToChecked;

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

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = InstagramPreferenceStyle.createPreferenceView(getContext(), InstagramPreferenceStyle.TRAILING_SWITCH);
        rowView = view;
        switchView = InstagramPreferenceStyle.findSwitch(view);
        return view;
    }

    @Override
    protected void onBindView(View view) {
        rowView = view;
        Object previousKey = view.getTag();
        boolean samePreference = getKey() != null && getKey().equals(previousKey);
        view.setTag(getKey());
        InstagramPreferenceStyle.bindText(this, view);
        InstagramPreferenceStyle.bindSwitchAccessibility(view, isChecked());
        InstagramPreferenceStyle.SwitchView currentSwitch = InstagramPreferenceStyle.findSwitch(view);
        if (currentSwitch != null) {
            currentSwitch.setEnabled(isEnabled());
            if (hasPendingAnimation && isChecked() == pendingToChecked) {
                currentSwitch.setChecked(pendingFromChecked, false);
                currentSwitch.setChecked(pendingToChecked, true);
                hasPendingAnimation = false;
            } else if (!currentSwitch.isAnimating() || !samePreference) {
                currentSwitch.setChecked(isChecked(), false);
            } else {
                currentSwitch.setEnabled(isEnabled());
            }
            switchView = currentSwitch;
        }
    }

    @Override
    protected void onClick() {
        if (!InstagramPreferenceStyle.consumeSwitchClickAllowed(rowView)) {
            hasPendingAnimation = false;
            return;
        }

        boolean wasChecked = isChecked();
        hasPendingAnimation = true;
        pendingFromChecked = wasChecked;
        pendingToChecked = !wasChecked;
        super.onClick();

        if (wasChecked == isChecked()) {
            hasPendingAnimation = false;
            return;
        }

        if (rowView != null) {
            InstagramPreferenceStyle.bindSwitchAccessibility(rowView, isChecked());
        }

        if (hasPendingAnimation && switchView != null) {
            switchView.setChecked(wasChecked, false);
            switchView.setChecked(isChecked(), true);
            hasPendingAnimation = false;
        }
    }
}
