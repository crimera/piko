/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import static app.morphe.extension.instagram.utils.IgStr.str;
import static app.morphe.extension.shared.requests.Route.Method.GET;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.Objects;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.entity.DeveloperOptions;
import app.morphe.extension.crimera.PikoUtils;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.Logger;

public class InstaUtils {
    private static final String SETTINGS_RESTART_TAG = "PikoSettingsRestart";
    private static final SettingsChangeCoordinator SETTINGS_CHANGE_COORDINATOR =
            new SettingsChangeCoordinator();

    public static void markSettingsChanged(Object previousValue, Object newValue) {
        if (SETTINGS_CHANGE_COORDINATOR.markChanged(previousValue, newValue)) {
            startSettingsTaskService();
        }
    }

    private static void onSettingsTaskRemoved() {
        try {
            SETTINGS_CHANGE_COORDINATOR.onTaskRemoved(
                    InstaUtils::flushPreferences,
                    InstaUtils::scheduleProcessExit
            );
        } catch (RuntimeException exception) {
            Log.e(
                    SETTINGS_RESTART_TAG,
                    "Failed to prepare the process restart; keeping it pending",
                    exception
            );
        }
    }

    private static void flushPreferences() {
        Context context = Utils.getContext();
        if (context == null) {
            throw new IllegalStateException("Extension context is unavailable");
        }

        boolean settingsFlushed = context
                .getSharedPreferences(ExtensionStrings.PIKO_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .commit();
        boolean flagsFlushed = context
                .getSharedPreferences(Constants.REC_FLAGS, Context.MODE_PRIVATE)
                .edit()
                .commit();

        requirePreferencesFlushed(settingsFlushed, flagsFlushed);
    }

    private static void requirePreferencesFlushed(boolean settingsFlushed, boolean flagsFlushed) {
        if (!settingsFlushed || !flagsFlushed) {
            throw new IllegalStateException("Failed to flush Piko preferences");
        }
    }

    private static void startSettingsTaskService() {
        Context context = Utils.getContext();
        if (context == null) {
            SETTINGS_CHANGE_COORDINATOR.taskServiceStartFailed();
            Log.e(
                    SETTINGS_RESTART_TAG,
                    "Failed to start the task service: extension context is unavailable"
            );
            return;
        }

        try {
            Intent intent = new Intent(context, SettingsTaskService.class);
            if (context.startService(intent) == null) {
                throw new IllegalStateException("Task service was not resolved");
            }
        } catch (RuntimeException exception) {
            SETTINGS_CHANGE_COORDINATOR.taskServiceStartFailed();
            Log.e(
                    SETTINGS_RESTART_TAG,
                    "Failed to start the task service; the next setting change can retry",
                    exception
            );
        }
    }

    private static void scheduleProcessExit() {
        new Handler(Looper.getMainLooper()).post(
                () -> Process.killProcess(Process.myPid())
        );
    }

    private static final class SettingsChangeCoordinator {
        private boolean pending;
        private boolean taskServiceStartRequested;

        synchronized boolean markChanged() {
            pending = true;
            if (taskServiceStartRequested) {
                return false;
            }

            taskServiceStartRequested = true;
            return true;
        }

        synchronized boolean markChanged(Object previousValue, Object newValue) {
            return !Objects.equals(previousValue, newValue) && markChanged();
        }

        synchronized void taskServiceStartFailed() {
            taskServiceStartRequested = false;
        }

        synchronized boolean onTaskRemoved(Runnable flushPreferences, Runnable scheduleExit) {
            if (!pending) {
                return false;
            }

            flushPreferences.run();
            scheduleExit.run();
            return true;
        }
    }

    public static final class SettingsTaskService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            return START_NOT_STICKY;
        }

        @Override
        public void onTaskRemoved(Intent rootIntent) {
            onSettingsTaskRemoved();
            super.onTaskRemoved(rootIntent);
        }
    }

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

    public static void deletePref(){
        if(Pref.clearAllPreferences()){
            PikoUtils.toast(str("piko_reset_pref_success"));
            Utils.restartApp(Utils.getContext());
        }else{
            PikoUtils.toast(str("piko_reset_pref_failed"));
        }
    }

    public static void downloadFile(String host, String endpoint, File outputFile, boolean restartAfterDownload) {
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

                PikoUtils.writeFile(outputFile, response.getBytes(), false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    PikoUtils.toast(str("piko_downloaded_media") + outputFile.getName());
                    if (restartAfterDownload) Utils.restartApp(context);
                });

            } catch (Exception e) {
                PikoUtils.logger(e);
                PikoUtils.toast(str("piko_download_failed_media") + outputFile.getName());
            }
        });

    }
}
