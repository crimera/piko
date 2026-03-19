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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.util.AttributeSet;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.constants.Strings;

public class ButtonPref extends Preference {
    private final Context context;
    private String iconName;


    public ButtonPref(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public ButtonPref(Context context, String iconName) {
        super(context);
        this.context = context;
        this.iconName = iconName;
        init();
    }

    public ButtonPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public ButtonPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }


    private void init() {

        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
            try {
                String key = getKey();
                if (key.equals(Strings.EXPORT_DEV_OVERRIDES) || key.equals(Strings.IMPORT_DEV_OVERRIDES) || key.equals(Strings.IMPORT_ID_MAPPING)) {
                    ActivityHook.callFragment((Activity) context, key);
                }
            } catch (Exception e) {
                Utils.showToastShort(e.getMessage());
                Logger.printException(() -> "Preference button onclick failure", e);
            }
            return true;
            }
        });
    }

}


