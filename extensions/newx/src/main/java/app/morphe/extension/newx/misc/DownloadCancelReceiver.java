package app.morphe.extension.newx.misc;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Cancels an in-flight inline download from its progress notification. */
public final class DownloadCancelReceiver extends BroadcastReceiver {
    static final String ACTION_CANCEL = "app.morphe.extension.newx.action.CANCEL_DOWNLOAD";
    private static final String EXTRA_NOTIFICATION_ID =
            "app.morphe.extension.newx.extra.CANCEL_NOTIFICATION_ID";

    /** PendingIntent for the progress notification's cancel button. Null when unusable. */
    static PendingIntent cancelPendingIntent(Context context, int notificationId) {
        if (context == null || notificationId <= 0) return null;
        try {
            Intent intent = new Intent(context, DownloadCancelReceiver.class);
            intent.setAction(ACTION_CANCEL);
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getBroadcast(context, notificationId, intent, flags);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ACTION_CANCEL.equals(intent.getAction())) return;

        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (notificationId <= 0) return;

        Context applicationContext = context.getApplicationContext();
        DownloadDestination.requestCancel(
                applicationContext != null ? applicationContext : context, notificationId);
    }
}
