/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.legacydownloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.service.notification.StatusBarNotification;

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
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;

public final class MediaDownloader {
    private static final String CHANNEL_ID = "media_download_channel";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long FOLDER_LOAD_TIMEOUT_MS = 15_000;
    private static final int MAX_LISTED_COMPLETIONS = 5;
    private static MediaDownloader shared;

    private final Context context;
    private final NotificationManager notificationManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger notificationIds = new AtomicInteger((int) System.currentTimeMillis());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Main thread only: the completion notification and the file names it lists, newest first.
    private int completionNotificationId;
    private final Deque<String> completedFileNames = new ArrayDeque<>();

    public static synchronized MediaDownloader shared(Context context) {
        if (shared == null) shared = new MediaDownloader(context);
        return shared;
    }

    private MediaDownloader(Context context) {
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

    /** Saves the requests in order, after every batch enqueued before it. Same names in a batch are numbered apart, around existing folders, before the conflict policy applies, so a rerun meets the same names. */
    public void enqueueBatch(List<DownloadRequest> requests) {
        List<DownloadRequest> batch = new ArrayList<>(requests);
        if (batch.isEmpty()) return;
        if (!StorageUtils.checkStoragePermissions()) {
            StorageUtils.allowStorageAccess();
            return;
        }
        executor.execute(() -> {
            Map<String, Set<String>> claimedBySubFolder = new HashMap<>();
            try {
                for (DownloadRequest request : batch) {
                    if (request.conflictPolicy == DownloadRequest.ConflictPolicy.RENAME) continue;
                    Set<String> claimed = claimedBySubFolder.get(request.subFolder);
                    if (claimed == null) {
                        claimed = directoryNames(request);
                        claimedBySubFolder.put(request.subFolder, claimed);
                    }
                    request.fileName = DownloadFileNames.findAvailable(request.fileName, claimed);
                    claimed.add(request.fileName);
                }
            } catch (Exception | LinkageError e) {
                PikoUtils.logger(e);
                showToast(ExtensionStrings.DOWNLOAD_ERROR + e.getMessage());
                return;
            }
            for (DownloadRequest request : batch) {
                runDownloadTask(request);
            }
        });
    }

    private Set<String> directoryNames(DownloadRequest request) throws Exception {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Uri> child : getChildDocuments(getTargetDirectoryUri(request)).entrySet()) {
            if (child.getValue() == null) names.add(child.getKey());
        }
        return names;
    }

    private void runDownloadTask(DownloadRequest request) {
        int notificationId = notificationIds.incrementAndGet();
        Uri outputDocumentUri = null;
        boolean downloadCompleted = false;
        boolean replacing = false;
        File replacement = null;
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

            Uri targetDirectoryUri = getTargetDirectoryUri(request);
            Map<String, Uri> children = getChildDocuments(targetDirectoryUri);
            Uri existing = request.conflictPolicy == DownloadRequest.ConflictPolicy.RENAME ? null
                    : children.get(request.fileName);
            if (existing != null && request.conflictPolicy == DownloadRequest.ConflictPolicy.SKIP) {
                showToast(ExtensionStrings.DOWNLOAD_MEDIA_EXISTS);
                return;
            }
            replacing = existing != null && request.conflictPolicy == DownloadRequest.ConflictPolicy.OVERWRITE;
            if (replacing) {
                outputDocumentUri = existing;
            } else {
                request.fileName = DownloadFileNames.findAvailable(request.fileName, children.keySet());
                outputDocumentUri = DocumentsContract.createDocument(
                        context.getContentResolver(), targetDirectoryUri,
                        getMimeType(request.fileName), request.fileName
                );
            }
            if (outputDocumentUri == null) {
                throw new IOException("Could not create download file");
            }

            String downloadStartString = ExtensionStrings.DOWNLOAD_ONGOING + request.fileName;
            builder.setContentTitle(downloadStartString);
            notificationManager.notify(notificationId, builder.build());
            showToast(downloadStartString);
            if (replacing) {
                // Finish all network and conversion work before touching the saved file.
                replacement = File.createTempFile("piko-replacement-", ".tmp", context.getCacheDir());
                downloadSources(request, Uri.fromFile(replacement), builder, notificationId);
                replaceDocument(replacement, outputDocumentUri);
            } else {
                downloadSources(request, outputDocumentUri, builder, notificationId);
            }
            downloadCompleted = true;

            notificationManager.cancel(notificationId);
            final int finalNotificationId = notificationIds.incrementAndGet();
            final String finalFileName = request.fileName;
            final String downloadCompletedString = ExtensionStrings.DOWNLOAD_COMPLETED + finalFileName;
            // Built here and not in the posted task, it queries the provider for the mime type.
            final PendingIntent viewFileIntent =
                    buildViewFilePendingIntent(outputDocumentUri, finalFileName, finalNotificationId);

            mainHandler.post(() -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(downloadCompletedString)
                        .setOngoing(false) // This unlocks the swipe lock completely
                        .setProgress(0, 0, false) // Wipes the progress track bar layout away entirely
                        .setAutoCancel(true)
                        .setContentIntent(viewFileIntent);
                showCompletion(builder, finalNotificationId, finalFileName);

                try {
                    PikoUtils.toast(downloadCompletedString);
                } catch (Exception ignored) {}
            });
        } catch (Exception | LinkageError e) {
            if (!downloadCompleted && !replacing && outputDocumentUri != null) {
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
            if (replacement != null) replacement.delete();
        }
    }

    /**
     * Posts {@code fileName}'s completion under the new {@code notificationId}, listing the completions still showing
     * in it. Android drops updates over its rate limit and new notifications past 50 per app. Main thread only.
     */
    private void showCompletion(Notification.Builder builder, int notificationId, String fileName) {
        boolean showing = false;
        for (StatusBarNotification active : notificationManager.getActiveNotifications()) {
            if (active.getId() == completionNotificationId) showing = true;
        }
        if (showing && !completedFileNames.isEmpty()) {
            notificationManager.cancel(completionNotificationId);
        } else {
            completedFileNames.clear();
        }
        completedFileNames.addFirst(fileName);
        if (completedFileNames.size() > MAX_LISTED_COMPLETIONS) completedFileNames.removeLast();
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String name : completedFileNames) style.addLine(name);
        builder.setContentText(String.join(", ", completedFileNames)).setStyle(style);
        notificationManager.notify(notificationId, builder.build());
        completionNotificationId = notificationId;
    }

    private void replaceDocument(File replacement, Uri destination) throws IOException {
        File backup = File.createTempFile("piko-backup-", ".tmp", context.getCacheDir());
        boolean keepBackup = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(destination);
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(backup))) {
                if (input == null) throw new IOException("Could not read existing download");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            try {
                copyRawFile(replacement, destination);
            } catch (IOException | RuntimeException failure) {
                // SAF has no atomic replace; restore the original if writing the replacement fails.
                try {
                    copyRawFile(backup, destination);
                } catch (IOException | RuntimeException restoreFailure) {
                    keepBackup = true;
                    failure.addSuppressed(new IOException("Original download backup retained at " + backup, restoreFailure));
                }
                throw failure;
            }
        } finally {
            if (!keepBackup) backup.delete();
        }
    }

    private void downloadSources(DownloadRequest request, Uri outputUri,
                                 Notification.Builder builder, int notificationId) throws Exception {
        List<DownloadRequest.Source> sources = new ArrayList<>();
        sources.add(new DownloadRequest.Source(request.url, request.audioUrl));
        sources.addAll(request.fallbackSources);
        if (request.fallbackUrl != null) sources.add(new DownloadRequest.Source(request.fallbackUrl, null));
        for (int i = 0; i < sources.size(); i++) {
            try {
                downloadSource(sources.get(i), request, outputUri, builder, notificationId, i > 0);
                return;
            } catch (Exception | LinkageError e) {
                if (i == sources.size() - 1) throw e;
                PikoUtils.logger(e);
            }
        }
    }

    private void downloadSource(DownloadRequest.Source source, DownloadRequest request, Uri outputUri,
                                Notification.Builder builder, int notificationId, boolean retry) throws Exception {
        if (source.audioUrl == null && request.metadata == null) {
            File cover = retry ? null : encodeFirstFrame(request, builder, notificationId);
            try {
                // Plain downloads also support providers offering write-only streams, not seekable files.
                OutputStream stream = retry
                        ? context.getContentResolver().openOutputStream(outputUri, "wt")
                        : context.getContentResolver().openOutputStream(outputUri);
                if (stream == null) throw new IOException("Could not open download file");
                try (OutputStream output = new BufferedOutputStream(stream)) {
                    if (cover != null) {
                        Files.copy(cover.toPath(), output);
                    } else {
                        download(source.url, output, builder, notificationId);
                    }
                }
            } finally {
                if (cover != null) cover.delete();
            }
            return;
        }
        // Cache and clean up each rendition independently so failed tracks never enter the next merge.
        List<File> cacheFiles = new ArrayList<>();
        boolean downloadedToCache = false;
        try {
            downloadToCache(source.url, cacheFiles, builder, notificationId);
            List<File> inputs = new ArrayList<>(cacheFiles);
            if (source.audioUrl != null) {
                downloadToCache(source.audioUrl, cacheFiles, builder, notificationId);
                File audio = cacheFiles.get(cacheFiles.size() - 1);
                inputs.add(request.audioToAacLc ? toAacLc(audio, cacheFiles) : audio);
            }
            downloadedToCache = true;
            Mp4Remuxer.write(inputs, openOutput(outputUri), request.metadata);
        } catch (Exception | LinkageError e) {
            if (source.audioUrl != null || !downloadedToCache
                    || !Mp4Remuxer.isReadableVideo(cacheFiles.get(0))) throw e;
            // Keep a readable original without metadata when rewriting it failed.
            PikoUtils.logger(e);
            copyRawFile(cacheFiles.get(0), outputUri);
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

    /** The request's first-frame cover as a cache JPEG the caller deletes, or null to save {@code url} instead. */
    private File encodeFirstFrame(DownloadRequest request, Notification.Builder builder, int notificationId) {
        if (request.firstFrameVideoUrl == null) return null;
        File video = null;
        File picture = null;
        Bitmap frame = null;
        boolean encoded = false;
        try {
            video = File.createTempFile("piko-cover-", ".mp4", context.getCacheDir());
            try (OutputStream cached = new FileOutputStream(video)) {
                download(request.firstFrameVideoUrl, cached, builder, notificationId);
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(video.getAbsolutePath());
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
            } finally {
                retriever.release();
            }
            if (frame == null || (long) frame.getWidth() * frame.getHeight() <= request.firstFrameMinPixels) {
                return null;
            }
            picture = File.createTempFile("piko-cover-", ".jpg", context.getCacheDir());
            try (OutputStream jpeg = new FileOutputStream(picture)) {
                encoded = frame.compress(Bitmap.CompressFormat.JPEG, 95, jpeg);
            }
            return encoded ? picture : null;
        } catch (Exception | LinkageError | OutOfMemoryError e) {
            PikoUtils.logger(e);
            return null;
        } finally {
            if (frame != null) frame.recycle();
            if (video != null) video.delete();
            if (!encoded && picture != null) picture.delete();
        }
    }

    /** Fetches {@code url} into {@code output}, reporting progress on the notification. */
    private void download(String url, OutputStream output, Notification.Builder builder, int notificationId)
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
        Uri existingDirectoryUri = findChildDirectory(parentUri, folderName);
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

    /** The folder's children as display name, document id and mime type, once its provider has loaded them all without an error. */
    private Cursor queryChildren(Uri parentUri) throws IOException {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri, DocumentsContract.getDocumentId(parentUri)
        );
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        long deadline = SystemClock.uptimeMillis() + FOLDER_LOAD_TIMEOUT_MS;
        while (true) {
            Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null);
            if (cursor == null) throw new IOException("Could not read download folder");
            Bundle extras = cursor.getExtras() != null ? cursor.getExtras() : Bundle.EMPTY;
            String error = extras.getString(DocumentsContract.EXTRA_ERROR);
            if (error == null && !extras.getBoolean(DocumentsContract.EXTRA_LOADING)) return cursor;
            cursor.close();
            if (error != null) throw new IOException(error);
            if (SystemClock.uptimeMillis() > deadline) throw new IOException("Download folder is still loading");
            SystemClock.sleep(250);
        }
    }

    private Map<String, Uri> getChildDocuments(Uri parentUri) throws IOException {
        Map<String, Uri> children = new HashMap<>();
        try (Cursor cursor = queryChildren(parentUri)) {
            while (cursor.moveToNext()) {
                // Directories reserve names too, but must never be treated as downloaded media.
                Uri document = DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)) ? null
                        : DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(1));
                children.put(cursor.getString(0), document);
            }
        }
        return children;
    }

    private Uri findChildDirectory(Uri parentUri, String displayName) throws IOException {
        try (Cursor cursor = queryChildren(parentUri)) {
            while (cursor.moveToNext()) {
                if (displayName.equals(cursor.getString(0))
                        && DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(1));
                }
            }
        }

        return null;
    }

    private String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }
}
