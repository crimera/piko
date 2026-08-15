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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;

public class MediaDownloader {
    public static final String CHANNEL_ID = "media_download_channel";
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private final Context context;
    private final NotificationManager notificationManager;
    private final LinkedBlockingQueue<DownloadRequest> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;

    public interface BatchListener {
        void onProgress(int processed, int total);

        void onCompleted(int downloaded, int skipped, int failed);

        void onCancelled(int processed, int total);

        void onError(Throwable error);
    }

    public static final class BatchHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();
        private final AtomicReference<Thread> worker = new AtomicReference<>();

        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;

            HttpURLConnection currentConnection = connection.get();
            if (currentConnection != null) currentConnection.disconnect();

            Thread currentWorker = worker.get();
            if (currentWorker != null) currentWorker.interrupt();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        private void setWorker(Thread currentWorker) {
            worker.set(currentWorker);
            if (cancelled.get()) currentWorker.interrupt();
        }

        private void clearWorker(Thread currentWorker) {
            worker.compareAndSet(currentWorker, null);
        }

        private void setConnection(HttpURLConnection currentConnection) {
            connection.set(currentConnection);
            if (cancelled.get()) currentConnection.disconnect();
        }

        private void clearConnection(HttpURLConnection currentConnection) {
            connection.compareAndSet(currentConnection, null);
        }
    }

    private interface ProgressListener {
        void onProgress(int percent);
    }

    private enum DownloadOutcome {
        DOWNLOADED,
        ALREADY_EXISTS
    }

    private static final class DownloadCancelledException extends IOException {
        DownloadCancelledException() {
            super("Download cancelled");
        }
    }

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
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);
    }

    public void enqueue(DownloadRequest request) {
        if (!StorageUtils.checkStoragePermissions()) {
            StorageUtils.allowStorageAccess();
            return;
        }
        queue.add(request);
        processNext();
    }

    public BatchHandle downloadBatch(List<DownloadRequest> requests, BatchListener listener) {
        BatchHandle handle = new BatchHandle();
        List<DownloadRequest> batch = new ArrayList<>(requests);
        Thread worker = new Thread(
                () -> runBatch(batch, handle, listener),
                "Piko collection download"
        );
        handle.setWorker(worker);
        worker.start();
        return handle;
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
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID);
        String downloadStartString = ExtensionStrings.DOWNLOAD_ONGOING + request.fileName;
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(downloadStartString)
                .setOngoing(true) // Keeps notification un-swipable during download execution.
                .setProgress(100, 0, false);

        notificationManager.notify(notificationId, builder.build());

        try {
            long[] lastUpdateTime = new long[]{0L};
            DownloadOutcome outcome = downloadRequest(
                    request,
                    null,
                    () -> showToast(downloadStartString),
                    percent -> {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime[0] <= 200) return;
                        builder.setProgress(100, percent, false);
                        notificationManager.notify(notificationId, builder.build());
                        lastUpdateTime[0] = currentTime;
                    }
            );
            if (outcome == DownloadOutcome.ALREADY_EXISTS) {
                showToast(ExtensionStrings.DOWNLOAD_MEDIA_EXISTS);
                notificationManager.cancel(notificationId);
                return;
            }

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
            showToast(ExtensionStrings.DOWNLOAD_ERROR + e.getMessage());
            notificationManager.cancel(notificationId);
            PikoUtils.logger(e);
        } finally {
            isDownloading = false;
            processNext();
        }
    }

    private void runBatch(
            List<DownloadRequest> requests,
            BatchHandle handle,
            BatchListener listener
    ) {
        Thread worker = Thread.currentThread();
        int total = requests.size();
        int processed = 0;
        int downloaded = 0;
        int skipped = 0;
        int failed = 0;

        try {
            if (!StorageUtils.checkStoragePermissions()) {
                mainHandler.post(StorageUtils::allowStorageAccess);
                notifyBatchError(listener, new IOException("Download folder access is missing"));
                return;
            }

            for (DownloadRequest request : requests) {
                if (handle.isCancelled()) break;

                try {
                    DownloadOutcome outcome = downloadRequest(request, handle, null, null);
                    if (outcome == DownloadOutcome.DOWNLOADED) downloaded++;
                    else skipped++;
                } catch (DownloadCancelledException ignored) {
                    break;
                } catch (Exception error) {
                    failed++;
                    PikoUtils.logger(error);
                }

                processed++;
                notifyBatchProgress(listener, processed, total);
            }

            if (handle.isCancelled()) {
                notifyBatchCancelled(listener, processed, total);
            } else {
                notifyBatchCompleted(listener, downloaded, skipped, failed);
            }
        } catch (Throwable error) {
            PikoUtils.logger(error);
            notifyBatchError(listener, error);
        } finally {
            handle.clearWorker(worker);
            Thread.interrupted();
        }
    }

    private DownloadOutcome downloadRequest(
            DownloadRequest request,
            BatchHandle handle,
            Runnable onStarted,
            ProgressListener progressListener
    ) throws Exception {
        Uri outputDocumentUri = null;
        HttpURLConnection connection = null;

        try {
            throwIfCancelled(handle);
            Uri targetDirectoryUri = getTargetDirectoryUri(request);
            if (findChildDocument(targetDirectoryUri, request.fileName, null) != null) {
                return DownloadOutcome.ALREADY_EXISTS;
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
            if (onStarted != null) onStarted.run();

            URL url = new URL(request.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (handle != null) handle.setConnection(connection);
            throwIfCancelled(handle);
            connection.connect();

            long length = connection.getContentLengthLong();
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = context.getContentResolver().openOutputStream(outputDocumentUri)) {
                if (output == null) throw new IOException("Could not open download file");

                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    throwIfCancelled(handle);
                    downloaded += count;
                    output.write(buffer, 0, count);

                    if (length > 0 && progressListener != null) {
                        int percent = (int) Math.min(99L, downloaded * 100L / length);
                        progressListener.onProgress(percent);
                    }
                }
                throwIfCancelled(handle);
                output.flush();
            }

            return DownloadOutcome.DOWNLOADED;
        } catch (Exception error) {
            if (outputDocumentUri != null) {
                try {
                    DocumentsContract.deleteDocument(context.getContentResolver(), outputDocumentUri);
                } catch (Exception ignored) {}
            }
            if (handle != null && handle.isCancelled() && !(error instanceof DownloadCancelledException)) {
                throw new DownloadCancelledException();
            }
            throw error;
        } finally {
            if (handle != null && connection != null) handle.clearConnection(connection);
            if (connection != null) connection.disconnect();
        }
    }

    private static void throwIfCancelled(BatchHandle handle) throws DownloadCancelledException {
        if (handle != null && (handle.isCancelled() || Thread.currentThread().isInterrupted())) {
            throw new DownloadCancelledException();
        }
    }

    private static void notifyBatchProgress(BatchListener listener, int processed, int total) {
        try {
            listener.onProgress(processed, total);
        } catch (Throwable error) {
            PikoUtils.logger(error);
        }
    }

    private static void notifyBatchCompleted(
            BatchListener listener,
            int downloaded,
            int skipped,
            int failed
    ) {
        try {
            listener.onCompleted(downloaded, skipped, failed);
        } catch (Throwable error) {
            PikoUtils.logger(error);
        }
    }

    private static void notifyBatchCancelled(BatchListener listener, int processed, int total) {
        try {
            listener.onCancelled(processed, total);
        } catch (Throwable error) {
            PikoUtils.logger(error);
        }
    }

    private static void notifyBatchError(BatchListener listener, Throwable error) {
        try {
            listener.onError(error);
        } catch (Throwable listenerError) {
            PikoUtils.logger(listenerError);
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
