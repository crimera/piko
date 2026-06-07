/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;

public class MediaDownloader {
    private static final String CHANNEL_ID = "media_download_channel";
    private final Context context;
    private final NotificationManager notificationManager;
    private final LinkedBlockingQueue<DownloadRequest> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;

    public MediaDownloader(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void enqueue(DownloadRequest request) {
        if (!StorageUtils.checkStoragePermissions()) {
            StorageUtils.allowStorageAccess();
            return;
        }
        queue.add(request);
        processNext();
    }

    private void processNext() {
        if (isDownloading || queue.isEmpty()) return;
        isDownloading = true;
        DownloadRequest request = queue.poll();
        if (request != null) {
            executor.execute(() -> runDownloadTask(request));
        }
    }

    private void runDownloadTask(DownloadRequest request) {
        int notificationId = (int) System.currentTimeMillis();
        String currentBaseDir = StorageUtils.getBasePath();

        File targetDir = (request.subFolder == null || request.subFolder.isEmpty())
                ? new File(currentBaseDir)
                : new File(currentBaseDir, request.subFolder);

        if (!targetDir.exists()) targetDir.mkdirs();

        File outputFile = new File(targetDir, request.fileName);
        if (outputFile.exists()) {
            showToast(ExtensionStrings.DOWNLOAD_MEDIA_EXISTS);
            isDownloading = false;
            processNext();
            return;
        }
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        String downloadStartString = ExtensionStrings.DOWNLOAD_ONGOING + request.fileName;
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(downloadStartString)
                .setOngoing(true) // Keeps notification un-swipable during download execution.
                .setProgress(100, 0, false);

        notificationManager.notify(notificationId, builder.build());

        try {
            showToast(downloadStartString);
            URL url = new URL(request.url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            int length = conn.getContentLength();
            InputStream input = new BufferedInputStream(conn.getInputStream());
            FileOutputStream output = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            long total = 0;
            int count;
            long lastUpdateTime = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                output.write(buffer, 0, count);

                if (length > 0) {
                    int per = (int) (total * 100 / length);

                    // Cap the percentage at 99 inside the loop so it NEVER shows 100% until it actually completes
                    if (per >= 100) per = 99;

                    // Performance optimization: Only notify the system every 200ms to avoid clogging the OS thread
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime > 200) {
                        builder.setProgress(100, per, false);
                        notificationManager.notify(notificationId, builder.build());
                        lastUpdateTime = currentTime;
                    }
                }
            }

            output.flush();
            output.close();
            input.close();
            conn.disconnect();

            final File finalOutputFile = outputFile;
            final int finalNotificationId = notificationId;
            final String finalFileName = request.fileName;
            final String downloadCompletedString = ExtensionStrings.DOWNLOAD_COMPLETED + finalFileName;


            // 2. Trigger the Toast and Media Scanner separately so they can't break the notification update
            mainHandler.post(() -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(downloadCompletedString)
                        .setContentText(finalFileName)
                        .setOngoing(false) // This unlocks the swipe lock completely
                        .setProgress(0, 0, false); // Wipes the progress track bar layout away entirely
                // Force post the update layout
                notificationManager.notify(finalNotificationId, builder.build());

                try {
                    showToast(downloadCompletedString);
                } catch (Exception ignored) {}

                try {
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(finalOutputFile));
                    context.sendBroadcast(mediaScanIntent);
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            mainHandler.post(() -> showToast(ExtensionStrings.DOWNLOAD_ERROR + e.getMessage()));
            notificationManager.cancel(notificationId);
            PikoUtils.logger(e);
        } finally {
            isDownloading = false;
            processNext();
        }
    }

    private void showToast(String msg) {
        mainHandler.post(() -> PikoUtils.toast(msg));
    }
}