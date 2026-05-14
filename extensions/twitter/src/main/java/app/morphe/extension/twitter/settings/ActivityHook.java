/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.view.View;
import android.view.Window;
import androidx.appcompat.widget.Toolbar;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.settings.featureflags.FeatureFlagsFragment;
import app.morphe.extension.twitter.settings.fragments.*;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeFragment;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;

@SuppressWarnings("deprecation")
public class ActivityHook {
    public static Toolbar toolbar;
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_SETTINGS = EXTRA_PIKO + "_settings";

    public static boolean create(Activity act) {
        Intent intent = act.getIntent();
        if (!(intent.getBooleanExtra(EXTRA_PIKO, false))) return false;

        Bundle bundle = intent.getExtras();
        Window window = act.getWindow();

        if (Build.VERSION.SDK_INT >= 35) {
            int uiMode = act.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                window.getDecorView().setSystemUiVisibility(window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }

            window.getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                int statusBarHeight = insets.getSystemWindowInsetTop();
                int navBarHeight = insets.getSystemWindowInsetBottom();

                window.getDecorView().setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), navBarHeight);

                return insets.consumeSystemWindowInsets();
            });
        }

        Fragment fragment = null;
        boolean addToBackStack = false;
        String activity_name = bundle != null ? bundle.getString(Settings.ACT_NAME) : null;

        if (activity_name.equals(EXTRA_PIKO_SETTINGS)) {
            fragment = new SettingsFragment();
        } else if (activity_name.equals( Settings.FEATURE_FLAGS) ){
            fragment = new FeatureFlagsFragment();
        } else if (activity_name.equals( Settings.PATCH_INFO)) {
            fragment = new SettingsAboutFragment();
        }else if (activity_name.equals( Settings.READER_MODE_KEY) ){
            fragment = new ReaderModeFragment();
        }  else {
            fragment = new PageFragment();
        }

        if (fragment != null) {
            fragment.setArguments(bundle);
            startFragment(act,activity_name, fragment, addToBackStack);
            return true;
        }
        return false;
    }

    public static void startFragment(Activity act, String activity_name, Fragment fragment, boolean addToBackStack) {
        act.setContentView(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "preference_fragment_activity"));
        toolbar = act.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "toolbar"));
        toolbar.setNavigationIcon(ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_arrow_left"));
        toolbar.setTitle(getTitle(activity_name));
        toolbar.setNavigationOnClickListener(view -> act.onBackPressed());

        FragmentTransaction transaction = act.getFragmentManager().beginTransaction().replace(
                ResourceUtils.getIdentifier(ResourceType.ID, "fragment_container"), fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private static String getTitle(String activity_name){
        String toolbarText = switch (activity_name) {
            case Settings.PREMIUM_SECTION -> "piko_title_premium";
            case Settings.DOWNLOAD_SECTION -> "piko_title_download";
            case Settings.FLAGS_SECTION -> "piko_title_feature_flags";
            case Settings.ADS_SECTION -> "piko_title_ads";
            case Settings.MISC_SECTION -> "piko_title_misc";
            case Settings.CUSTOMISE_SECTION -> "piko_title_customisation";
            case Settings.FONT_SECTION -> "piko_title_font";
            case Settings.TIMELINE_SECTION -> "piko_title_timeline";
            case Settings.BACKUP_SECTION -> "piko_title_backup";
            case Settings.NATIVE_SECTION -> "piko_title_native";
            case Settings.LOGGING_SECTION -> "piko_title_logging";
            case Settings.READER_MODE_KEY -> "piko_title_native_reader_mode";
            case Settings.CHANGE_APP_ICON -> "piko_pref_customisation_change_app_icon";
            case Settings.EXPORT_LOGIN_TOKEN -> "piko_pref_export_login_token";
            default -> "piko_title_settings";
        };
        return ResourceUtils.getString(toolbarText);
    }

    public static void startActivity(String activity_name, Bundle bundle) throws Exception {
        Intent intent = new Intent(Utils.getContext(), Class.forName(
                "com.twitter.android.AuthorizeAppActivity"));
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utils.getContext().startActivity(intent);
    }

    public static void startActivity(String activity_name) throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        startActivity(activity_name, bundle);
    }

    public static void startReaderMode(String tweetId) throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(ReaderModeUtils.ARG_TWEET_ID, tweetId);
        startActivity(Settings.READER_MODE_KEY, bundle);
    }

    public static void startSettingsActivity() throws Exception {
        startActivity(EXTRA_PIKO_SETTINGS);
    }
}
