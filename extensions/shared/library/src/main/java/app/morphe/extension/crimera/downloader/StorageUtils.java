/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.crimera.downloader;

import android.os.Environment;

import java.io.File;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.crimera.constants.ExtensionStrings;

public class StorageUtils {
    private static final String KEY_BASE_PATH = "custom_downloader_path";

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
}