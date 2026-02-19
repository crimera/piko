package app.morphe.extension.twitter.settings.fragments;

import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import androidx.annotation.Nullable;

import app.morphe.extension.twitter.settings.widgets.Helper;
import app.morphe.extension.twitter.settings.ScreenBuilder;
import app.morphe.extension.twitter.settings.Settings;

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
        String activity_name = bundle != null ? bundle.getString(Settings.ACT_NAME) : null;
        if (activity_name.equals(Settings.PREMIUM_SECTION)) {
            screenBuilder.buildPremiumSection(false);
        }else if (activity_name.equals(Settings.DOWNLOAD_SECTION)) {
            screenBuilder.buildDownloadSection(false);
        }else if (activity_name.equals(Settings.FLAGS_SECTION)) {
            screenBuilder.buildFeatureFlagsSection(false);
        }else if (activity_name.equals(Settings.ADS_SECTION)) {
            screenBuilder.buildAdsSection(false);
        }else if (activity_name.equals(Settings.MISC_SECTION)) {
            screenBuilder.buildMiscSection(false);
        }else if (activity_name.equals(Settings.CUSTOMISE_SECTION)) {
            screenBuilder.buildCustomiseSection(false);
        }else if (activity_name.equals(Settings.FONT_SECTION)) {
            screenBuilder.buildFontSection(false);
        }else if (activity_name.equals(Settings.TIMELINE_SECTION)) {
            screenBuilder.buildTimelineSection(false);
        }else if (activity_name.equals(Settings.BACKUP_SECTION)) {
            screenBuilder.buildExportSection(false);
        }else if (activity_name.equals(Settings.NATIVE_SECTION)) {
            screenBuilder.buildNativeSection(false);
        }else if (activity_name.equals(Settings.LOGGING_SECTION)) {
            screenBuilder.buildLoggingSection(false);
        }
//        setSupportActionBar(ActivityHook.toolbar);
        setPreferenceScreen(screen);

    }

}