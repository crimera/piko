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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.settings.SettingsStatus;

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

        // Extract both variables safely from the incoming intent bundle
        if (getIntent() != null && getIntent().getExtras() != null) {
            displayTitle = str(getIntent().getStringExtra(Constants.PIKO_FRAGMENT_TITLE));
        }

        // Fallback to default localized string if no custom title was provided in the intent
        if (displayTitle == null || displayTitle.isEmpty()) {
            displayTitle = str("piko_title_settings");
        }

        createLayout( displayTitle);

        SettingsFragment fragment = new SettingsFragment();
        if (getIntent() != null && getIntent().getExtras() != null) {
            fragment.setArguments(getIntent().getExtras());
        }

        getFragmentManager().beginTransaction().replace(1001, fragment).commit();
    }

    @SuppressLint("ResourceType")
    private void createLayout(String displayTitle) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor(this));

        applySystemBarStyle();

        // ---------- Toolbar ----------
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor(this));

        int toolbarPadding = InstagramPreferenceStyle.dp(this, 15);
        toolbar.setPadding(toolbarPadding, InstagramPreferenceStyle.dp(this, 10), toolbarPadding, InstagramPreferenceStyle.dp(this, 8));

        int iconSize = InstagramPreferenceStyle.dp(this, 44);

        BackArrowView back = new BackArrowView(this);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        backParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        titleTextView = new TextView(this);
        titleTextView.setText(displayTitle); // Dynamically bound from intent data
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        titleTextView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        titleTextView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = InstagramPreferenceStyle.dp(this, 7);
        titleTextView.setLayoutParams(titleParams);
        titleTextView.setTextColor(InstagramPreferenceStyle.primaryTextColor(this));

        toolbar.addView(back);
        toolbar.addView(titleTextView);

        // ---------- Custom Container ----------
        customContainer = new LinearLayout(this);
        customContainer.setOrientation(LinearLayout.VERTICAL);
        customContainer.setBackgroundColor(Color.TRANSPARENT);

        root.addView(toolbar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, InstagramPreferenceStyle.dp(this, 70)));
        root.addView(customContainer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---------- Content ----------
        content = new LinearLayout(this);
        content.setId(1001);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(InstagramPreferenceStyle.backgroundColor(this));

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

    // (Keep original BackArrowView & applySystemBarStyle methods unchanged)
    private static class BackArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BackArrowView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float centerY = getHeight() / 2f;
            float tipX = InstagramPreferenceStyle.dp(getContext(), 2);
            float endX = InstagramPreferenceStyle.dp(getContext(), 21);
            float headEndX = InstagramPreferenceStyle.dp(getContext(), 10);
            float headOffset = InstagramPreferenceStyle.dp(getContext(), 7);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(InstagramPreferenceStyle.dp(getContext(), 1.9f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(InstagramPreferenceStyle.primaryTextColor(getContext()));

            canvas.drawLine(tipX, centerY, endX, centerY, paint);
            canvas.drawLine(tipX, centerY, headEndX, centerY - headOffset, paint);
            canvas.drawLine(tipX, centerY, headEndX, centerY + headOffset, paint);
        }
    }

    private void applySystemBarStyle() {
        getWindow().setStatusBarColor(InstagramPreferenceStyle.backgroundColor(this));
        getWindow().setNavigationBarColor(InstagramPreferenceStyle.backgroundColor(this));

        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (InstagramPreferenceStyle.isDark(this)) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // (Keep the nested static SettingsFragment class unchanged)
    public static class SettingsFragment extends PreferenceFragment {

        Context context;

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
                listView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor(context));
            }

            if (rootView != null) {
                rootView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor(context));
            }
        }
    }
}