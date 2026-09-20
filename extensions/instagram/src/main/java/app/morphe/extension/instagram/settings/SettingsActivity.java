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

import java.util.List;
import java.util.function.Supplier;

import app.morphe.extension.crimera.downloader.StorageUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.customise.font.CustomFont;
import app.morphe.extension.instagram.patches.customise.font.FontStorage;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.widgets.FontPref;
import app.morphe.extension.instagram.settings.preference.widgets.FontSelection;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.settings.preference.widgets.SwitchPref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.theme.MaterialYouTheme;

public class SettingsActivity extends Activity {

    private LinearLayout root;
    private LinearLayout toolbar;
    private LinearLayout content;
    private LinearLayout customContainer;
    private TextView titleTextView;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        String displayTitle = null;
        String fragmentName = null;

        // Extract both variables safely from the incoming intent bundle
        if (getIntent() != null && getIntent().getExtras() != null) {
            displayTitle = str(getIntent().getStringExtra(Constants.PIKO_FRAGMENT_TITLE));
            fragmentName = getIntent().getStringExtra(Constants.PIKO_FRAGMENT_NAME);
        }

        // Fallback to default localized string if no custom title was provided in the intent
        if (displayTitle == null || displayTitle.isEmpty()) {
            displayTitle = str("piko_title_settings");
        }

        boolean isRootSettings = fragmentName == null || Constants.PIKO_FRAGMENT_SETTINGS.equals(fragmentName);
        createLayout(displayTitle, isRootSettings);

        if (bundle == null) {
            SettingsFragment fragment = new SettingsFragment();
            if (getIntent() != null && getIntent().getExtras() != null) {
                fragment.setArguments(getIntent().getExtras());
            }

            getFragmentManager().beginTransaction().replace(1001, fragment).commit();
        }
    }

    @SuppressLint("ResourceType")
    private void createLayout(String displayTitle, boolean isRootSettings) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        InstagramPreferenceStyle.applySystemBarStyle(this);

        // ---------- Toolbar ----------
        toolbar = new LinearLayout(this);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        ImageView back = new ImageView(this);
        UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        titleTextView = new TextView(this);
        titleTextView.setText(displayTitle); // Dynamically bound from intent data
        InstagramPreferenceStyle.applyToolbarLayout(
                this, toolbar, back, titleTextView, isRootSettings);
        CustomFont.applyTo(titleTextView);
        titleTextView.setTextColor(InstagramPreferenceStyle.primaryTextColor());

        toolbar.addView(back);
        toolbar.addView(titleTextView);

        // ---------- Custom Container ----------
        customContainer = new LinearLayout(this);
        customContainer.setOrientation(LinearLayout.VERTICAL);
        customContainer.setBackgroundColor(Color.TRANSPARENT);

        root.addView(toolbar);
        root.addView(customContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---------- Content ----------
        content = new LinearLayout(this);
        content.setId(1001);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
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
            }
        });

        setContentView(root);
    }

    public LinearLayout getCustomContainer() {
        return customContainer;
    }

    public static class SettingsFragment extends PreferenceFragment
            implements FontSelection.Listener {

        Context context;

        /** Which fonts the font section was built from, so a font added elsewhere is noticed. */
        private List<String> renderedFonts;

        /** Set only on the font screen. */
        private FontSelection fontSelection;

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

            // The font picker runs in its own activity, so a font added there only shows up once
            // the list has been built again.
            if (renderedFonts != null && !renderedFonts.equals(FontStorage.list())) {
                rebuildFontScreen();
                return;
            }

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
            } else if (fragment_name.equals(Constants.PIKO_FRAGMENT_FONT)) {
                prepareFontSelection();
                screenBuilder.buildFontSection(fontSelection);
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

                // Resolved by position on every long press, rather than attached to each row's own
                // view, since the list recycles those views across whichever font ends up bound to
                // them - a listener kept on the view itself would drift onto the wrong font.
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    Object item = parent.getItemAtPosition(position);
                    if (!(item instanceof FontPref)) {
                        return false;
                    }
                    ((FontPref) item).confirmDelete();
                    return true;
                });
            }

            if (rootView != null) {
                rootView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            }
        }

        @Override
        public void onListChanged() {
            rebuildFontScreen();
        }

        /** Starts a fresh selection for the font screen that is about to be built. */
        private void prepareFontSelection() {
            renderedFonts = FontStorage.list();
            fontSelection = new FontSelection();
            fontSelection.setListener(this);
        }

        /**
         * Builds the font list again so it matches the fonts that are there now. Adding or
         * removing a row is not something the preference list can do in place, but replacing the
         * whole screen keeps the activity - and the list's own styling - as it is.
         */
        private void rebuildFontScreen() {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            prepareFontSelection();
            new ScreenBuilder(context, screen, new Helper(context)).buildFontSection(fontSelection);
            setPreferenceScreen(screen);
        }
    }
}
