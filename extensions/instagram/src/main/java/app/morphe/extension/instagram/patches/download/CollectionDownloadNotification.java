/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;

import app.morphe.extension.crimera.downloader.MediaDownloader;

final class CollectionDownloadNotification {
    // Reusing one ID turns loading, downloading, and completion into one notification lifecycle.
    private static final int NOTIFICATION_ID = 0x50494B43;

    private final Context context;
    private final NotificationManager manager;
    private final PendingIntent cancelIntent;
    private final long startedAt = System.currentTimeMillis();

    CollectionDownloadNotification(Context context, PendingIntent cancelIntent) {
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext != null ? applicationContext : context;
        this.manager = this.context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("Notification service is unavailable");
        this.cancelIntent = cancelIntent;
        createChannel();
    }

    void showLoading(int loadedPosts) {
        Notification.Builder builder = activeBuilder(
                str("piko_preparing_collection_download"),
                str("piko_collection_posts_loaded", loadedPosts)
        );
        builder.setProgress(0, 0, true);
        notify(builder);
    }

    void showReady(int posts) {
        Notification.Builder builder = activeBuilder(
                str("piko_download_collection"),
                str("piko_collection_posts_ready", posts)
        );
        builder.setProgress(0, 0, false);
        notify(builder);
    }

    void showDownloading(int processedFiles, int totalFiles) {
        Notification.Builder builder = activeBuilder(
                str("piko_downloading_collection"),
                str("piko_collection_download_progress", processedFiles, totalFiles)
        );
        builder.setProgress(Math.max(totalFiles, 1), processedFiles, false);
        notify(builder);
    }

    void showComplete(int downloaded, int skipped, int failed) {
        String summary;
        if (failed > 0) {
            summary = str(
                    "piko_collection_download_summary_with_failures",
                    downloaded,
                    skipped,
                    failed
            );
        } else if (skipped > 0) {
            summary = str("piko_collection_download_summary_with_existing", downloaded, skipped);
        } else {
            summary = str("piko_collection_download_summary", downloaded);
        }

        Notification.Builder builder = baseBuilder(
                str("piko_collection_download_complete"),
                summary
        );
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false);
        notify(builder);
    }

    void showFailed() {
        Notification.Builder builder = baseBuilder(
                str("piko_collection_download_failed"),
                str("piko_collection_download_try_again")
        );
        builder.setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false);
        notify(builder);
    }

    void cancel() {
        manager.cancel(NOTIFICATION_ID);
    }

    private Notification.Builder activeBuilder(String title, String text) {
        return baseBuilder(title, text)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(
                                context,
                                android.R.drawable.ic_menu_close_clear_cancel
                        ),
                        context.getString(android.R.string.cancel),
                        cancelIntent
                ).build());
    }

    private Notification.Builder baseBuilder(String title, String text) {
        Notification.Builder builder = new Notification.Builder(context, MediaDownloader.CHANNEL_ID);
        return builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setWhen(startedAt)
                .setCategory(Notification.CATEGORY_PROGRESS);
    }

    @SuppressLint("NotificationPermission") // The patched Instagram host declares this permission.
    private void notify(Notification.Builder builder) {
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                MediaDownloader.CHANNEL_ID,
                str("piko_download_notification_channel"),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }
}
