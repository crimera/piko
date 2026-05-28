/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.fragments;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.settings.Settings;

public class BackupPrefFragment extends Fragment {
    private String prefData;
    private static String prefTag;

    private void startIntent(String fileName,int mode) {
        fileName = fileName+"_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".json";
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("application/json");
        intent.putExtra("android.intent.extra.TITLE", fileName);
        startActivityForResult(intent, mode);
    }

    private void copyFile(Uri uri) {
        try {
            String jsonString = this.prefData;
            byte[] bytes = jsonString.getBytes();

            OutputStream openOutputStream = getActivity().getContentResolver().openOutputStream(uri);
            openOutputStream.write(bytes);
            openOutputStream.close();
            toast(str("piko_pref_export_success"));
        } catch (IOException e) {
            toast(str("piko_pref_export_failed",prefTag));
        }
    }

    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        Uri uri = null;
        if (intent != null) {
            uri = intent.getData();
        }
        if (uri == null) {
            toast(str("piko_pref_export_no_uri"));

        }
        else if (i2 == -1) {
            copyFile(uri);
        }

        getFragmentManager().popBackStack();
    }

    private void toast(String msg){
        app.morphe.extension.crimera.PikoUtils.toast(msg);
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean featureFlag = getArguments().getBoolean("featureFlag", false);

        if (featureFlag) {
            this.prefData = Utils.getStringPref(Settings.MISC_FEATURE_FLAGS);
            prefTag = str("piko_title_feature_flags");
            startIntent("feature_flags", 1);
        } else {
            this.prefData = Utils.getAll(true);
            prefTag = str("notification_settings_preferences_category");
            startIntent("backup", 1);
        }
    }
}