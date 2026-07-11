/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
        Uri outputDocumentUri = null;
        boolean downloadCompleted = false;
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
            Uri targetDirectoryUri = getTargetDirectoryUri(request);
            if (findChildDocument(targetDirectoryUri, request.fileName, null) != null) {
                showToast(ExtensionStrings.DOWNLOAD_MEDIA_EXISTS);
                notificationManager.cancel(notificationId);
                return;
            }

            outputDocumentUri = DocumentsContract.createDocument(
                    context.getContentResolver(),
                    targetDirectoryUri,
                    getMimeType(request.fileName),
                    request.fileName
            );
            if (outputDocumentUri == null) {
                throw new IOException("Could not create download file");
            }

            showToast(downloadStartString);
            HttpURLConnection conn = null;
            try {
                URL url = new URL(request.url);
                conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int length = conn.getContentLength();
                try (InputStream input = new BufferedInputStream(conn.getInputStream());
                     OutputStream output = context.getContentResolver().openOutputStream(outputDocumentUri)) {
                    if (output == null) {
                        throw new IOException("Could not open download file");
                    }

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
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            downloadCompleted = true;

            final int finalNotificationId = notificationId;
            final String finalFileName = request.fileName;
            final String downloadCompletedString = ExtensionStrings.DOWNLOAD_COMPLETED + finalFileName;

            mainHandler.post(() -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(downloadCompletedString)
                        .setContentText(finalFileName)
                        .setOngoing(false) // This unlocks the swipe lock completely
                        .setProgress(0, 0, false); // Wipes the progress track bar layout away entirely
                // Force post the update layout
                notificationManager.notify(finalNotificationId, builder.build());

                try {
                    PikoUtils.toast(downloadCompletedString);
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            if (!downloadCompleted && outputDocumentUri != null) {
                try {
                    DocumentsContract.deleteDocument(context.getContentResolver(), outputDocumentUri);
                } catch (Exception ignored) {}
            }
            showToast(ExtensionStrings.DOWNLOAD_ERROR + e.getMessage());
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

    private Uri getTargetDirectoryUri(DownloadRequest request) throws Exception {
        Uri treeUri = StorageUtils.getDownloadTreeUri();
        if (treeUri == null) {
            throw new IOException("Download folder access is missing");
        }

        Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
        );

        if (request.subFolder == null || request.subFolder.isBlank()) {
            return parentUri;
        }

        for (String folderName : request.subFolder.split("/")) {
            if (!folderName.isBlank()) {
                parentUri = getOrCreateDirectory(parentUri, folderName);
            }
        }

        return parentUri;
    }

    private Uri getOrCreateDirectory(Uri parentUri, String folderName) throws Exception {
        Uri existingDirectoryUri = findChildDocument(parentUri, folderName, DocumentsContract.Document.MIME_TYPE_DIR);
        if (existingDirectoryUri != null) {
            return existingDirectoryUri;
        }

        Uri directoryUri = DocumentsContract.createDocument(
                context.getContentResolver(),
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                folderName
        );
        if (directoryUri == null) {
            throw new IOException("Could not create folder " + folderName);
        }

        return directoryUri;
    }

    private Uri findChildDocument(Uri parentUri, String displayName, String mimeType) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri,
                DocumentsContract.getDocumentId(parentUri)
        );
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return null;
            }

            while (cursor.moveToNext()) {
                String childDocumentId = cursor.getString(0);
                String childDisplayName = cursor.getString(1);
                String childMimeType = cursor.getString(2);
                boolean nameMatches = displayName.equals(childDisplayName);
                boolean mimeTypeMatches = mimeType == null || mimeType.equals(childMimeType);
                if (nameMatches && mimeTypeMatches) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, childDocumentId);
                }
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }

        return null;
    }

    private String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }
}
