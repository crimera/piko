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
import android.widget.CompoundButton;
import app.morphe.extension.instagram.settings.preference.Helper;

public class SwitchPref extends SwitchPreference {
    private static Helper helper;
    private View rowView;
    private CompoundButton switchView;
    private boolean clickInProgress;
    private boolean pendingFromChecked;
    private boolean pendingToChecked;
    private boolean switchInteractionEnabled = true;

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
                return helper.setValue(preference,newValue);
            }
        });
    }

    public void setSwitchInteractionEnabled(boolean enabled) {
        if (switchInteractionEnabled == enabled) {
            return;
        }
        switchInteractionEnabled = enabled;
        applySwitchInteractionState(rowView, switchView);
        notifyChanged();
    }

    private void applySwitchInteractionState(
            View currentRow,
            CompoundButton currentSwitch
    ) {
        if (currentRow != null) {
            currentRow.setEnabled(isEnabled() && switchInteractionEnabled);
            currentRow.setAlpha(switchInteractionEnabled ? 1.0f : 0.5f);
        }
        if (currentSwitch != null) {
            currentSwitch.setEnabled(isEnabled());
        }
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
        InstagramPreferenceStyle.bindText(this, view);
        InstagramPreferenceStyle.bindSwitchAccessibility(view, isChecked());
        CompoundButton currentSwitch = InstagramPreferenceStyle.findSwitch(view);
        if (currentSwitch != null) {
            applySwitchInteractionState(view, currentSwitch);
            boolean deferBinding = clickInProgress
                    && currentSwitch == switchView
                    && currentSwitch.isChecked() == pendingFromChecked
                    && isChecked() == pendingToChecked;
            if (!deferBinding && currentSwitch.isChecked() != isChecked()) {
                InstagramPreferenceStyle.setNativeSwitchChecked(
                        currentSwitch,
                        isChecked(),
                        false
                );
            }
            switchView = currentSwitch;
        }
    }

    @Override
    protected void onClick() {
        if (!InstagramPreferenceStyle.consumeSwitchClickAllowed(rowView)
                || !switchInteractionEnabled) {
            return;
        }

        boolean wasChecked = isChecked();
        pendingFromChecked = wasChecked;
        pendingToChecked = !wasChecked;
        clickInProgress = true;
        try {
            super.onClick();
        } finally {
            clickInProgress = false;
        }

        if (wasChecked == isChecked()) {
            return;
        }

        if (rowView != null) {
            InstagramPreferenceStyle.bindSwitchAccessibility(rowView, isChecked());
        }

        if (switchView != null) {
            InstagramPreferenceStyle.setNativeSwitchChecked(
                    switchView,
                    isChecked(),
                    switchView.isChecked() == wasChecked
            );
        }
    }

}
