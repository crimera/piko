/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.patches.Block;
import app.morphe.extension.instagram.patches.download.DownloadMapping;

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
                    if (key == null || !hasAction(key)) {
                        return true;
                    }

                    if (key.equals(str("piko_export_dev_overrides")) || key.equals(str("piko_import_dev_overrides")) || key.equals(str("piko_import_id_mapping"))
                            || key.equals(str("piko_export_pref")) || key.equals(str("piko_import_pref"))
                            || key.equals(str("piko_download_set_path"))) {
                        ActivityHook.launchFragment((Activity) context, key);

                    } else if (key.equals(str("piko_delete_analytics_cache"))) {
                        Block.deleteAnalyticsCacheFolder();

                    } else if (key.equals(str("piko_export_experiment_list"))) {
                        app.morphe.extension.instagram.utils.Utils.decompileExperiments(false);

                    } else if (key.equals(str("piko_export_experiment_mappings"))) {
                        app.morphe.extension.instagram.utils.Utils.decompileExperiments(true);

                    } else if (key.equals(str("piko_download_id_mapping"))) {
                        DownloadMapping.downloadMapping();

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
        return InstagramPreferenceStyle.createPreferenceView(context, InstagramPreferenceStyle.TRAILING_CHEVRON);
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
        InstagramPreferenceStyle.setTrailingVisible(view, hasAction(getKey()));
    }

    private boolean hasAction(String key) {
        return key != null
                && (key.equals(str("piko_export_dev_overrides"))
                || key.equals(str("piko_import_dev_overrides"))
                || key.equals(str("piko_import_id_mapping"))
                || key.equals(str("piko_export_pref"))
                || key.equals(str("piko_import_pref"))
                || key.equals(str("piko_download_set_path"))
                || key.equals(str("piko_delete_analytics_cache"))
                || key.equals(str("piko_export_experiment_list"))
                || key.equals(str("piko_export_experiment_mappings"))
                || key.equals(str("piko_download_id_mapping")));
    }

}


