/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.os.Environment;
import android.os.Build;
import java.io.File;
import android.content.Intent;
import android.net.Uri;
import android.content.Context;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;

public class StorageUtils {
    private static final String KEY_BASE_PATH = "custom_download_path";

    public static void saveCustomPath(String path) {
        SharedPref.setStringPref(KEY_BASE_PATH, path);
    }

    public static String getBasePath() {
        String customPath = SharedPref.getStringPref(KEY_BASE_PATH, "");

        if (customPath != null && !customPath.isBlank() && !customPath.equals("")) {
            File file = new File(customPath);
            if (file.exists() && file.canWrite()) {
                return customPath;
            }
        }
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + ExtensionStrings.DEFAULT_PIKO_FOLDER;
    }

    public static boolean checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true; // Lower versions handled by standard manifest permissions
    }

    public static void allowStorageAccess() {
        try {
            Context context = PikoUtils.getContext();
            Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            PikoUtils.toast(ExtensionStrings.DOWNLOAD_GRANT_PERMISSION);
        } catch (Exception e) {
            PikoUtils.toast(ExtensionStrings.DOWNLOAD_GRANT_PERMISSION_FAILED);
        }
    }
}