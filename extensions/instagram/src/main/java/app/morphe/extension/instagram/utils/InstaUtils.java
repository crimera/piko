/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.net.HttpURLConnection;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.entity.DeveloperOptions;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.crimera.PikoUtils;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.Logger;
import static app.morphe.extension.shared.requests.Route.Method.GET;

public class InstaUtils {

    public static boolean deleteRecursive(File file) {
        try {
            if (file == null || !file.exists()) {
                PikoUtils.toast(str("piko_fail_no_file"));
                return false;
            }

            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            return file.delete();
        } catch (RuntimeException e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    public static void decompileExperiments(boolean asJson) {
        String appVersionName = Utils.getAppVersionName();
        DeveloperOptions developerOptions = new DeveloperOptions();

        String fileName = appVersionName + " Experiments";
        boolean fileDone = false;
        String data = "";
        String fileDoneTxt = " failed";

        if (asJson) {
            fileName += ".json";
            data = developerOptions.toJSONObject().toString();
        } else {
            fileName += ".txt";
            data = developerOptions.toString();
        }
        fileDone = PikoUtils.pikoWriteFile(fileName, Constants.DEFAULT_PIKO_FOLDER, data, false);
        fileDoneTxt = fileDone ? " created" : fileDoneTxt;
        PikoUtils.toast(fileName + fileDoneTxt);
    }

    public static void showResetSettingsDialog(Context context) {
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_reset_pref_confirm"))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(
                        str("piko_ok"),
                        (dialogInterface, which) -> deletePref()
                )
                .show();
    }

    public static void deletePref(){
        if(Pref.clearAllPreferences()){
            PikoUtils.toast(str("piko_reset_pref_success"));
            Utils.restartApp(Utils.getContext());
        }else{
            PikoUtils.toast(str("piko_reset_pref_failed"));
        }
    }

    public static void downloadFile(String host, String endpoint, File outputFile, Runnable onComplete) {
        Context context = Utils.getContext();

        if (!Utils.isNetworkConnected()) {
            PikoUtils.toast(str("piko_no_internet"));
            return;
        }
        Utils.runOnBackgroundThread(() -> {
            try {
                Route route = new Route(GET, endpoint);
                HttpURLConnection connection = Requester.getConnectionFromRoute(host, route);
                String response = Requester.parseString(connection);

                boolean fileWritten = PikoUtils.writeFile(outputFile, response.getBytes(), false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!fileWritten) {
                        PikoUtils.toast(str("piko_download_failed_media") + outputFile.getName());
                        return;
                    }
                    PikoUtils.toast(str("piko_downloaded_media") + outputFile.getName());

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

            } catch (Exception e) {
                PikoUtils.logger(e);
                PikoUtils.toast(str("piko_download_failed_media") + outputFile.getName());
            }
        });
    }
}
