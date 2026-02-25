package app.morphe.extension.instagram.settings;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.Helper;

public class SettingsFragment extends PreferenceFragment {
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        preferenceManager.setSharedPreferencesName(Strings.SHARED_PREF_NAME);

        Helper helper = new Helper(context);
        ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

        screenBuilder.buildAdsSection();
        screenBuilder.linksSection();
        screenBuilder.buildMiscSection();
        screenBuilder.buildDeveloperSection();

        setPreferenceScreen(screen);
    }
}