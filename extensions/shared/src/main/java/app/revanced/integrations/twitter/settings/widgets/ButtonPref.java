package app.revanced.integrations.twitter.settings.widgets;


import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.util.AttributeSet;
import app.revanced.integrations.twitter.Utils;
import app.revanced.integrations.twitter.patches.DatabasePatch;
import app.revanced.integrations.twitter.settings.ActivityHook;
import app.revanced.integrations.twitter.settings.Settings;
import app.revanced.integrations.twitter.settings.fragments.BackupPrefFragment;
import app.revanced.integrations.twitter.settings.fragments.RestorePrefFragment;

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
        if (iconName != null) {
            int resId = app.revanced.integrations.shared.Utils.getResourceIdentifier(iconName, "drawable");
            Drawable icon = context.getResources().getDrawable(resId);

            int clr = Color.RED;

            if (!iconName.contains("trashcan")) {
                int[] textColorAttr = {context.getResources().getIdentifier("coreColorPrimaryText", "attr", context.getPackageName())};
                TypedArray typedValue = context.obtainStyledAttributes(textColorAttr);
                clr = typedValue.getColor(0, 0);
                typedValue.recycle();
            }

            icon.setColorFilter(clr, PorterDuff.Mode.SRC_IN);
            setIcon(icon);
        }
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    String key = getKey();
                    Bundle bundle = new Bundle();
                    Fragment fragment = null;
                    if (key.equals(Settings.EXPORT_PREF.key)) {
                        bundle.putBoolean("featureFlag", false);
                        fragment = new BackupPrefFragment();
                    } else if (key.equals(Settings.EXPORT_FLAGS.key)) {
                        bundle.putBoolean("featureFlag", true);
                        fragment = new BackupPrefFragment();
                    } else if (key.equals(Settings.IMPORT_PREF.key)) {
                        bundle.putBoolean("featureFlag", false);
                        fragment = new RestorePrefFragment();
                    } else if (key.equals(Settings.IMPORT_FLAGS.key)) {
                        bundle.putBoolean("featureFlag", true);
                        fragment = new RestorePrefFragment();
                    } else if (key.equals(Settings.PREMIUM_UNDO_POSTS.key)) {
                        Utils.startUndoPostActivity();
                    } else if (key.equals(Settings.PREMIUM_ICONS.key)) {
                        Utils.openUrl("https://www.x.com/settings/app_icon");
                    }  else if (key.equals(Settings.PREMIUM_NAVBAR.key)) {
                        Utils.openUrl("https://www.x.com/settings/custom_navigation");
                    } else if (key.equals(Settings.RESET_PREF.key)) {
                        Utils.deleteSharedPrefAB(context, false);
                    } else if (key.equals(Settings.RESET_FLAGS.key)) {
                        Utils.deleteSharedPrefAB(context, true);
                    } else if (key.equals(Settings.ADS_DEL_FROM_DB.key)) {
                        DatabasePatch.showDialog(context);
                    } else {
                        ActivityHook.startActivity(key);
                    }

                    if (fragment != null) {
                        fragment.setArguments(bundle);
                        ActivityHook.startFragment((Activity) context, fragment, true);
                    }
                } catch (Exception e) {
                    Utils.logger(e);
                    Utils.toast(e.toString());
                }

                return true;
            }
        });
    }

}


