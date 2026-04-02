/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.shared.Utils;

public class SettingsActivity extends Activity {

    private LinearLayout root;
    private LinearLayout toolbar;
    private LinearLayout content;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        createLayout();

        getFragmentManager().beginTransaction().replace(1001, new SettingsFragment()).commit();
    }


    @SuppressLint("ResourceType")
    private void createLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // ---------- Toolbar ----------
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        int dimen = Utils.getResourceDimensionPixelSize("abc_edit_text_inset_top_material");

        UI.setThemedIcon(back, "material_ic_keyboard_arrow_left_black_24dp");
        back.setPaddingRelative(dimen, dimen, dimen, dimen);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView title = new TextView(this);
        title.setText(Strings.PIKO_SETTINGS_TITLE);
        title.setTextSize(Utils.getResourceDimensionPixelSize("fbui_text_size_micro"));
        title.setPaddingRelative(dimen, dimen, dimen, dimen);
        title.setTextColor(UI.getThemedColour());

        toolbar.addView(back);
        toolbar.addView(title);

        // ---------- Content ----------

        content = new LinearLayout(this);
        content.setId(1001);
        content.setOrientation(LinearLayout.VERTICAL);

        root.addView(toolbar);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int topInset = insets.getSystemWindowInsetTop();
                v.setPadding(0, topInset, 0, 0);
                return insets;
            }
        });

        setContentView(root);
    }


    public static class SettingsFragment extends PreferenceFragment {

        Context context;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            context = getActivity();
            PreferenceManager preferenceManager = getPreferenceManager();
            PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
            preferenceManager.setSharedPreferencesName(Strings.SHARED_PREF_NAME);

            Helper helper = new Helper(context);
            ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

            screenBuilder.buildAdsSection();
            screenBuilder.ghostSection();
            screenBuilder.linksSection();
            screenBuilder.distractionFreeSection();
            screenBuilder.buildMiscSection();
            screenBuilder.buildDownloadSection();
            screenBuilder.buildDeveloperSection();
            screenBuilder.aboutSection();

            setPreferenceScreen(screen);
        }
    }
}
