/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class CollectionDownloadService extends Service {
    private static final String ACTION_START =
            "app.morphe.extension.instagram.action.START_COLLECTION_DOWNLOAD";
    private static final String ACTION_FINISH =
            "app.morphe.extension.instagram.action.FINISH_COLLECTION_DOWNLOAD";
    private static final String EXTRA_NOTIFICATION = "notification";

    private boolean foregroundStopped;

    static void start(Context context, Notification notification) {
        Context applicationContext = context.getApplicationContext();
        Context resolvedContext = applicationContext != null ? applicationContext : context;
        Intent intent = new Intent(resolvedContext, CollectionDownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NOTIFICATION, notification);

        resolvedContext.startForegroundService(intent);
    }

    static void stop(Context context) {
        Context applicationContext = context.getApplicationContext();
        Context resolvedContext = applicationContext != null ? applicationContext : context;
        resolvedContext.stopService(new Intent(resolvedContext, CollectionDownloadService.class));
    }

    static void finish(Context context, Notification notification) {
        Context applicationContext = context.getApplicationContext();
        Context resolvedContext = applicationContext != null ? applicationContext : context;
        Intent intent = new Intent(resolvedContext, CollectionDownloadService.class)
                .setAction(ACTION_FINISH)
                .putExtra(EXTRA_NOTIFICATION, notification);

        resolvedContext.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_FINISH.equals(intent.getAction())) {
            finish(getNotification(intent), startId);
            return START_NOT_STICKY;
        }

        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        Notification notification = getNotification(intent);
        if (notification == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    CollectionDownloadNotification.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(CollectionDownloadNotification.NOTIFICATION_ID, notification);
        }
        foregroundStopped = false;
        return START_NOT_STICKY;
    }

    @SuppressLint("NotificationPermission") // The patched Instagram host declares this permission.
    private void finish(Notification notification, int startId) {
        stopForeground(STOP_FOREGROUND_REMOVE);
        foregroundStopped = true;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (notification != null && manager != null) {
            manager.notify(CollectionDownloadNotification.NOTIFICATION_ID, notification);
        }
        stopSelf(startId);
    }

    @SuppressWarnings("deprecation")
    private static Notification getNotification(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_NOTIFICATION, Notification.class);
        }
        return intent.getParcelableExtra(EXTRA_NOTIFICATION);
    }

    @Override
    public void onDestroy() {
        if (!foregroundStopped) stopForeground(STOP_FOREGROUND_DETACH);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
