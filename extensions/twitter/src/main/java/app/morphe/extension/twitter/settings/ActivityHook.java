/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;

import androidx.appcompat.widget.Toolbar;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.settings.featureflags.FeatureFlagsFragment;
import app.morphe.extension.twitter.settings.fragments.*;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeFragment;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;

@SuppressWarnings("deprecation")
public class ActivityHook {
    @SuppressLint("StaticFieldLeak")
    public static Toolbar toolbar;
    private static final String AUTHORIZE_ACTIVITY_CLASS = "com.twitter.android.AuthorizeAppActivity";
    private static final String EXTRA_PIKO = "piko";
    private static final String EXTRA_PIKO_SETTINGS = EXTRA_PIKO + "_settings";
    public static final String EXTRA_SETTINGS_SEARCH_TARGET_KEY = EXTRA_PIKO + "_settings_search_target_key";
    public static final String EXTRA_SETTINGS_SEARCH_TARGET_TITLE = EXTRA_PIKO + "_settings_search_target_title";
    public static final String EXTRA_SETTINGS_SEARCH_TARGET_SUMMARY = EXTRA_PIKO + "_settings_search_target_summary";
    private static final String PIKO_PREF_KEY = "pref_mod";

    public static Context getPreferenceContext(Context context) {
        String theme = app.morphe.extension.twitter.Utils.getTheme();
        int style = ("dark".equals(theme) || "dim".equals(theme))
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
        return new ContextThemeWrapper(context, style);
    }

    public static boolean create(Activity act) {
        Intent intent = act.getIntent();
        if (!(intent.getBooleanExtra(EXTRA_PIKO, false))) return false;

        Bundle bundle = intent.getExtras();
        Window window = act.getWindow();

        if (Build.VERSION.SDK_INT >= 35) {
            View decorView = window.getDecorView();
            int uiMode = act.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            } else {
                decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }

            decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                int statusBarHeight = insets.getSystemWindowInsetTop();
                int navBarHeight = insets.getSystemWindowInsetBottom();

                window.getDecorView().setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), navBarHeight);

                return insets.consumeSystemWindowInsets();
            });
        }

        Fragment fragment;
        boolean addToBackStack = false;
        String activity_name = bundle != null ? bundle.getString(Settings.ACT_NAME) : null;

        if (EXTRA_PIKO_SETTINGS.equals(activity_name)) {
            fragment = new SettingsFragment();
        } else if (Settings.FEATURE_FLAGS.equals(activity_name)) {
            fragment = new FeatureFlagsFragment();
        } else if (Settings.PATCH_INFO.equals(activity_name)) {
            fragment = new SettingsAboutFragment();
        } else if (Settings.READER_MODE_KEY.equals(activity_name)) {
            fragment = new ReaderModeFragment();
        } else {
            fragment = new PageFragment();
        }

        fragment.setArguments(bundle);
        startFragment(act, activity_name, fragment, addToBackStack);
        return true;
    }

    static void clearToolbarIfOwnedBy(Toolbar candidate) {
        if (toolbar == candidate) {
            toolbar = null;
        }
    }

    public static void startFragment(Activity act, String activity_name, Fragment fragment, boolean addToBackStack) {
        act.setContentView(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "preference_fragment_activity"));
        toolbar = act.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "toolbar"));
        toolbar.setNavigationIcon(ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_arrow_left"));
        toolbar.setTitle(getTitle(activity_name));
        toolbar.setNavigationOnClickListener(view -> act.onBackPressed());

        int fragmentContainerId = ResourceUtils.getIdentifier(ResourceType.ID, "fragment_container");
        if (EXTRA_PIKO_SETTINGS.equals(activity_name)) {
            SettingsSearchUIController.install(act, toolbar, fragmentContainerId);
        }

        FragmentTransaction transaction = act.getFragmentManager().beginTransaction().replace(
                fragmentContainerId, fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private static String getTitle(String activity_name){
        if (activity_name == null) {
            return ResourceUtils.getString("piko_title_settings");
        }
        String sectionTitle = ScreenBuilder.getSectionTitleResourceName(activity_name);
        if (sectionTitle != null) {
            return ResourceUtils.getString(sectionTitle);
        }
        String toolbarText = switch (activity_name) {
            case Settings.READER_MODE_KEY -> "piko_title_native_reader_mode";
            case Settings.CHANGE_APP_ICON -> "piko_pref_customisation_change_app_icon";
            case Settings.EXPORT_LOGIN_TOKEN -> "piko_pref_export_login_token";
            default -> "piko_title_settings";
        };
        return ResourceUtils.getString(toolbarText);
    }

    public static void startActivity(String activity_name, Bundle bundle) {
        Context context = Utils.getContext();
        String packageName = context.getPackageName();
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        Intent intent = new Intent();
        intent.setClassName(packageName, AUTHORIZE_ACTIVITY_CLASS);
        intent.setPackage(packageName);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void startActivity(String activity_name) {
        Bundle bundle = new Bundle();
        bundle.putString(Settings.ACT_NAME, activity_name);
        bundle.putBoolean(EXTRA_PIKO, true);
        startActivity(activity_name, bundle);
    }

    public static void startReaderMode(String tweetId) {
        Bundle bundle = new Bundle();
        bundle.putString(ReaderModeUtils.ARG_TWEET_ID, tweetId);
        startActivity(Settings.READER_MODE_KEY, bundle);
    }

    public static void startSettingsActivity() {
        startActivity(EXTRA_PIKO_SETTINGS);
    }

    @SuppressWarnings("unused")
    public static boolean startSettingsActivity(String key) {
        if (PIKO_PREF_KEY.equals(key)) {
            try {
                startSettingsActivity();
                return true;
            } catch (Exception ex) {
                Logger.printException(() -> "startSettingsActivity failed", ex);
            }
        }

        return false;
    }
}
