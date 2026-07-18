/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import app.morphe.extension.twitter.settings.widgets.Helper;
import app.morphe.extension.twitter.settings.ScreenBuilder;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.SettingsSearchUIController;

@SuppressWarnings("deprecation")
public class SettingsFragment extends PreferenceFragment implements SettingsSearchUIController.Listener {
    private Context context;
    private PreferenceScreen screen;
    private ScreenBuilder screenBuilder;

    @Override
    public void onResume() {
        super.onResume();
        if (screenBuilder != null) {
            screenBuilder.invalidateSettingsSearchIndex();
        }
        if (!SettingsSearchUIController.isActive()) {
            SettingsSearchUIController.restoreToolbarTitle(getActivity());
        }
        SettingsSearchUIController.setListener(getActivity(), this);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        Context preferenceContext = ActivityHook.getPreferenceContext(context);

        PreferenceManager preferenceManager = getPreferenceManager();
        screen = preferenceManager.createPreferenceScreen(preferenceContext);
        preferenceManager.setSharedPreferencesName(Settings.SHARED_PREF_NAME);

        Helper helper = new Helper(context);
        screenBuilder = new ScreenBuilder(context, screen, helper);

        rebuildSettings(SettingsSearchUIController.query());
        setPreferenceScreen(screen);

    }

    @Override
    public void onDestroy() {
        Activity activity = getActivity();
        if (activity != null && (activity.isFinishing() || activity.isChangingConfigurations())) {
            SettingsSearchUIController.release(activity);
        }
        super.onDestroy();
    }

    @Override
    public void onSettingsSearchQueryChanged(String query) {
        if (screen == null || screenBuilder == null) {
            return;
        }
        rebuildSettings(query);
    }

    private void rebuildSettings(String query) {
        screen.removeAll();
        if (!SettingsSearchUIController.isActive()) {
            SettingsSearchUIController.setContentState("", true, false);
            screenBuilder.buildSettingsCategories();
        } else if (TextUtils.isEmpty(query) || TextUtils.isEmpty(query.trim())) {
            SettingsSearchUIController.setContentState(query, false, false);
        } else {
            int resultCount = screenBuilder.buildSettingsSearchResults(query);
            SettingsSearchUIController.setContentState(query, resultCount > 0, resultCount == 0);
        }
    }
}
