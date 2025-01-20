package app.revanced.integrations.twitter.settings.fragments;

import app.revanced.integrations.shared.StringRef;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import androidx.annotation.Nullable;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.twitter.settings.widgets.Helper;
import app.revanced.integrations.twitter.settings.ScreenBuilder;
import app.revanced.integrations.twitter.settings.Settings;
import app.revanced.integrations.twitter.settings.ActivityHook;
import app.revanced.integrations.twitter.settings.SettingsStatus;
//import android.content.Intent;

@SuppressWarnings("deprecation")
public class PageFragment extends PreferenceFragment {
    private Context context;

//    @Override
//    public void onResume() {
//        super.onResume();
//        ActivityHook.toolbar.setTitle(StringRef.str("piko_title_settings"));
//    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        String toolbarText = "piko_title_settings";

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        preferenceManager.setSharedPreferencesName(Settings.SHARED_PREF_NAME);

        Helper helper = new Helper(context);
        ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

        Bundle bundle = getArguments();
        if (bundle.getBoolean(Settings.PREMIUM_SECTION.key, false)) {
            screenBuilder.buildPremiumSection(false);
            toolbarText = "piko_title_premium";
        }else if (bundle.getBoolean(Settings.DOWNLOAD_SECTION.key, false)) {
            screenBuilder.buildDownloadSection(false);
            toolbarText = "piko_title_download";
        }else if (bundle.getBoolean(Settings.FLAGS_SECTION.key, false)) {
            screenBuilder.buildFeatureFlagsSection(false);
            toolbarText = "piko_title_feature_flags";
        }else if (bundle.getBoolean(Settings.ADS_SECTION.key, false)) {
            screenBuilder.buildAdsSection(false);
            toolbarText = "piko_title_ads";
        }else if (bundle.getBoolean(Settings.MISC_SECTION.key, false)) {
            screenBuilder.buildMiscSection(false);
            toolbarText = "piko_title_misc";
        }else if (bundle.getBoolean(Settings.CUSTOMISE_SECTION.key, false)) {
            screenBuilder.buildCustomiseSection(false);
            toolbarText = "piko_title_customisation";
        }else if (bundle.getBoolean(Settings.TIMELINE_SECTION.key, false)) {
            screenBuilder.buildTimelineSection(false);
            toolbarText = "piko_title_timeline";
        }else if (bundle.getBoolean(Settings.BACKUP_SECTION.key, false)) {
            screenBuilder.buildExportSection(false);
            toolbarText = "piko_title_backup";
        }else if (bundle.getBoolean(Settings.NATIVE_SECTION.key, false)) {
            screenBuilder.buildNativeSection();
            toolbarText = "piko_title_native";
        }

        ActivityHook.toolbar.setTitle(StringRef.str(toolbarText));
//        setSupportActionBar(ActivityHook.toolbar);
        setPreferenceScreen(screen);

    }

}