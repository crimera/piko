/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.preference.fragments.FragmentHook;
import app.morphe.extension.instagram.patches.Block;
import app.morphe.extension.instagram.patches.download.DownloadMapping;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;

public class ButtonPref extends Preference {
    private final Context context;


    public ButtonPref(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public ButtonPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public ButtonPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }


    private void init() {

        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    String key = getKey();
                    if (key == null) {
                        return true;
                    }

                    if (key.equals("piko_export_dev_overrides") || key.equals("piko_import_dev_overrides") || key.equals("piko_import_id_mapping")
                            || key.equals("piko_export_pref") || key.equals("piko_import_pref")
                            || key.equals("piko_download_set_path")) {
                        ActivityHook.launchFragment((Activity) context, key);

                    } else if (key.equals("piko_delete_analytics_cache")) {
                        Block.deleteAnalyticsCacheFolder();

                    } else if (key.equals("piko_export_experiment_list")) {
                        app.morphe.extension.instagram.utils.Utils.decompileExperiments(false);

                    } else if (key.equals("piko_export_experiment_mappings")) {
                        app.morphe.extension.instagram.utils.Utils.decompileExperiments(true);

                    } else if (key.equals("piko_download_id_mapping")) {
                        DownloadMapping.downloadMapping();

                    } else if (key.startsWith("piko_frag_")) {
                        FragmentHook.startFragment(key);

                    }
                } catch (Exception e) {
                    Utils.showToastShort(e.getMessage());
                    Logger.printException(() -> "Preference button onclick failure", e);
                }
                return true;
            }
        });
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return InstagramPreferenceStyle.createPreferenceView(context, InstagramPreferenceStyle.TRAILING_CHEVRON,getIconResourceName(getKey()));
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
        InstagramPreferenceStyle.setTrailingVisible(view, hasVisibleTrail(getKey()));
    }

    private boolean hasVisibleTrail(String key) {
        return key != null
                && (key.equals("piko_export_dev_overrides")
                || key.equals("piko_import_dev_overrides")
                || key.equals("piko_import_id_mapping")
                || key.equals("piko_export_pref")
                || key.equals("piko_import_pref")
                || key.equals("piko_download_set_path")
                || key.equals("piko_delete_analytics_cache")
                || key.equals("piko_export_experiment_list")
                || key.equals("piko_export_experiment_mappings")
                || key.equals("piko_download_id_mapping"));
    }

    private String getIconResourceName(String key) {
        if(key.equals(Constants.PIKO_FRAGMENT_ADS)){
            return UI.DRAWABLE_SHEILD_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_GHOST)){
            return UI.DRAWABLE_SNAPCHAT_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_LINKS)){
            return UI.DRAWABLE_LINK_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_DISTRACTION_FREE)){
            return UI.DRAWABLE_FRAME_CROSSED_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_MISC)){
            return UI.DRAWABLE_CODE_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA)){
            return UI.DRAWABLE_FB_DOWNLOAD_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_NAV_BTNS)){
            return UI.DRAWABLE_STACK_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_DEV_OPTIONS)){
            return UI.DRAWABLE_GEAR_ICON;
        }
        if(key.equals(Constants.PIKO_FRAGMENT_ABOUT)){
            return UI.DRAWABLE_DEBUG_ICON;
        }
        return null;
    }

}


