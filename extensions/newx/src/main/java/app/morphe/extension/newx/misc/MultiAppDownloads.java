package app.morphe.extension.newx.misc;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Utils;

/** Public-download compatibility for affected clone profiles, inside the patched application. */
public final class MultiAppDownloads {
    private static final String TAG = "PikoMultiAppDownload";
    private static final String CHANNEL = "piko_multiapp_downloads";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger IDS = new AtomicInteger(74000);

    private MultiAppDownloads() {}

    public static boolean isNeeded() {
        return isPatchApplied() && MultiAppTransfer.useAppProcess(
                Process.myUid(), Build.VERSION.SDK_INT, Build.MANUFACTURER);
    }

    // Replaced at patch time only when the clone compatibility patch is selected.
    static boolean isPatchApplied() {
        return false;
    }

    // Both bridges are resolved against exact APK bytecode when the patch is applied.
    private static Context nativeContext(Object owner) {
        throw new IllegalStateException("Native download context bridge was not patched");
    }

    private static void nativeCallback(Object callback, boolean success, String error, String uri) {
        throw new IllegalStateException("Native download callback bridge was not patched");
    }

    public static boolean tryNativeDownload(Object owner, Uri url, String title, String mimeType,
            Map<String, String> headers, String destination, Object callback, boolean privateFile) {
        // Private/offline cache downloads have another lifecycle and retain the native path.
        if (!isNeeded() || privateFile || (destination != null && !destination.isEmpty())) return false;
        Context context = nativeContext(owner);
        enqueue(context, url.toString(), title, mimeType, "Download/X/", headers, callback, null);
        return true;
    }

    enum EnqueueResult { QUEUED, SKIPPED, FAILED }

    static boolean pendingFileExists(String name, String mimeType) {
        return PENDING.contains(InlineDownloadButton.relativeDownloadPath(mimeType) + name);
    }

    static EnqueueResult enqueueInline(Context context, String url, String name, String mimeType,
            InlineDownloadButton.ConflictBehavior conflictBehavior) {
        return enqueue(context, url, name, mimeType, InlineDownloadButton.relativeDownloadPath(mimeType),
                Collections.emptyMap(), null, conflictBehavior);
    }

    private static EnqueueResult enqueue(Context context, String url, String name, String mimeType,
            String relativePath, Map<String, String> headers, Object callback,
            InlineDownloadButton.ConflictBehavior conflictBehavior) {
        String key = relativePath + name;
        boolean reserved = false;
        try {
            MultiAppTransfer.fileName(name);
            URL parsed = new URL(url);
            if (!parsed.getProtocol().equals("https") && !parsed.getProtocol().equals("http")) {
                throw new IOException("Unsupported download protocol");
            }
            if (!PENDING.add(key)) {
                // Do not merge native requests by filename: URLs/headers may differ.
                // Every native invocation still receives a terminal callback.
                complete(callback, false, "Already pending", null);
                return EnqueueResult.SKIPPED;
            }
            reserved = true;
            Context app = context.getApplicationContext();
            Context safe = app == null ? context : app;
            Map<String, String> safeHeaders = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
            EXECUTOR.execute(() -> transfer(safe, url, name, mimeType, relativePath, safeHeaders,
                    callback, conflictBehavior, key));
            Log.i(TAG, "Queued public media in app user=" + Process.myUid() / 100_000);
            record(safe, "queued path=" + relativePath + " user=" + Process.myUid() / 100_000);
            return EnqueueResult.QUEUED;
        } catch (Exception exception) {
            if (reserved) PENDING.remove(key);
            complete(callback, false, exception.getClass().getSimpleName(), null);
            Utils.showToastShort("Could not start download");
            Log.e(TAG, "Queue failed: " + exception.getClass().getSimpleName());
            record(context, "queue_error " + exception);
            return EnqueueResult.FAILED;
        }
    }

    @SuppressLint({"NewApi", "InlinedApi"}) // Reached only through isNeeded(), which requires API 29.
    private static void transfer(Context context, String address, String name, String requestedMime,
            String relativePath, Map<String, String> headers, Object callback,
            InlineDownloadButton.ConflictBehavior conflictBehavior, String key) {
        int notificationId = IDS.incrementAndGet();
        ContentResolver resolver = context.getContentResolver();
        Uri destination = null;
        HttpURLConnection connection = null;
        String mime = requestedMime;
        try {
            notifyProgress(context, notificationId, name, -1, 0, null, false);
            connection = MediaDownloadConnection.open(address, headers, requestedMime,
                    event -> record(context, event + " id=" + notificationId));
            long expected = connection.getContentLengthLong();
            record(context, "http_ready id=" + notificationId + " expected=" + expected);
            if (mime == null || mime.isEmpty()) {
                mime = connection.getContentType();
                if (mime != null) mime = mime.split(";", 2)[0].trim();
                if (mime == null || mime.isEmpty()) mime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substring(name.lastIndexOf('.') + 1));
                if (mime == null) mime = "application/octet-stream";
            }
            Uri collection = relativePath.startsWith("Download/")
                    ? MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : mime.startsWith("video/")
                    ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            destination = resolver.insert(collection, values);
            if (destination == null) throw new IOException("MediaStore insert returned null");
            record(context, "media_inserted id=" + notificationId);
            long[] last = {0};
            long count;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = resolver.openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("MediaStore output returned null");
                count = MultiAppTransfer.copy(input, output, expected, bytes -> {
                    long now = SystemClock.elapsedRealtime();
                    if (now - last[0] >= 750) {
                        last[0] = now;
                        notifyProgress(context, notificationId, name, expected, bytes, null, false);
                    }
                });
            }
            String allocatedName = InlineDownloadButton.mediaStoreDisplayName(resolver, destination);
            if (allocatedName == null) throw new IOException("MediaStore returned no display name");
            if (conflictBehavior == InlineDownloadButton.ConflictBehavior.SKIP
                    && InlineDownloadButton.mediaStoreAllocatedNameDiffers(name, allocatedName)) {
                // Inspect the real downloaded item instead of publishing a synthetic
                // one-byte probe, which fails in clone profiles. Hidden old-owner files
                // can still occupy a name even when the initial query returns no row.
                record(context, "skipped_existing id=" + notificationId);
                cancelNotification(context, notificationId);
                showResult("Already downloaded: " + name);
                return; // Finally discards only this new, still-pending item.
            }
            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(destination, published, null, null) != 1) {
                throw new IOException("MediaStore publish failed");
            }
            Uri publishedUri = destination;
            destination = null; // A published file must never enter transfer rollback.
            if (conflictBehavior == InlineDownloadButton.ConflictBehavior.OVERWRITE) {
                InlineDownloadButton.deleteExistingMedia(resolver, collection, name, relativePath, publishedUri);
            }
            PublishedMediaRefresh.request(context, publishedUri, mime);
            notifyProgress(context, notificationId, allocatedName, count, count, publishedUri, false);
            complete(callback, true, null, publishedUri.toString());
            showResult("Downloaded: " + allocatedName);
            Log.i(TAG, "Completed user=" + Process.myUid() / 100_000 + " bytes=" + count);
            record(context, "complete id=" + notificationId + " bytes=" + count);
        } catch (Exception exception) {
            complete(callback, false, exception.getClass().getSimpleName(), null);
            notifyProgress(context, notificationId, name, -1, 0, null, true);
            showResult("Download failed: " + name);
            // No URL, request headers or tokens in diagnostics.
            Log.e(TAG, "Transfer failed: " + exception.getClass().getSimpleName());
            record(context, "transfer_error id=" + notificationId + " " + exception);
        } finally {
            if (destination != null) {
                try { resolver.delete(destination, null, null); } catch (RuntimeException ignored) {}
            }
            if (connection != null) connection.disconnect();
            PENDING.remove(key);
        }
    }

    static synchronized void record(Context context, String text) {
        try {
            File file = new File(context.getFilesDir(), "piko-download.log");
            // Bound local diagnostics and remove URLs before writing. Request headers are never recorded.
            String sanitized = text.replaceAll("(?i)(?:https?|content|file)://[^\\s]+", "[uri]");
            try (FileWriter writer = new FileWriter(file, file.length() < 65536)) {
                writer.write(System.currentTimeMillis() + " " + sanitized + "\n");
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void complete(Object callback, boolean success, String error, String uri) {
        if (callback == null) return;
        try {
            new Handler(Looper.getMainLooper()).post(() -> {
                try { nativeCallback(callback, success, error, uri); }
                catch (RuntimeException exception) { Log.e(TAG, "Native callback failed: " + exception.getClass().getSimpleName()); }
            });
        } catch (RuntimeException exception) {
            Log.e(TAG, "Native callback dispatch failed: " + exception.getClass().getSimpleName());
        }
    }

    private static void showResult(String message) {
        try { Utils.showToastShort(message); }
        catch (RuntimeException exception) {
            Log.e(TAG, "Result notification failed: " + exception.getClass().getSimpleName());
        }
    }

    private static void cancelNotification(Context context, int id) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(id);
        } catch (RuntimeException ignored) {}
    }

    @SuppressLint("NotificationPermission") // Best effort; denied notification permission leaves transfer running.
    private static void notifyProgress(Context context, int id, String name, long total, long count,
            Uri completed, boolean failed) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Piko downloads", NotificationManager.IMPORTANCE_LOW));
            boolean running = completed == null && !failed;
            Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(running ? android.R.drawable.stat_sys_download : android.R.drawable.stat_sys_download_done)
                    .setContentTitle(name).setOnlyAlertOnce(true).setOngoing(running).setAutoCancel(!running);
            if (running) {
                int percent = total > 0 ? (int) Math.min(100, count * 100 / total) : 0;
                builder.setProgress(100, percent, total <= 0).setContentText(total > 0 ? percent + "%" : "Downloading…");
            } else {
                builder.setContentText(failed ? "Download failed" : "Download complete");
            }
            if (completed != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(completed).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                builder.setContentIntent(PendingIntent.getActivity(context, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException ignored) {
            // Notification permission does not control the transfer itself.
        }
    }
}
