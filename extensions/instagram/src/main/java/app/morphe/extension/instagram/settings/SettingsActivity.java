/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.function.Supplier;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import app.morphe.extension.crimera.downloader.StorageUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.settings.preference.widgets.SwitchPref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.theme.MaterialYouTheme;

public class SettingsActivity extends Activity {

    @SuppressLint("ResourceType")
    static void setupOnActivity(Activity activity) {
        String displayTitle = null;
        String fragmentName = null;

        if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
            displayTitle = str(activity.getIntent().getStringExtra(Constants.PIKO_FRAGMENT_TITLE));
            fragmentName = activity.getIntent().getStringExtra(Constants.PIKO_FRAGMENT_NAME);
        }

        if (displayTitle == null || displayTitle.isEmpty()) {
            displayTitle = str("piko_title_settings");
        }

        boolean isRootSettings = fragmentName == null || Constants.PIKO_FRAGMENT_SETTINGS.equals(fragmentName);
        createLayoutOnActivity(activity, displayTitle, isRootSettings);

        if (isRootSettings && activity instanceof ComponentActivity) {
            ComponentActivity componentActivity = (ComponentActivity) activity;
            componentActivity.getOnBackPressedDispatcher().addCallback(
                    componentActivity,
                    new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            if (SettingsRestart.promptRestartIfPending(activity)) {
                                return;
                            }
                            setEnabled(false);
                            componentActivity.getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
            );
        }

        if (activity.getFragmentManager().findFragmentById(1001) == null) {
            SettingsFragment fragment = new SettingsFragment();
            if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
                fragment.setArguments(activity.getIntent().getExtras());
            }

            activity.getFragmentManager().beginTransaction().replace(1001, fragment).commit();
        }
    }

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setupOnActivity(this);
    }

    @Override
    public void onBackPressed() {
        if (SettingsRestart.promptRestartIfPending(this)) {
            return;
        }
        super.onBackPressed();
    }

    @SuppressLint("ResourceType")
    private static void createLayoutOnActivity(Activity activity, String displayTitle, boolean isRootSettings) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        InstagramPreferenceStyle.applySystemBarStyle(activity);

        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        ImageView back = new ImageView(activity);
        UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
        back.setOnClickListener(v -> {
            if (isRootSettings && SettingsRestart.promptRestartIfPending(activity)) {
                return;
            }
            activity.finish();
        });

        TextView titleTextView = new TextView(activity);
        titleTextView.setText(displayTitle);
        InstagramPreferenceStyle.applyToolbarLayout(
                activity, toolbar, back, titleTextView, isRootSettings);
        titleTextView.setTextColor(InstagramPreferenceStyle.primaryTextColor());

        toolbar.addView(back);
        toolbar.addView(titleTextView);

        LinearLayout customContainer = new LinearLayout(activity);
        customContainer.setOrientation(LinearLayout.VERTICAL);
        customContainer.setBackgroundColor(Color.TRANSPARENT);

        root.addView(toolbar);
        root.addView(customContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(activity);
        content.setId(1001);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();

            v.setPadding(0, topInset, 0, 0);

            content.setPadding(
                    content.getPaddingLeft(),
                    content.getPaddingTop(),
                    content.getPaddingRight(),
                    bottomInset
            );

            return insets;
        });

        activity.setContentView(root);
    }
    public static class SettingsFragment extends PreferenceFragment {

        Context context;

        private void refreshPreferenceSummary(
                String key,
                Supplier<CharSequence> summaryProvider
        ) {
            Preference preference = findPreference(key);
            if (preference != null) {
                preference.setSummary(summaryProvider.get());
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshPreferenceSummary(
                    "piko_download_set_path",
                    StorageUtils::getCustomPathForDisplay
            );
            Preference preference = findPreference(Settings.AMOLED_THEME.key);
            if (preference instanceof SwitchPref) {
                SwitchPref amoledPreference = (SwitchPref) preference;
                amoledPreference.setChecked(MaterialYouTheme.isAmoledEnabled());
                amoledPreference.setSwitchInteractionEnabled(
                        MaterialYouTheme.canEnableAmoled(getActivity())
                );
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            context = getActivity();
            PreferenceManager preferenceManager = getPreferenceManager();
            PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
            preferenceManager.setSharedPreferencesName(Constants.SHARED_PREF_NAME);

            Helper helper = new Helper(context);
            ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

            String fragment_name = Constants.PIKO_FRAGMENT_SETTINGS;
            if (getArguments() != null) {
                fragment_name = getArguments().getString(Constants.PIKO_FRAGMENT_NAME, Constants.PIKO_FRAGMENT_SETTINGS);
            }
            if(fragment_name.equals(Constants.PIKO_FRAGMENT_SETTINGS)) {
                screenBuilder.buildSettingsPage();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_ADS)) {
                screenBuilder.buildAdsSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_GHOST)) {
                screenBuilder.ghostSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_LINKS)) {
                screenBuilder.linksSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_DISTRACTION_FREE)) {
                screenBuilder.distractionFreeSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_MISC)) {
                screenBuilder.buildMiscSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA)) {
                screenBuilder.buildDownloadSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_NAV_BTNS)) {
                screenBuilder.buildNavigationSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_DEV_OPTIONS)) {
                screenBuilder.buildDeveloperSection();
            } else if(fragment_name.equals(Constants.PIKO_FRAGMENT_ABOUT)) {
                screenBuilder.aboutSection(SettingsStatus.FLAGS);
            } else if (fragment_name.equals(Constants.PIKO_FRAGMENT_ACTION_BAR)) {
                screenBuilder.buildActionBarSection();
            } else if (fragment_name.equals(Constants.PIKO_FRAGMENT_DM)) {
                screenBuilder.dmSection();
            } else if (fragment_name.equals(Constants.PIKO_FRAGMENT_FILTER_CONTENT)) {
                screenBuilder.filterContentSection();
            } else if (fragment_name.equals(Constants.PIKO_FRAGMENT_REC_FLAGS)) {
                preferenceManager.setSharedPreferencesName(Constants.REC_FLAGS);
                screenBuilder.buildRecommendedFlagsSection();
            }

            setPreferenceScreen(screen);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            View rootView = getView();
            ListView listView = null;
            if (rootView != null) {
                View list = rootView.findViewById(android.R.id.list);
                if (list instanceof ListView) {
                    listView = (ListView) list;
                }
            }
            if (listView != null) {
                listView.setPadding(0, InstagramPreferenceStyle.dp(context, 10), 0, InstagramPreferenceStyle.dp(context, 10));
                listView.setClipToPadding(false);
                listView.setDivider(null);
                listView.setDividerHeight(0);
                listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
                listView.setCacheColorHint(Color.TRANSPARENT);
                listView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            }

            if (rootView != null) {
                rootView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            }
        }
    }
}
