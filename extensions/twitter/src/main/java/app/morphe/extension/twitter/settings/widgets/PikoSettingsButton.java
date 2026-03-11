/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.settings.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.appcompat.widget.AppCompatButton;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.ActivityHook;

public class PikoSettingsButton extends AppCompatButton implements View.OnClickListener {

    // Constructors
    public PikoSettingsButton(Context context) {
        super(context);
        init();
    }

    public PikoSettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PikoSettingsButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // Initialization method
    private void init() {
        setOnClickListener(this);
        if (!Utils.getBooleanPerf(Settings.MISC_QUICK_SETTINGS_BUTTON)) {
            setVisibility(GONE);
        }
    }

    // Override the onClick method
    @Override
    public void onClick(View v) {
        try {
            ActivityHook.startSettingsActivity();
        } catch (Exception e) {
            Utils.logger(e);
        }

    }
}
