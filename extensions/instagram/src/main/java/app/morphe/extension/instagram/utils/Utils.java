/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.entity.DeveloperOptions;

public class Utils {

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
        String appVersionName = app.morphe.extension.shared.Utils.getAppVersionName();
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
}