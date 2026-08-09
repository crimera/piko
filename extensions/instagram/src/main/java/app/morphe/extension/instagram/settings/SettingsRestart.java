/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Objects;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.patches.devFlags.FlagsSharedPref;
import app.morphe.extension.shared.Utils;

public final class SettingsRestart {
    private static final String TAG = "PikoSettingsRestart";
    private static boolean pending;
    private static boolean taskServiceStartRequested;

    private SettingsRestart() {
    }

    public static void markChanged(Object previousValue, Object newValue) {
        if (Objects.equals(previousValue, newValue) || !requestTaskServiceStart()) {
            return;
        }

        startTaskService();
    }

    static void onTaskRemoved() {
        synchronized (SettingsRestart.class) {
            if (!pending) {
                return;
            }
        }

        try {
            flushPreferences();
            new Handler(Looper.getMainLooper()).post(
                    () -> Process.killProcess(Process.myPid())
            );
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to prepare the process restart; keeping it pending", exception);
        }
    }

    private static synchronized boolean requestTaskServiceStart() {
        pending = true;
        if (taskServiceStartRequested) {
            return false;
        }

        taskServiceStartRequested = true;
        return true;
    }

    private static void startTaskService() {
        Context context = Utils.getContext();
        if (context == null) {
            taskServiceStartFailed();
            Log.e(TAG, "Failed to start the task service: extension context is unavailable");
            return;
        }

        try {
            Intent intent = new Intent(context, SettingsTaskService.class);
            if (context.startService(intent) == null) {
                throw new IllegalStateException("Task service was not resolved");
            }
        } catch (RuntimeException exception) {
            taskServiceStartFailed();
            Log.e(TAG, "Failed to start the task service; the next setting change can retry", exception);
        }
    }

    private static synchronized void taskServiceStartFailed() {
        taskServiceStartRequested = false;
    }

    private static void flushPreferences() {
        boolean settingsFlushed = SharedPref.flush();
        boolean flagsFlushed = FlagsSharedPref.flush();
        if (!settingsFlushed || !flagsFlushed) {
            throw new IllegalStateException("Failed to flush Piko preferences");
        }
    }
}
