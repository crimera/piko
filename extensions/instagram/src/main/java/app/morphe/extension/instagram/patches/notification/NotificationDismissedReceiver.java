/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import app.morphe.extension.shared.Logger;

/** A swipe on a chat notification: marks what it held as shown, then runs Instagram's own delete intent. */
public final class NotificationDismissedReceiver extends BroadcastReceiver {
    static final String EXTRA_TAG = "piko_notification_tag";
    static final String EXTRA_ID = "piko_notification_id";
    static final String EXTRA_ORIGINAL = "piko_original_delete_intent";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            ConversationNotifications.onDismissed(intent.getStringExtra(EXTRA_TAG), intent.getIntExtra(EXTRA_ID, 0));
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to handle a dismissed notification", ex);
        }
        try {
            PendingIntent original = intent.getParcelableExtra(EXTRA_ORIGINAL);
            if (original != null) original.send();
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to run Instagram's delete intent", ex);
        }
    }
}
