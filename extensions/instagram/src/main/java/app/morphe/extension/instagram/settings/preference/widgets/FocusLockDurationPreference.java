/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.graphics.PorterDuff;
import android.preference.Preference;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import app.morphe.extension.instagram.patches.focusLock.FocusLockDuration;

/**
 * The lock duration, as a slider sitting in the screen rather than behind a dialog.
 *
 * The steps are the ones in {@link FocusLockDuration}, which run from a minute to a few months,
 * so the slider moves in even notches while the time it represents grows quickly.
 */
public class FocusLockDurationPreference extends Preference {

    private TextView titleView;
    private TextView valueView;
    private SeekBar seekBar;

    public FocusLockDurationPreference(Context context) {
        super(context);
        // The value lives in its own preference, written when the slider settles.
        setPersistent(false);
        setSelectable(false);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(
                InstagramPreferenceStyle.dp(context, 17),
                InstagramPreferenceStyle.dp(context, 14),
                InstagramPreferenceStyle.dp(context, 17),
                InstagramPreferenceStyle.dp(context, 14)
        );
        row.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.addView(valueView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        seekBar = new SeekBar(context);
        seekBar.setMax(FocusLockDuration.stepCount() - 1);
        seekBar.setPadding(
                InstagramPreferenceStyle.dp(context, 4),
                InstagramPreferenceStyle.dp(context, 10),
                InstagramPreferenceStyle.dp(context, 4),
                0
        );
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) showValue(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                // Written once the slider settles, rather than on every notch it passes.
                FocusLockDuration.setStep(bar.getProgress());
                showValue(bar.getProgress());
            }
        });
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return row;
    }

    private void showValue(int step) {
        if (valueView != null) valueView.setText(FocusLockDuration.labelForStep(step));
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        boolean enabled = isEnabled();
        int titleColor = enabled
                ? InstagramPreferenceStyle.primaryTextColor()
                : InstagramPreferenceStyle.disabledTextColor();
        int valueColor = enabled
                ? InstagramPreferenceStyle.selectionColor()
                : InstagramPreferenceStyle.disabledTextColor();

        if (titleView != null) {
            titleView.setText(getTitle());
            titleView.setTextColor(titleColor);
        }
        if (valueView != null) {
            valueView.setTextColor(valueColor);
        }
        if (seekBar != null) {
            seekBar.setEnabled(enabled);
            seekBar.setProgress(FocusLockDuration.currentStep());
            seekBar.getProgressDrawable().setColorFilter(
                    valueColor, PorterDuff.Mode.SRC_IN
            );
            if (seekBar.getThumb() != null) {
                seekBar.getThumb().setColorFilter(valueColor, PorterDuff.Mode.SRC_IN);
            }
            showValue(seekBar.getProgress());
        }
        view.setEnabled(enabled);
    }
}
