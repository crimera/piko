/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;

public class MediaDownloader {
    private static final String CHANNEL_ID = "media_download_channel";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    // Separate downloader instances must reserve names before another request can choose them.
    private static final Object CREATE_DOCUMENT_LOCK = new Object();
    private final Context context;
    private final NotificationManager notificationManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MediaDownloader(Context context) {
        Context resolvedContext = context != null ? context : Utils.getContext();
        if (resolvedContext == null) {
            throw new IllegalStateException("Download context is unavailable");
        }

        Context applicationContext = resolvedContext.getApplicationContext();
        this.context = applicationContext != null ? applicationContext : resolvedContext;
        this.notificationManager = this.context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void enqueue(DownloadRequest request) {
        enqueueBatch(Collections.singletonList(request));
    }

    /** Saves the whole batch before starting the next request on this downloader. */
    public void enqueueBatch(List<DownloadRequest> requests) {
        List<DownloadRequest> batch = new ArrayList<>(requests);
        if (batch.isEmpty()) return;
        if (!StorageUtils.checkStoragePermissions()) {
            StorageUtils.allowStorageAccess();
            return;
        }
        executor.execute(() -> {
            for (DownloadRequest request : batch) {
                runDownloadTask(request);
            }
        });
    }

    private void runDownloadTask(DownloadRequest request) {
        int notificationId = (int) System.currentTimeMillis();
        Uri outputDocumentUri = null;
        List<File> cacheFiles = new ArrayList<>();
        boolean downloadCompleted = false;
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(ExtensionStrings.DOWNLOAD_ONGOING + request.fileName)
                    .setOngoing(true) // Keeps notification un-swipable during download execution.
                    .setProgress(100, 0, false);

            notificationManager.notify(notificationId, builder.build());

            synchronized (CREATE_DOCUMENT_LOCK) {
                Uri targetDirectoryUri = getTargetDirectoryUri(request);
                request.fileName = DownloadFileNames.findAvailable(
                        request.fileName, getChildNames(targetDirectoryUri)
                );
                outputDocumentUri = DocumentsContract.createDocument(
                        context.getContentResolver(),
                        targetDirectoryUri,
                        getMimeType(request.fileName),
                        request.fileName
                );
            }
            if (outputDocumentUri == null) {
                throw new IOException("Could not create download file");
            }

            String downloadStartString = ExtensionStrings.DOWNLOAD_ONGOING + request.fileName;
            builder.setContentTitle(downloadStartString);
            notificationManager.notify(notificationId, builder.build());
            showToast(downloadStartString);
            if (request.audioUrl == null && request.metadata == null) {
                try (OutputStream output = context.getContentResolver().openOutputStream(outputDocumentUri)) {
                    if (output == null) {
                        throw new IOException("Could not open download file");
                    }
                    download(request.url, output, builder, notificationId);
                } catch (IOException downloadException) {
                    if (request.fallbackUrl == null) throw downloadException;
                    PikoUtils.logger(downloadException);
                    try (OutputStream output = new BufferedOutputStream(openOutput(outputDocumentUri))) {
                        download(request.fallbackUrl, output, builder, notificationId);
                    }
                    request.completedToast = null;
                }
            } else {
                // Anything that needs rewriting is fetched to the cache first, then remuxed into place.
                boolean downloadedToCache = false;
                try {
                    downloadToCache(request.url, cacheFiles, builder, notificationId);
                    List<File> inputs = new ArrayList<>(cacheFiles);
                    if (request.audioUrl != null) {
                        downloadToCache(request.audioUrl, cacheFiles, builder, notificationId);
                        File audio = cacheFiles.get(cacheFiles.size() - 1);
                        inputs.add(request.audioToAacLc ? toAacLc(audio, cacheFiles) : audio);
                    }
                    downloadedToCache = true;
                    Mp4Remuxer.write(inputs, openOutput(outputDocumentUri), request.metadata);
                } catch (Exception | LinkageError downloadException) {
                    PikoUtils.logger(downloadException);
                    if (request.audioUrl == null) {
                        if (!downloadedToCache) {
                            throw downloadException;
                        }
                        // Only the metadata was lost: the file itself is complete.
                        copyRawFile(cacheFiles.get(0), outputDocumentUri);
                    } else if (request.fallbackUrl != null) {
                        try (OutputStream output = new BufferedOutputStream(openOutput(outputDocumentUri))) {
                            download(request.fallbackUrl, output, builder, notificationId);
                        }
                    } else {
                        throw new IOException("Could not download or merge video and audio", downloadException);
                    }
                }
            }
            downloadCompleted = true;

            final int finalNotificationId = notificationId;
            final String finalFileName = request.fileName;
            final String downloadCompletedString = ExtensionStrings.DOWNLOAD_COMPLETED + finalFileName;
            final String completedToast = request.completedToast != null ? request.completedToast : downloadCompletedString;
            // Built here and not in the posted task, it queries the provider for the mime type.
            final PendingIntent viewFileIntent =
                    buildViewFilePendingIntent(outputDocumentUri, finalFileName, finalNotificationId);

            mainHandler.post(() -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(downloadCompletedString)
                        .setContentText(finalFileName)
                        .setOngoing(false) // This unlocks the swipe lock completely
                        .setProgress(0, 0, false) // Wipes the progress track bar layout away entirely
                        .setAutoCancel(true)
                        .setContentIntent(viewFileIntent);
                // Force post the update layout
                notificationManager.notify(finalNotificationId, builder.build());

                try {
                    PikoUtils.toast(completedToast);
                } catch (Exception ignored) {}
            });
        } catch (Exception | LinkageError e) {
            if (!downloadCompleted && outputDocumentUri != null) {
                try {
                    DocumentsContract.deleteDocument(context.getContentResolver(), outputDocumentUri);
                } catch (Exception ignored) {}
            }
            PikoUtils.logger(e);
            try {
                showToast(ExtensionStrings.DOWNLOAD_ERROR + e.getMessage());
                notificationManager.cancel(notificationId);
            } catch (Exception notificationException) {
                PikoUtils.logger(notificationException);
            }
        } finally {
            for (File file : cacheFiles) {
                if (file.exists() && !file.delete()) {
                    PikoUtils.logger(new IOException("Could not delete download cache file"));
                }
            }
        }
    }

    private void copyDownload(
            InputStream input,
            OutputStream output,
            int length,
            Notification.Builder builder,
            int notificationId
    ) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;
        long lastUpdateTime = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            output.write(buffer, 0, count);

            if (length > 0) {
                int percent = (int) (total * 100 / length);
                if (percent >= 100) percent = 99;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime > 200) {
                    builder.setProgress(100, percent, false);
                    notificationManager.notify(notificationId, builder.build());
                    lastUpdateTime = currentTime;
                }
            }
        }
        output.flush();
    }

    /** Fetches {@code url} into {@code output}, reporting progress on the notification. */
    protected void download(String url, OutputStream output, Notification.Builder builder, int notificationId)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.connect();
            try (InputStream input = new BufferedInputStream(conn.getInputStream())) {
                copyDownload(input, output, conn.getContentLength(), builder, notificationId);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Fetches {@code url} into a new cache file, added to {@code cacheFiles} first so a failed fetch is cleaned up too. */
    private void downloadToCache(String url, List<File> cacheFiles, Notification.Builder builder, int notificationId)
            throws IOException {
        File cacheFile = File.createTempFile("piko-download-", ".mp4", context.getCacheDir());
        cacheFiles.add(cacheFile);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(cacheFile))) {
            download(url, output, builder, notificationId);
        }
    }

    /**
     * {@code audio} re-encoded as AAC-LC in a new cache file, or {@code audio} itself when it has
     * no xHE-AAC track or the conversion fails: the video then keeps its sound as served.
     */
    private File toAacLc(File audio, List<File> cacheFiles) {
        try {
            File converted = File.createTempFile("piko-download-", ".m4a", context.getCacheDir());
            cacheFiles.add(converted);
            return AacLcTranscoder.transcodeXheAac(audio, converted) ? converted : audio;
        } catch (Exception | LinkageError e) {
            PikoUtils.logger(e);
            return audio;
        }
    }

    /** Opens the download document for writing from its start, whatever an earlier attempt left in it. */
    private FileOutputStream openOutput(Uri outputUri) throws IOException {
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(outputUri, "rwt");
        if (descriptor == null) {
            throw new IOException("Could not open download file");
        }
        return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
    }

    private void copyRawFile(File inputFile, Uri outputUri) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(inputFile))) {
            try (OutputStream output = new BufferedOutputStream(openOutput(outputUri))) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
        }
    }

    // Opens the downloaded file in whichever app handles its mime type.
    private PendingIntent buildViewFilePendingIntent(Uri documentUri, String fileName, int requestCode) {
        try {
            String providerMimeType = context.getContentResolver().getType(documentUri);
            String mimeType = providerMimeType != null ? providerMimeType : getMimeType(fileName);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(documentUri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            return PendingIntent.getActivity(context, requestCode, viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } catch (Exception e) {
            // A notification without a tap action is better than crashing the app.
            PikoUtils.logger(e);
            return null;
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

    private Set<String> getChildNames(Uri parentUri) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri, DocumentsContract.getDocumentId(parentUri)
        );
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        Set<String> names = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) throw new IOException("Could not read download folder");
            while (cursor.moveToNext()) names.add(cursor.getString(0));
        }
        return names;
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
