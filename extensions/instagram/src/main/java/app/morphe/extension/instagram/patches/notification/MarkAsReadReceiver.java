/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Clears a chat's notification and marks it as seen when Android Auto marks it as read. */
public final class MarkAsReadReceiver extends BroadcastReceiver {
    static final String EXTRA_TAG = "piko_notification_tag";
    static final String EXTRA_ID = "piko_notification_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        // A failure here must not crash Instagram, e.g. when Android Auto sends the action.
        try {
            ConversationNotifications.markAsRead(context, intent.getStringExtra(EXTRA_TAG), intent.getIntExtra(EXTRA_ID, 0));
        } catch (Throwable ex) {
            app.morphe.extension.shared.Logger.printException(() -> "Failed to mark a chat as read", ex);
        }
    }
}
