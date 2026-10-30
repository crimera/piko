/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.patches.devFlags.FlagsSharedPref;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class SettingsRestart {

    private static final String TASK_SERVICE_ACTION =
            "app.morphe.extension.instagram.action.SETTINGS_RESTART";
    private static final Map<String, Object> originalValues = new HashMap<>();
    private static final Map<String, RestartPolicy> changedSettings = new HashMap<>();

    private SettingsRestart() {
    }

    public static void markChanged(String key, Object previousValue, Object newValue) {
        markChanged(key, SettingRegistry.restartPolicyFor(key), previousValue, newValue);
    }

    /** For changes outside preferences, such as replacing the custom font file. */
    public static synchronized void markChanged(String key, RestartPolicy restartPolicy,
                                                Object previousValue, Object newValue) {
        Objects.requireNonNull(restartPolicy);
        if (Objects.equals(previousValue, newValue)) {
            return;
        }

        if (!originalValues.containsKey(key)) {
            // Preference widgets can mutate their sets after saving.
            originalValues.put(key, previousValue instanceof Set
                    ? new HashSet<>((Set<?>) previousValue) : previousValue);
        }
        if (Objects.equals(originalValues.get(key), newValue)) {
            changedSettings.remove(key);
        } else {
            changedSettings.put(key, restartPolicy);
        }
        flushPreferences();
        if (!changedSettings.isEmpty()) {
            startTaskService();
        }
    }

    private static void startTaskService() {
        Context context = Utils.getContext();
        if (context == null) return;
        try {
            Intent intent = new Intent(TASK_SERVICE_ACTION);
            intent.setClassName(context.getPackageName(), taskServiceClassName());
            if (context.startService(intent) == null) {
                throw new IllegalStateException("Task service was not resolved");
            }
        } catch (RuntimeException e) {
            Logger.printException(() -> "Failed to start the task service", e);
        }
    }

    // Replaced by the patch with the service resolved from the target APK's manifest.
    private static String taskServiceClassName() {
        return "";
    }

    public static boolean isTaskServiceIntent(Intent intent) {
        return intent != null && TASK_SERVICE_ACTION.equals(intent.getAction());
    }

    public static void onTaskRemoved() {
        new Handler(Looper.getMainLooper()).post(SettingsRestart::restartIfTaskRemoved);
    }

    private static synchronized void restartIfTaskRemoved() {
        if (changedSettings.isEmpty()) return;
        try {
            Context context = Utils.getContext();
            if (context == null) return;
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null || !manager.getAppTasks().isEmpty()) return;
            if (!flushPreferences()) return;
            // A delayed callback must not terminate an app that has already been reopened.
            if (manager.getAppTasks().isEmpty()) {
                Process.killProcess(Process.myPid());
            }
        } catch (RuntimeException e) {
            Logger.printException(() -> "Failed to prepare the process restart", e);
        }
    }

    private static synchronized void clearPendingRestart() {
        originalValues.clear();
        changedSettings.clear();
    }

    public static boolean promptRestartIfPending(Activity activity) {
        synchronized (SettingsRestart.class) {
            if (activity == null || activity.isFinishing()
                    || !changedSettings.containsValue(RestartPolicy.REQUIRED)) {
                return false;
            }
        }

        try {
            new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                    .setTitle(str("piko_restart_app"))
                    .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                        clearPendingRestart();
                        Utils.restartApp(activity);
                    })
                    .setNegativeButton(str("piko_cancel"), (dialog, which) -> {
                        activity.finish();
                    })
                    .setOnCancelListener(dialog -> {
                        activity.finish();
                    })
                    .show();
            return true;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to prompt restart", e);
            return false;
        }
    }

    private static boolean flushPreferences() {
        boolean settingsFlushed = SharedPref.flush();
        boolean flagsFlushed = FlagsSharedPref.flush();
        return settingsFlushed && flagsFlushed;
    }
}
