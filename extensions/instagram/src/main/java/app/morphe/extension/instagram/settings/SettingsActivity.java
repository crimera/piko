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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.preference.ScreenBuilder;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.shared.Utils;

public class SettingsActivity extends Activity {

    private LinearLayout root;
    private LinearLayout toolbar;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        createLayout();

        getFragmentManager()
                .beginTransaction()
                .replace(1001, new SettingsFragment())
                .commit();
    }


    private void createLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // ---------- Toolbar ----------
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(20, 40, 20, 40);
        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        back.setImageResource(Utils.getResourceIdentifier("material_ic_keyboard_arrow_left_black_24dp","drawable"));

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView title = new TextView(this);
        title.setText(Strings.PIKO_SETTINGS_TITLE);
        title.setTextSize(18f);
        title.setPadding(20, 0, 0, 0);

        toolbar.addView(back);
        toolbar.addView(title);

        // ---------- Content ----------

        content = new LinearLayout(this);
        content.setId(1001);
        content.setOrientation(LinearLayout.VERTICAL);

        root.addView(toolbar);
        root.addView(content,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                ));

        setContentView(root);
    }



    public static class SettingsFragment extends PreferenceFragment{

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

            setPreferenceScreen(screen);
        }
    }
}