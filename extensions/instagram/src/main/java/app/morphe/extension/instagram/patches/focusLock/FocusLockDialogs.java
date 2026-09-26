/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Utils;

/** Confirmation flows behind the Focus Lock button. */
public class FocusLockDialogs {

    public static void onActionPressed(Context context, Preference button) {
        if (!FocusLock.isLocked()) {
            confirmLock(context);
        } else if (FocusLock.canUnlockNow()) {
            confirmUnlock(context);
        } else if (FocusLock.isUnlockPending()) {
            FocusLock.cancelUnlockRequest();
            refresh(button);
        } else {
            confirmRequestUnlock(context, button);
        }
    }

    private static void refresh(Preference button) {
        button.setTitle(FocusLock.buttonTitle());
        button.setSummary(FocusLock.statusSummary());
    }

    private static void confirmLock(Context context) {
        boolean reels = Pref.focusLockBlockReels();
        boolean explore = Pref.focusLockBlockExplore();
        if (!reels && !explore) {
            PikoUtils.toast(str("piko_focus_lock_nothing_selected"));
            return;
        }
        String days = Pref.focusLockDurationDays();
        String message = String.format(
                str("piko_focus_lock_confirm_lock_desc"),
                days,
                FocusLock.COOLING_OFF_MS / (60 * 60 * 1000)
        );
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_focus_lock_confirm_lock"))
                .setMessage(message)
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (d, w) -> {
                    if (FocusLock.lock()) {
                        PikoUtils.toast(str("piko_focus_lock_locked_toast"));
                        // Some hooks read preferences once at class-load time, so restart.
                        Utils.restartApp(Utils.getContext());
                    }
                })
                .show();
    }

    private static void confirmRequestUnlock(Context context, Preference button) {
        String message = String.format(
                str("piko_focus_lock_confirm_request_unlock_desc"),
                FocusLock.COOLING_OFF_MS / (60 * 60 * 1000)
        );
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_focus_lock_confirm_request_unlock"))
                .setMessage(message)
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (d, w) -> {
                    FocusLock.requestUnlock();
                    refresh(button);
                })
                .show();
    }

    private static void confirmUnlock(Context context) {
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_focus_lock_confirm_unlock"))
                .setMessage(str("piko_focus_lock_confirm_unlock_desc"))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (d, w) -> {
                    if (FocusLock.unlock()) {
                        Utils.restartApp(Utils.getContext());
                    }
                })
                .show();
    }
}
