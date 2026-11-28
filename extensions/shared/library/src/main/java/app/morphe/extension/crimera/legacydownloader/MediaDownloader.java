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
import android.system.ErrnoException;
import android.system.OsConstants;

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
import java.util.function.Predicate;

import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;

public final class MediaDownloader {
    private static final String CHANNEL_ID = "media_download_channel";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long FOLDER_LOAD_TIMEOUT_MS = 15_000;
    private static final int MAX_LISTED_COMPLETIONS = 5;
    private static final String ACTION_COMPLETION_DISMISSED = "app.morphe.extension.crimera.DOWNLOAD_COMPLETION_DISMISSED";
    private static final int COMPLETION_INTENT_FLAGS = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
    private static MediaDownloader shared;

    private final Context context;
    private final NotificationManager notificationManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger notificationIds = new AtomicInteger((int) System.currentTimeMillis());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Main thread only: the completion notification, its tap intent, and the file names it lists, newest first, since the user last opened or dismissed it.
    private int completionNotificationId;
    private Intent completionViewIntent;
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
        enqueueBatch(requests, request -> true);
    }

    public void enqueueBatch(List<DownloadRequest> requests, Predicate<DownloadRequest> prepare) {
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
                runDownloadTask(request, prepare);
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

    private void runDownloadTask(DownloadRequest request, Predicate<DownloadRequest> prepare) {
        int notificationId = notificationIds.incrementAndGet();
        Uri outputDocumentUri = null;
        boolean downloadCompleted = false;
        boolean replacing = false;
        File replacement = null;
        File cover = null;
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
            cover = encodeFirstFrame(request);
            if (cover != null) {
                request.cachedMedia = new FileInputStream(cover);
            } else if (!prepare.test(request)) {
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
            final Intent viewFileIntent = buildViewFileIntent(outputDocumentUri, finalFileName);

            mainHandler.post(() -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(downloadCompletedString)
                        .setOngoing(false) // This unlocks the swipe lock completely
                        .setProgress(0, 0, false); // Wipes the progress track bar layout away entirely
                try {
                    showCompletion(builder, finalNotificationId, finalFileName, viewFileIntent);
                } catch (Exception e) {
                    PikoUtils.logger(e);
                }

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
            closeCached(request.cachedMedia);
            closeCached(request.cachedAudio);
            if (cover != null) cover.delete();
            if (replacement != null) replacement.delete();
        }
    }

    /**
     * Replaces the previous completion with {@code fileName}'s under the new {@code notificationId}, listing the
     * completions since the user last opened or dismissed it. Android drops updates over its rate limit and new
     * notifications past 50 per app. Main thread only.
     */
    private void showCompletion(Notification.Builder builder, int notificationId, String fileName, Intent viewIntent) {
        if (completionNotificationId != 0) {
            // Tapping sends the one-shot view intent and swiping the delete intent. Android forgets a one-shot intent
            // once sent or once its notification is gone. A delete receiver misses taps, and getActiveNotifications()
            // misses a just-posted notification.
            boolean dismissed = PendingIntent.getBroadcast(context, completionNotificationId,
                    completionDismissedIntent(), COMPLETION_INTENT_FLAGS | PendingIntent.FLAG_NO_CREATE) == null;
            boolean opened = completionViewIntent != null && PendingIntent.getActivity(context,
                    completionNotificationId, completionViewIntent,
                    COMPLETION_INTENT_FLAGS | PendingIntent.FLAG_NO_CREATE) == null;
            if (dismissed || opened) completedFileNames.clear();
            notificationManager.cancel(completionNotificationId);
        }

        completedFileNames.addFirst(fileName);
        if (completedFileNames.size() > MAX_LISTED_COMPLETIONS) completedFileNames.removeLast();
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (String name : completedFileNames) style.addLine(name);
        builder.setContentText(String.join(", ", completedFileNames))
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(viewIntent == null ? null
                        : PendingIntent.getActivity(context, notificationId, viewIntent, COMPLETION_INTENT_FLAGS))
                .setDeleteIntent(PendingIntent.getBroadcast(context, notificationId, completionDismissedIntent(),
                        COMPLETION_INTENT_FLAGS));
        notificationManager.notify(notificationId, builder.build());
        completionNotificationId = notificationId;
        completionViewIntent = viewIntent;
    }

    /** Nothing receives it: sending it only marks the swiped notification's one-shot delete intent as used. */
    private Intent completionDismissedIntent() {
        return new Intent(ACTION_COMPLETION_DISMISSED).setPackage(context.getPackageName());
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

    private static void closeCached(InputStream stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException e) {
            PikoUtils.logger(e);
        }
    }

    private void downloadSources(DownloadRequest request, Uri outputUri,
                                 Notification.Builder builder, int notificationId) throws Exception {
        boolean ranked = !request.qualitySources.isEmpty();
        List<DownloadRequest.Source> sources = ranked ? request.sourcesByNetworkCost() : legacySources(request);
        if (sources.isEmpty()) throw new IOException("No available download source");
        Map<String, File> staged = new HashMap<>();
        try {
            for (int i = 0; i < sources.size(); i++) {
                DownloadRequest.Source source = sources.get(i);
                List<DownloadRequest.Source> remaining = sources.subList(i + 1, sources.size());
                try {
                    downloadSource(source, request, outputUri, builder, notificationId, i > 0, staged,
                            ranked ? remaining : Collections.emptyList());
                    return;
                } catch (Exception | LinkageError e) {
                    if (ranked && e instanceof UnavailableSource) {
                        String failedUrl = ((UnavailableSource) e).url;
                        remaining.removeIf(candidate -> candidate.uses(failedUrl));
                    }
                    if (failedStorage(e) || remaining.isEmpty()) throw e;
                    staged.entrySet().removeIf(entry -> {
                        if (remaining.stream().anyMatch(candidate -> candidate.uses(entry.getKey()))) return false;
                        deleteCacheFile(entry.getValue());
                        return true;
                    });
                    if (!staged.isEmpty()) {
                        List<DownloadRequest.Source> next = request.sourcesByNetworkCost(new HashSet<>(remaining), staged.keySet());
                        if (next.isEmpty()) throw e;
                        remaining.clear();
                        remaining.addAll(next);
                    }
                    PikoUtils.logger(e);
                }
            }
        } finally {
            for (File file : staged.values()) deleteCacheFile(file);
        }
    }

    private static List<DownloadRequest.Source> legacySources(DownloadRequest request) {
        List<DownloadRequest.Source> sources = new ArrayList<>();
        DownloadRequest.Source primary = new DownloadRequest.Source(request.url, request.audioUrl);
        DownloadRequest.Source cached = request.cachedSource;
        boolean cachedAlternative = cached != null && !cached.equals(primary);
        if (cachedAlternative) sources.add(new DownloadRequest.Source(cached.url, cached.audioUrl, true));
        sources.add(primary);
        if (request.cachedMedia == null || cached != null) {
            sources.addAll(request.fallbackSources);
            if (request.fallbackUrl != null) sources.add(new DownloadRequest.Source(request.fallbackUrl, null));
        }
        return sources;
    }

    private static boolean failedStorage(Throwable failure) {
        for (Throwable error = failure; error != null; error = error.getCause()) {
            if (error instanceof OutputFailure || error instanceof SecurityException) return true;
            if (error instanceof ErrnoException) {
                int errno = ((ErrnoException) error).errno;
                if (errno == OsConstants.ENOSPC || errno == OsConstants.EROFS || errno == OsConstants.EIO
                        || errno == OsConstants.EACCES || errno == OsConstants.EFBIG) return true;
            }
        }
        return false;
    }

    private void downloadSource(DownloadRequest.Source source, DownloadRequest request, Uri outputUri,
                                Notification.Builder builder, int notificationId, boolean retry,
                                Map<String, File> staged, List<DownloadRequest.Source> remaining) throws Exception {
        boolean cacheOnly = source.cacheOnly;
        if (source.audioUrl == null && request.metadata == null) {
            // Plain downloads also support providers offering write-only streams, not seekable files.
            DownloadOutput destination = truncate -> truncate
                    ? context.getContentResolver().openOutputStream(outputUri, "wt")
                    : context.getContentResolver().openOutputStream(outputUri);
            File complete = staged.get(source.url);
            if (complete != null) {
                try (InputStream input = new FileInputStream(complete)) {
                    copyDownload(input, destination, retry, false, -1, builder, notificationId);
                }
            } else {
                download(request, source.url, retry ? null : request.cachedMedia, destination, retry,
                        cacheOnly, builder, notificationId);
            }
            return;
        }
        List<File> cacheFiles = new ArrayList<>();
        File media = null;
        try {
            media = downloadToCache(request, source.url, retry ? null : request.cachedMedia, cacheOnly,
                    cacheFiles, staged, remaining, builder, notificationId);
            List<File> inputs = new ArrayList<>();
            inputs.add(media);
            if (source.audioUrl != null) {
                File audio = downloadToCache(request, source.audioUrl, retry ? null : request.cachedAudio, cacheOnly,
                        cacheFiles, staged, remaining, builder, notificationId);
                inputs.add(request.audioToAacLc ? toAacLc(audio, cacheFiles) : audio);
            }
            Mp4Remuxer.write(inputs, openOutput(outputUri), request.metadata);
        } catch (Exception | LinkageError e) {
            if (failedStorage(e) || source.audioUrl != null || media == null
                    || !Mp4Remuxer.isReadableVideo(media)) throw e;
            // Keep a readable original without metadata when rewriting it failed.
            PikoUtils.logger(e);
            copyRawFile(media, outputUri);
        } finally {
            for (File file : cacheFiles) deleteCacheFile(file);
        }
    }

    private static void deleteCacheFile(File file) {
        if (file.exists() && !file.delete()) PikoUtils.logger(new IOException("Could not delete download cache file"));
    }

    @FunctionalInterface
    private interface DownloadOutput {
        OutputStream open(boolean truncate) throws IOException;
    }

    private static final class OutputFailure extends IOException {
        OutputFailure(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static final class UnavailableSource extends IOException {
        final String url;

        UnavailableSource(String url, int status) {
            super("Download failed (HTTP " + status + ")");
            this.url = url;
        }
    }

    /** A cache eviction can restart from byte zero. A failed destination must not cause a network retry. */
    private boolean copyDownload(InputStream input, DownloadOutput destination, boolean truncate, boolean fromCache,
                                 int length, Notification.Builder builder, int notificationId) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        long lastUpdateTime = 0;
        IOException inputFailure;
        try {
            OutputStream stream = destination.open(truncate);
            if (stream == null) throw new IOException("Could not open download file");
            try (OutputStream output = new BufferedOutputStream(stream)) {
                while (true) {
                    int count;
                    try {
                        count = input.read(buffer);
                    } catch (IOException e) {
                        inputFailure = e;
                        break;
                    }
                    if (count == -1) return true;
                    total += count;
                    output.write(buffer, 0, count);

                    if (length > 0) {
                        int percent = (int) Math.min(99, total * 100 / length);
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateTime > 200) {
                            builder.setProgress(100, percent, false);
                            notificationManager.notify(notificationId, builder.build());
                            lastUpdateTime = currentTime;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new OutputFailure(e);
        }
        if (!fromCache) throw inputFailure;
        return false;
    }

    /** The request's first-frame cover as a cache JPEG the caller deletes, or null to save {@code url} instead. */
    private File encodeFirstFrame(DownloadRequest request) {
        if (request.firstFrameVideo == null) return null;
        File video = null;
        File picture = null;
        Bitmap frame = null;
        boolean encoded = false;
        try {
            try (InputStream cached = request.firstFrameVideo.call()) {
                if (cached == null) return null;
                video = File.createTempFile("piko-cover-", ".mp4", context.getCacheDir());
                Files.copy(cached, video.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    private void download(DownloadRequest request, String url, InputStream prepared, DownloadOutput output,
                          boolean truncate, boolean cacheOnly, Notification.Builder builder, int notificationId) throws IOException {
        InputStream cached = prepared;
        if (cached == null && request.localSource != null) {
            try {
                cached = request.localSource.open(url);
            } catch (IOException ignored) {
                // The item can disappear before its cache entry is opened.
            }
        }
        if (cached != null) {
            try {
                if (copyDownload(cached, output, truncate, true, -1, builder, notificationId)) return;
            } finally {
                // The request owns prepared streams and closes them after all file operations.
                if (prepared == null) closeCached(cached);
            }
            if (request.localSource == null) throw new IOException("Could not read local media");
            truncate = true;
        }
        if (cacheOnly) throw new IOException("Could not read local media");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            if (request.userAgent != null) {
                String userAgent = request.userAgent.apply(context);
                if (userAgent != null) conn.setRequestProperty("User-Agent", userAgent);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.connect();
            if (!request.qualitySources.isEmpty()) {
                int status = conn.getResponseCode();
                if (status == 403 || status == 404 || status == 410) throw new UnavailableSource(url, status);
            }
            try (InputStream input = new BufferedInputStream(conn.getInputStream())) {
                copyDownload(input, output, truncate, false, conn.getContentLength(), builder, notificationId);
            }
        } finally {
            conn.disconnect();
        }
    }

    private File downloadToCache(DownloadRequest request, String url, InputStream prepared, boolean cacheOnly,
                                 List<File> cacheFiles, Map<String, File> staged, List<DownloadRequest.Source> remaining,
                                 Notification.Builder builder, int notificationId)
            throws IOException {
        File complete = staged.get(url);
        if (complete != null) return complete;
        File cacheFile;
        try {
            cacheFile = File.createTempFile("piko-download-", ".mp4", context.getCacheDir());
        } catch (IOException e) {
            throw new OutputFailure(e);
        }
        cacheFiles.add(cacheFile);
        download(request, url, prepared, truncate -> new FileOutputStream(cacheFile), false, cacheOnly, builder, notificationId);
        if (remaining.stream().anyMatch(source -> source.uses(url))) {
            staged.put(url, cacheFile);
            cacheFiles.remove(cacheFile);
        }
        return cacheFile;
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
        try {
            ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(outputUri, "rwt");
            if (descriptor == null) throw new IOException("Could not open download file");
            return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
        } catch (IOException e) {
            throw new OutputFailure(e);
        }
    }

    private void copyRawFile(File inputFile, Uri outputUri) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(inputFile))) {
            copyDownload(input, truncate -> openOutput(outputUri), true, false, -1, null, 0);
        }
    }

    // Opens the downloaded file in whichever app handles its mime type.
    private Intent buildViewFileIntent(Uri documentUri, String fileName) {
        try {
            String providerMimeType = context.getContentResolver().getType(documentUri);
            String mimeType = providerMimeType != null ? providerMimeType : getMimeType(fileName);
            return new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(documentUri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
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
