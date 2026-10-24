/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import app.morphe.extension.crimera.downloader.StorageUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.devFlags.RecommendedFlags;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.settings.preference.widgets.SearchResultPreference;
import app.morphe.extension.instagram.settings.preference.widgets.SwitchPref;
import app.morphe.extension.instagram.theme.MaterialYouTheme;
import app.morphe.extension.shared.Logger;

public final class SettingsActivity {

    private SettingsActivity() {
    }

    private static final int FRAGMENT_CONTAINER_ID = 1001;
    private static final String STATE_FRAGMENT_NAME = "piko_settings_fragment_name";
    private static final String STATE_FRAGMENT_STATE = "piko_settings_fragment_state";
    private static final String STATE_DISPLAY_TITLE = "piko_settings_display_title";
    private static final String STATE_SEARCH_QUERY = "piko_settings_search_query";
    private static final String STATE_SEARCH_MODE = "piko_settings_search_mode";
    private static final String STATE_TARGET_PREFERENCE_KEY = "piko_settings_target_preference_key";
    private static final int NO_RESULTS_PORTRAIT_TOP_MARGIN_DP = 132;
    private static final int NO_RESULTS_LANDSCAPE_TOP_MARGIN_DP = 24;

    private static final Map<Activity, Controller> sControllers = new WeakHashMap<>();

    static Controller getController(Activity activity) {
        return activity == null ? null : sControllers.get(activity);
    }

    @SuppressLint("ResourceType")
    public static void setupOnActivity(Activity activity, Bundle savedInstanceState) {
        Controller controller = new Controller(activity);
        sControllers.put(activity, controller);
        controller.setup(savedInstanceState);

        if (activity != null && activity.getApplication() != null) {
            activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override
                public void onActivityResumed(Activity a) {
                    if (a == activity) {
                        controller.onResume();
                    }
                }
                @Override
                public void onActivityPaused(Activity a) {
                    if (a == activity) {
                        controller.onPause();
                    }
                }
                @Override public void onActivityStopped(Activity a) {}
                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {
                    if (a == activity) {
                        controller.onSaveInstanceState(b);
                    }
                }
                @Override
                public void onActivityDestroyed(Activity a) {
                    if (a == activity) {
                        a.getApplication().unregisterActivityLifecycleCallbacks(this);
                        sControllers.remove(activity);
                        controller.onDestroy();
                    }
                }
            });
        }
    }

    public static boolean handleBackPressed(Activity activity) {
        Controller controller = getController(activity);
        if (controller != null && controller.exitSearchIfActive()) {
            return true;
        }
        if (controller != null && controller.rootSettings && SettingsRestart.promptRestartIfPending(activity)) {
            return true;
        }
        return false;
    }

    public static class Controller {
        private final Activity activity;
        private FrameLayout root;
        private FrameLayout chromeOverlay;
        private LinearLayout toolbar;
        private FrameLayout contentFrame;
        private LinearLayout customContainer;
        private TextView titleTextView;
        private ImageView backButton;
        private View noResultsOverlay;
        private SettingsFragment rootFragment;
        private SettingsSearchController searchController;
        private boolean refreshSearchIndexOnResume;
        private final Runnable recommendedFlagsChanged = this::refreshSearchIndex;
        private String fragmentName;
        private String displayTitle;
        private String targetPreferenceKey;
        private String restoredSearchQuery = "";
        private boolean restoredSearchMode;
        private boolean rootSettings;
        private Object searchBackDispatcher;
        private Object searchBackCallback;
        private boolean searchBackCallbackRegistered;

        public Controller(Activity activity) {
            this.activity = activity;
        }

        @SuppressLint("ResourceType")
        public void setup(Bundle savedInstanceState) {
            Bundle intentExtras = activity.getIntent() == null ? null : activity.getIntent().getExtras();
            String requestedFragmentName = intentExtras == null
                    ? null
                    : intentExtras.getString(Constants.PIKO_FRAGMENT_NAME);
            String requestedTitle = intentExtras == null
                    ? null
                    : intentExtras.getString(Constants.PIKO_FRAGMENT_TITLE);
            targetPreferenceKey = intentExtras == null
                    ? null
                    : intentExtras.getString(Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY);

            if (savedInstanceState != null) {
                requestedFragmentName = savedInstanceState.getString(STATE_FRAGMENT_NAME, requestedFragmentName);
                displayTitle = savedInstanceState.getString(STATE_DISPLAY_TITLE);
                restoredSearchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "");
                restoredSearchMode = savedInstanceState.getBoolean(STATE_SEARCH_MODE, false);
                targetPreferenceKey = savedInstanceState.getString(
                        STATE_TARGET_PREFERENCE_KEY,
                        targetPreferenceKey
                );
            }

            if (displayTitle == null || displayTitle.isEmpty()) {
                displayTitle = str(requestedTitle);
            }
            if (displayTitle == null || displayTitle.isEmpty()) {
                displayTitle = str("piko_title_settings");
            }

            rootSettings = requestedFragmentName == null
                    || Constants.PIKO_FRAGMENT_SETTINGS.equals(requestedFragmentName);
            fragmentName = requestedFragmentName == null
                    ? Constants.PIKO_FRAGMENT_SETTINGS
                    : requestedFragmentName;
            createLayout(displayTitle, rootSettings);

            if (activity instanceof androidx.activity.ComponentActivity) {
                try {
                    ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(
                            (androidx.lifecycle.LifecycleOwner) activity,
                            new androidx.activity.OnBackPressedCallback(true) {
                                @Override
                                public void handleOnBackPressed() {
                                    if (exitSearchIfActive()) {
                                        return;
                                    }
                                    if (rootSettings && SettingsRestart.promptRestartIfPending(activity)) {
                                        return;
                                    }
                                    remove();
                                    activity.finish();
                                }
                            }
                    );
                } catch (Throwable ignored) {
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33BackHelper.register(activity, () -> {
                    if (exitSearchIfActive()) {
                        return;
                    }
                    if (rootSettings && SettingsRestart.promptRestartIfPending(activity)) {
                        return;
                    }
                    activity.finish();
                });
            }

            SettingsFragment fragment = null;
            if (savedInstanceState != null) {
                android.app.Fragment restoredFragment = activity.getFragmentManager()
                        .findFragmentById(FRAGMENT_CONTAINER_ID);
                if (restoredFragment instanceof SettingsFragment) {
                    fragment = (SettingsFragment) restoredFragment;
                    fragment.updateNavigationArguments(fragmentName, targetPreferenceKey);
                }
            }

            if (fragment == null && activity.getFragmentManager().findFragmentById(FRAGMENT_CONTAINER_ID) == null) {
                fragment = new SettingsFragment();
                fragment.setArguments(createFragmentArguments());
                if (savedInstanceState != null) {
                    fragment.setInitialSavedState(savedInstanceState.getParcelable(STATE_FRAGMENT_STATE));
                }
                activity.getFragmentManager().beginTransaction()
                        .replace(FRAGMENT_CONTAINER_ID, fragment)
                        .commit();
            }
        }

        private Bundle createFragmentArguments() {
            Bundle arguments = new Bundle();
            arguments.putString(Constants.PIKO_FRAGMENT_NAME, fragmentName);
            arguments.putString(Constants.PIKO_FRAGMENT_TITLE, displayTitle);
            if (targetPreferenceKey != null && !targetPreferenceKey.isEmpty()) {
                arguments.putString(
                        Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY,
                        targetPreferenceKey
                );
            }
            return arguments;
        }

        @SuppressLint("ResourceType")
        private void createLayout(String displayTitle, boolean isRootSettings) {
            root = new FrameLayout(activity);
            root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            LinearLayout page = new LinearLayout(activity);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            root.addView(page, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            InstagramPreferenceStyle.applySystemBarStyle(activity);

            // ---------- Toolbar ----------
            toolbar = new LinearLayout(activity);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            int toolbarPadding = InstagramPreferenceStyle.dp(activity, 15);
            toolbar.setPadding(toolbarPadding, InstagramPreferenceStyle.dp(activity, 10), toolbarPadding, InstagramPreferenceStyle.dp(activity, 8));

            int iconSize = InstagramPreferenceStyle.dp(activity, 44);

            View backSpacer = new View(activity);
            LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            backParams.gravity = android.view.Gravity.CENTER_VERTICAL;
            backSpacer.setLayoutParams(backParams);

            titleTextView = new TextView(activity);
            titleTextView.setText(displayTitle); // Dynamically bound from intent data
            int titleTextSize = isRootSettings ? 25 : 20;
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleTextSize);
            titleTextView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            titleTextView.setIncludeFontPadding(false);
            titleTextView.setMaxLines(1);
            titleTextView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
            titleTextView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            if (!isRootSettings) {
                titleTextView.setAutoSizeTextTypeUniformWithConfiguration(
                        18,
                        20,
                        1,
                        TypedValue.COMPLEX_UNIT_SP
                );
            }
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            titleParams.gravity = android.view.Gravity.CENTER_VERTICAL;
            titleParams.setMarginStart(InstagramPreferenceStyle.dp(activity, 7));
            titleTextView.setLayoutParams(titleParams);
            titleTextView.setTextColor(InstagramPreferenceStyle.primaryTextColor());

            toolbar.addView(backSpacer);
            toolbar.addView(titleTextView);

            customContainer = new LinearLayout(activity);
            customContainer.setOrientation(LinearLayout.VERTICAL);
            customContainer.setBackgroundColor(Color.TRANSPARENT);

            page.addView(toolbar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, InstagramPreferenceStyle.dp(activity, 70)));
            page.addView(customContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // ---------- Content ----------
            contentFrame = new FrameLayout(activity);
            contentFrame.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            FrameLayout fragmentContainer = new FrameLayout(activity);
            fragmentContainer.setId(FRAGMENT_CONTAINER_ID);
            fragmentContainer.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            contentFrame.addView(fragmentContainer, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            noResultsOverlay = createNoResultsOverlay();
            contentFrame.addView(noResultsOverlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            page.addView(contentFrame, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ));

            chromeOverlay = new FrameLayout(activity);
            root.addView(chromeOverlay, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            backButton = new ImageView(activity);
            UI.setThemedIcon(backButton, UI.DRAWABLE_ARROW_BACK);
            backButton.setScaleType(ImageView.ScaleType.CENTER);
            backButton.setPaddingRelative(
                    0,
                    0,
                    InstagramPreferenceStyle.dp(activity, 16),
                    0
            );
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (exitSearchIfActive()) {
                        return;
                    }
                    if (isRootSettings && SettingsRestart.promptRestartIfPending(activity)) {
                        return;
                    }
                    activity.finish();
                }
            });
            FrameLayout.LayoutParams overlayBackParams = new FrameLayout.LayoutParams(
                    iconSize,
                    iconSize
            );
            overlayBackParams.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
            overlayBackParams.setMarginStart(InstagramPreferenceStyle.dp(activity, 15));
            overlayBackParams.topMargin = InstagramPreferenceStyle.dp(activity, 13);
            chromeOverlay.addView(backButton, overlayBackParams);

            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int topInset = insets.getSystemWindowInsetTop();
                int bottomInset = insets.getSystemWindowInsetBottom();

                v.setPadding(0, topInset, 0, 0);

                contentFrame.setPadding(
                        contentFrame.getPaddingLeft(),
                        contentFrame.getPaddingTop(),
                        contentFrame.getPaddingRight(),
                        bottomInset
                );

                return insets;
            });

            activity.setContentView(root);
        }

        private View createNoResultsOverlay() {
            FrameLayout overlay = new FrameLayout(activity);
            overlay.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            overlay.setClickable(true);
            overlay.setVisibility(View.GONE);

            LinearLayout message = new LinearLayout(activity);
            message.setOrientation(LinearLayout.VERTICAL);
            message.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

            ImageView icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconPadding = InstagramPreferenceStyle.dp(activity, 12);
            icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            UI.setThemedIcon(icon, UI.DRAWABLE_SEARCH, "igds_color_secondary_text");
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            message.addView(icon, new LinearLayout.LayoutParams(
                    InstagramPreferenceStyle.dp(activity, 96),
                    InstagramPreferenceStyle.dp(activity, 96)
            ));

            TextView title = new TextView(activity);
            title.setText(str("piko_settings_search_no_results"));
            title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setGravity(android.view.Gravity.CENTER);
            title.setIncludeFontPadding(false);
            title.setPadding(
                    InstagramPreferenceStyle.dp(activity, 24),
                    InstagramPreferenceStyle.dp(activity, 8),
                    InstagramPreferenceStyle.dp(activity, 24),
                    0
            );
            message.addView(title, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            messageParams.gravity = android.view.Gravity.TOP
                    | android.view.Gravity.CENTER_HORIZONTAL;
            int orientation = activity.getResources().getConfiguration().orientation;
            int topMarginDp = orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? NO_RESULTS_LANDSCAPE_TOP_MARGIN_DP
                    : NO_RESULTS_PORTRAIT_TOP_MARGIN_DP;
            messageParams.topMargin = InstagramPreferenceStyle.dp(activity, topMarginDp);
            overlay.addView(message, messageParams);
            return overlay;
        }

        void onRootFragmentReady(
                SettingsFragment fragment,
                PreferenceScreen rootScreen
        ) {
            if (!rootSettings || searchController != null) {
                return;
            }

            rootFragment = fragment;
            searchController = new SettingsSearchController(
                    activity,
                    createSearchIndex(fragment, rootScreen),
                    new SettingsSearchController.Listener() {
                        @Override
                        public void onSearchStateChanged(SettingsSearchController.State state) {
                            if (rootFragment != null) {
                                rootFragment.renderSearchState(state);
                            }
                            noResultsOverlay.setVisibility(
                                    state.noResults ? View.VISIBLE : View.GONE
                            );
                        }

                        @Override
                        public void onSearchChromeChanged(boolean inToolbar) {
                            applySearchChromeState(inToolbar);
                        }
                    }
            );
            searchController.attach(chromeOverlay);
            fragment.searchEntryView = searchController.createSearchEntryView();
            chromeOverlay.bringChildToFront(backButton);
            searchController.restore(restoredSearchQuery, restoredSearchMode);
        }

        private SettingsSearchIndex createSearchIndex(
                SettingsFragment fragment,
                PreferenceScreen rootScreen
        ) {
            final PreferenceManager preferenceManager = fragment.getPreferenceManager();
            final Context context = fragment.getActivity();
            return SettingsSearchIndex.create(
                    rootScreen,
                    new SettingsSearchIndex.ScreenFactory() {
                        @Override
                        public PreferenceScreen build(String fragmentName) {
                            String previousSharedPreferencesName =
                                    preferenceManager.getSharedPreferencesName();
                            preferenceManager.setSharedPreferencesName(
                                    Constants.PIKO_FRAGMENT_REC_FLAGS.equals(fragmentName)
                                            ? Constants.REC_FLAGS
                                            : Constants.SHARED_PREF_NAME
                            );
                            try {
                                PreferenceScreen screen =
                                        preferenceManager.createPreferenceScreen(context);
                                new ScreenBuilder(context, screen, new Helper(context))
                                        .buildFragmentForSearch(fragmentName);
                                return screen;
                            } finally {
                                preferenceManager.setSharedPreferencesName(
                                        previousSharedPreferencesName
                                );
                            }
                        }
                    }
            );
        }

        public void onPause() {
            RecommendedFlags.removeChangeListener(recommendedFlagsChanged);
            refreshSearchIndexOnResume = searchController != null;
        }

        public void onResume() {
            if (rootSettings) {
                RecommendedFlags.addChangeListener(recommendedFlagsChanged);
            }
            if (refreshSearchIndexOnResume) {
                refreshSearchIndexOnResume = false;
                refreshSearchIndex();
            }
        }

        private void refreshSearchIndex() {
            if (rootFragment != null && searchController != null) {
                searchController.replaceIndex(createSearchIndex(
                        rootFragment,
                        rootFragment.buildScreen(Constants.PIKO_FRAGMENT_SETTINGS)
                ));
            }
        }

        private void applySearchChromeState(boolean inToolbar) {
            if (inToolbar) {
                registerSearchBackCallback();
            } else {
                unregisterSearchBackCallback();
            }
            titleTextView.setVisibility(inToolbar ? View.GONE : View.VISIBLE);
            if (chromeOverlay != null && backButton != null) {
                chromeOverlay.bringChildToFront(backButton);
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            SettingsSearchController.State searchState = searchController == null
                    ? null
                    : searchController.state();
            outState.putString(STATE_FRAGMENT_NAME, fragmentName);
            outState.putString(STATE_DISPLAY_TITLE, displayTitle);
            outState.putString(
                    STATE_SEARCH_QUERY,
                    searchState == null ? restoredSearchQuery : searchState.query
            );
            outState.putBoolean(
                    STATE_SEARCH_MODE,
                    searchState == null ? restoredSearchMode : searchState.searchMode
            );
            outState.putString(STATE_TARGET_PREFERENCE_KEY, targetPreferenceKey);

            // Instagram removes android:fragments during host creation, so keep our state under a Piko key.
            android.app.Fragment fragment = activity.getFragmentManager()
                    .findFragmentById(FRAGMENT_CONTAINER_ID);
            if (fragment instanceof SettingsFragment) {
                outState.putParcelable(
                        STATE_FRAGMENT_STATE,
                        activity.getFragmentManager().saveFragmentInstanceState(fragment)
                );
            }
        }

        public boolean exitSearchIfActive() {
            if (searchController == null
                    || searchController.state() == null
                    || !searchController.state().searchMode) {
                return false;
            }
            searchController.exitSearchMode();
            return true;
        }

        private void registerSearchBackCallback() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                    || searchBackCallbackRegistered) {
                return;
            }

            try {
                Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
                Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
                Object dispatcher = Activity.class
                        .getMethod("getOnBackInvokedDispatcher")
                        .invoke(activity);
                Object callback = Proxy.newProxyInstance(
                        SettingsActivity.class.getClassLoader(),
                        new Class<?>[]{callbackClass},
                        (proxy, method, args) -> {
                            String methodName = method.getName();
                            int parameterCount = method.getParameterTypes().length;
                            if ("hashCode".equals(methodName) && parameterCount == 0) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(methodName) && parameterCount == 1) {
                                return proxy == (args == null ? null : args[0]);
                            }
                            if ("toString".equals(methodName) && parameterCount == 0) {
                                return "PikoInstagramSettingsSearchBackCallback";
                            }
                            if ("onBackInvoked".equals(methodName)) {
                                exitSearchIfActive();
                            }
                            return null;
                        }
                );
                int priorityDefault = dispatcherClass
                        .getField("PRIORITY_DEFAULT")
                        .getInt(null);
                dispatcher.getClass()
                        .getMethod("registerOnBackInvokedCallback", int.class, callbackClass)
                        .invoke(dispatcher, priorityDefault, callback);
                searchBackDispatcher = dispatcher;
                searchBackCallback = callback;
                searchBackCallbackRegistered = true;
            } catch (Throwable throwable) {
                Logger.printException(
                        () -> "Instagram settings search back callback registration failed",
                        throwable
                );
                searchBackDispatcher = null;
                searchBackCallback = null;
                searchBackCallbackRegistered = false;
            }
        }

        private void unregisterSearchBackCallback() {
            if (!searchBackCallbackRegistered || searchBackCallback == null) {
                return;
            }

            try {
                Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
                Object dispatcher = searchBackDispatcher;
                if (dispatcher == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dispatcher = Activity.class
                            .getMethod("getOnBackInvokedDispatcher")
                            .invoke(activity);
                }
                if (dispatcher != null) {
                    dispatcher.getClass()
                            .getMethod("unregisterOnBackInvokedCallback", callbackClass)
                            .invoke(dispatcher, searchBackCallback);
                }
            } catch (Throwable throwable) {
                Logger.printException(
                        () -> "Instagram settings search back callback unregistration failed",
                        throwable
                );
            }
            searchBackDispatcher = null;
            searchBackCallback = null;
            searchBackCallbackRegistered = false;
        }

        public void onDestroy() {
            RecommendedFlags.removeChangeListener(recommendedFlagsChanged);
            unregisterSearchBackCallback();
        }

        public LinearLayout getCustomContainer() {
            return customContainer;
        }
    }

    public static class SettingsFragment extends PreferenceFragment {

        Context context;
        private PreferenceManager preferenceManager;
        private String fragmentName = Constants.PIKO_FRAGMENT_SETTINGS;
        private String targetPreferenceKey;
        private ListView listView;
        private View searchEntryView;
        private boolean searchEntryAdded;

        void updateNavigationArguments(String fragmentName, String targetPreferenceKey) {
            this.fragmentName = fragmentName == null
                    ? Constants.PIKO_FRAGMENT_SETTINGS
                    : fragmentName;
            this.targetPreferenceKey = targetPreferenceKey;

            Bundle arguments = getArguments();
            if (arguments != null) {
                arguments.putString(Constants.PIKO_FRAGMENT_NAME, this.fragmentName);
                if (targetPreferenceKey == null || targetPreferenceKey.isEmpty()) {
                    arguments.remove(Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY);
                } else {
                    arguments.putString(
                            Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY,
                            targetPreferenceKey
                    );
                }
            }
        }

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
        public void onDestroyView() {
            listView = null;
            searchEntryView = null;
            searchEntryAdded = false;
            super.onDestroyView();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            context = getActivity();
            preferenceManager = getPreferenceManager();
            preferenceManager.setSharedPreferencesName(Constants.SHARED_PREF_NAME);

            if (getArguments() != null) {
                fragmentName = getArguments().getString(
                        Constants.PIKO_FRAGMENT_NAME,
                        Constants.PIKO_FRAGMENT_SETTINGS
                );
                targetPreferenceKey = getArguments().getString(
                        Constants.PIKO_SEARCH_TARGET_PREFERENCE_KEY
                );
            }

            preferenceManager.setSharedPreferencesName(
                    Constants.PIKO_FRAGMENT_REC_FLAGS.equals(fragmentName)
                            ? Constants.REC_FLAGS
                            : Constants.SHARED_PREF_NAME
            );

            setPreferenceScreen(buildScreen(fragmentName));
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            View rootView = getView();
            listView = null;
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

            Activity activity = getActivity();
            Controller controller = getController(activity);
            if (isRootFragment() && controller != null) {
                controller.onRootFragmentReady(
                        this,
                        getPreferenceScreen()
                );
            } else if (listView != null
                    && targetPreferenceKey != null
                    && !targetPreferenceKey.isEmpty()) {
                scrollToPreference(listView, targetPreferenceKey);
            }

            // Build the search screen before binding so a queued rebind cannot reset restored scroll state.
            super.onActivityCreated(savedInstanceState);
        }

        private PreferenceScreen buildScreen(String fragmentName) {
            PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
            new ScreenBuilder(context, screen, new Helper(context)).buildFragment(fragmentName);
            return screen;
        }

        private boolean isRootFragment() {
            return fragmentName == null
                    || Constants.PIKO_FRAGMENT_SETTINGS.equals(fragmentName);
        }

        private void renderSearchState(SettingsSearchController.State state) {
            if (!isRootFragment() || state == null) {
                return;
            }

            PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
            if (!state.searchMode) {
                new ScreenBuilder(context, screen, new Helper(context))
                        .buildFragment(Constants.PIKO_FRAGMENT_SETTINGS);
            } else {
                for (SettingsSearchIndex.Result result : state.results) {
                    screen.addPreference(new SearchResultPreference(
                            context,
                            result
                    ));
                }
            }
            setPreferenceScreen(screen);
            if (listView != null) {
                if (searchEntryView != null) {
                    if (state.searchMode && searchEntryAdded) {
                        listView.removeHeaderView(searchEntryView);
                        searchEntryAdded = false;
                    } else if (!state.searchMode && !searchEntryAdded) {
                        listView.addHeaderView(searchEntryView, null, false);
                        searchEntryAdded = true;
                    }
                    listView.setPadding(0,
                            InstagramPreferenceStyle.dp(context, state.searchMode ? 10 : 0),
                            0, InstagramPreferenceStyle.dp(context, 10));
                }
                screen.bind(listView);
            }
        }

        private void scrollToPreference(
                final ListView listView,
                final String preferenceKey
        ) {
            if (findPreference(preferenceKey) == null) {
                return;
            }

            listView.post(new Runnable() {
                @Override
                public void run() {
                    ListAdapter adapter = listView.getAdapter();
                    int position = findPreferencePosition(adapter, preferenceKey);
                    if (position < 0) {
                        return;
                    }

                    listView.setSelectionFromTop(
                            position,
                            InstagramPreferenceStyle.dp(context, 16)
                    );
                }
            });
        }

        private static int findPreferencePosition(
                ListAdapter adapter,
                String preferenceKey
        ) {
            if (adapter == null) {
                return -1;
            }
            for (int position = 0; position < adapter.getCount(); position++) {
                if (isTargetPreference(adapter, position, preferenceKey)) {
                    return position;
                }
            }
            return -1;
        }

        private static boolean isTargetPreference(
                ListAdapter adapter,
                int position,
                String preferenceKey
        ) {
            if (adapter == null || position < 0 || position >= adapter.getCount()) {
                return false;
            }
            Object item = adapter.getItem(position);
            if (!(item instanceof Preference)) {
                return false;
            }
            String itemKey = ((Preference) item).getKey();
            return preferenceKey.equals(itemKey);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class Api33BackHelper {
        static void register(Activity activity, Runnable onBack) {
            try {
                activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        onBack::run
                );
            } catch (Throwable ignored) {
            }
        }
    }
}
