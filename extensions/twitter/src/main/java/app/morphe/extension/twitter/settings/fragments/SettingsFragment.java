package app.morphe.extension.twitter.settings.fragments;

import app.morphe.extension.shared.StringRef;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import androidx.annotation.Nullable;
import app.morphe.extension.twitter.settings.widgets.Helper;
import app.morphe.extension.twitter.settings.ScreenBuilder;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.ActivityHook;

@SuppressWarnings("deprecation")
public class SettingsFragment extends PreferenceFragment {
    private Context context;

    @Override
    public void onResume() {
        super.onResume();
        ActivityHook.toolbar.setTitle(StringRef.str("piko_title_settings"));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        preferenceManager.setSharedPreferencesName(Settings.SHARED_PREF_NAME);

        Helper helper = new Helper(context);
        ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

        boolean isSinglePage = app.morphe.extension.twitter.Utils.getBooleanPerf(Settings.SINGLE_PAGE_SETTINGS);
        if (isSinglePage) {
            boolean buildCategory = true;
            screenBuilder.buildPremiumSection(buildCategory);
            screenBuilder.buildDownloadSection(buildCategory);
            screenBuilder.buildFeatureFlagsSection(buildCategory);
            screenBuilder.buildAdsSection(buildCategory);
            screenBuilder.buildNativeSection(buildCategory);
            screenBuilder.buildMiscSection(buildCategory);
            screenBuilder.buildCustomiseSection(buildCategory);
            screenBuilder.buildTimelineSection(buildCategory);
            screenBuilder.buildLoggingSection(buildCategory);
            screenBuilder.buildExportSection(buildCategory);
            screenBuilder.buildPikoSection(buildCategory);

        } else {
            screenBuilder.buildSinglePageSettings();
        }
        setPreferenceScreen(screen);

    }

}
