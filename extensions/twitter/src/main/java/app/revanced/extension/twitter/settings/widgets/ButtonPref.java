package app.revanced.extension.twitter.settings.widgets;


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
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.twitter.patches.DatabasePatch;
import app.revanced.extension.twitter.settings.ActivityHook;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.twitter.settings.fragments.BackupPrefFragment;
import app.revanced.extension.twitter.settings.fragments.RestorePrefFragment;
import app.revanced.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;


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
            int resId = app.revanced.extension.shared.Utils.getResourceIdentifier(iconName, "drawable");
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
                    if (key.equals(Settings.EXPORT_PREF)) {
                        bundle.putBoolean("featureFlag", false);
                        fragment = new BackupPrefFragment();
                    } else if (key.equals(Settings.EXPORT_FLAGS)) {
                        bundle.putBoolean("featureFlag", true);
                        fragment = new BackupPrefFragment();
                    } else if (key.equals(Settings.IMPORT_PREF)) {
                        bundle.putBoolean("featureFlag", false);
                        fragment = new RestorePrefFragment();
                    } else if (key.equals(Settings.IMPORT_FLAGS)) {
                        bundle.putBoolean("featureFlag", true);
                        fragment = new RestorePrefFragment();
                    } else if (key.equals(Settings.PREMIUM_UNDO_POSTS)) {
                        Utils.startUndoPostActivity();
                    } else if (key.equals(Settings.PREMIUM_ICONS)) {
                        Utils.openUrl("https://www.x.com/settings/app_icon");
                    }  else if (key.equals(Settings.PREMIUM_NAVBAR.key)) {
                        Utils.openUrl("https://www.x.com/settings/custom_navigation");
                    } else if (key.equals(Settings.RESET_PREF)) {
                        Utils.deleteSharedPrefAB(context, false);
                    } else if (key.equals(Settings.RESET_FLAGS)) {
                        Utils.deleteSharedPrefAB(context, true);
                    } else if (key.equals(Settings.ADS_DEL_FROM_DB)) {
                        DatabasePatch.showDialog(context);
                    }else if (key.equals(Settings.RESET_READER_MODE_CACHE)) {
                        ReaderModeUtils.clearCache();
                    } else {
                        ActivityHook.startActivity(key);
                    }

                    if (fragment != null) {
                        fragment.setArguments(bundle);
                        ActivityHook.startFragment((Activity) context, key,fragment, true);
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


