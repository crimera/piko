/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.wireguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import com.wireguard.android.backend.GoBackend;

import app.morphe.extension.twitter.settings.Settings;

/** Foreground lifecycle around the unmodified upstream userspace backend service. */
public final class WireGuardVpnService extends GoBackend.VpnService {
    private static final String CHANNEL = "piko_wireguard";
    private static final int NOTIFICATION = 0x70696b6f;
    private boolean foregroundFailed;

    @Override public void onCreate() {
        try {
            NotificationManager notifications = getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Piko WireGuard",
                    NotificationManager.IMPORTANCE_LOW));
            Intent settings = new Intent().setClassName(getPackageName(), "com.twitter.android.AuthorizeAppActivity")
                    .putExtra("piko", true).putExtra(Settings.ACT_NAME, Settings.WIREGUARD_SECTION);
            PendingIntent pending = PendingIntent.getActivity(this, NOTIFICATION, settings,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("Piko WireGuard")
                    .setContentText(WireGuardFragment.text(this, "piko_wireguard_notification"))
                    .setContentIntent(pending).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
            } else startForeground(NOTIFICATION, notification);
        } catch (Exception | LinkageError error) {
            foregroundFailed = true;
            stopSelf();
        }
        super.onCreate();
    }

    @Override public Builder getBuilder() {
        if (foregroundFailed) throw new IllegalStateException("VPN foreground service unavailable");
        return super.getBuilder();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override public void onRevoke() { stopSelf(); }

    @Override public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Upstream onDestroy mutates GoBackend and closes its native handle. Queue it
        // with setState to avoid destroying a handle concurrently with connect/disconnect.
        WireGuardManager.get(this).serviceDestroyed(() -> super.onDestroy());
    }
}
