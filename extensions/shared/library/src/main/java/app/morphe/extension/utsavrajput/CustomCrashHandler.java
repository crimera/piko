/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Build;
import android.util.Log;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.shared.Utils;

public class CustomCrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CustomCrashHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        String stackTrace = Log.getStackTraceString(throwable);
        saveCrashToFile(stackTrace);

        // Pass to original handler to handle OS-level crash cleanup
        defaultHandler.uncaughtException(thread, throwable);
    }

    private void saveCrashToFile(String stackTrace) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);

            StringBuilder report = new StringBuilder();
            report.append("--- Device Info ---\n");
            report.append("Brand: ").append(Build.BRAND).append("\n");
            report.append("OS Version: ").append(Build.VERSION.RELEASE).append("\n");
            report.append("SDK Level: ").append(Build.VERSION.SDK_INT).append("\n\n");
            report.append("--- App Info ---\n");
            report.append("Package Name: ").append(context.getPackageName()).append("\n");
            report.append("Version Name: ").append(pi.versionName).append("\n");
            report.append("Version Code: ").append(pi.versionCode).append("\n");
            report.append("Patch version: ").append(Utils.getPatchesReleaseVersion()).append("\n\n");
            report.append("--- Stack Trace ---\n").append(stackTrace);

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            String fileName = "crash_report_" + timeStamp + ".txt";
            String folderName = ExtensionStrings.DEFAULT_PIKO_FOLDER;

            boolean isCreated = PikoUtils.pikoWriteFile(fileName,folderName,report.toString(),false);
            if(isCreated){
                PikoUtils.toast("Crashlogs: /Downloads/"+folderName+"/"+fileName);
            } else{
                PikoUtils.toast("Failed to create Crashlogs file");
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }
}