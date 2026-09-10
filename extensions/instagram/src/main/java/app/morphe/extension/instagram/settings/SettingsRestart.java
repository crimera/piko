/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.Objects;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.patches.devFlags.FlagsSharedPref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class SettingsRestart {

    private static boolean pendingRestart;

    private SettingsRestart() {
    }

    public static synchronized void markChanged(Object previousValue, Object newValue) {
        if (Objects.equals(previousValue, newValue)) {
            return;
        }

        pendingRestart = true;
        flushPreferences();
    }

    private static synchronized void clearPendingRestart() {
        pendingRestart = false;
    }

    public static boolean promptRestartIfPending(Activity activity) {
        synchronized (SettingsRestart.class) {
            if (!pendingRestart || activity == null || activity.isFinishing()) {
                return false;
            }
        }

        try {
            new AlertDialog.Builder(activity)
                    .setTitle(str("piko_restart_app"))
                    .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                        clearPendingRestart();
                        Utils.restartApp(activity);
                    })
                    .setNegativeButton(str("piko_cancel"), (dialog, which) -> {
                        clearPendingRestart();
                        activity.finish();
                    })
                    .setOnCancelListener(dialog -> {
                        clearPendingRestart();
                        activity.finish();
                    })
                    .show();
            return true;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to prompt restart", e);
            clearPendingRestart();
            return false;
        }
    }

    private static void flushPreferences() {
        SharedPref.flush();
        FlagsSharedPref.flush();
    }
}
