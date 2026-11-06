/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Images of chat messages (photos, video covers, shared reel/post thumbnails) for notifications.
 * Downloaded once per item on their own thread, scaled down, and kept in the app's cache, where
 * {@link NotificationImageProvider} serves them to the system UI. Disappearing media never gets
 * here (see {@code DirectItem.imageUrl}).
 */
public final class NotificationImages {
    static final String AUTHORITY = "com.instagram.android.piko.notificationimages";
    private static final String DIR = "piko_notification_images";
    private static final int MAX_SIDE = 1080;
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int MAX_FILES = 200;
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60_000L;

    private static Handler worker;
    private static boolean purged;

    private NotificationImages() {
    }

    static boolean enabled() {
        try {
            return Pref.notificationImages();
        } catch (Throwable ex) {
            return false;
        }
    }

    /** The cached image of an item, or null when it has none (yet). */
    static Uri uriFor(String itemId) {
        File file = cached(itemId);
        return file == null ? null
                : new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build();
    }

    /** MIME type of an item's cached image, for the notification's message data. */
    static String mimeFor(String itemId) {
        File file = cached(itemId);
        return file != null && file.getName().endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static File cached(String itemId) {
        if (TextUtils.isEmpty(itemId) || !enabled()) return null;
        Context context = Utils.getContext();
        for (boolean png : new boolean[]{false, true}) {
            File file = file(context, itemId, png);
            if (file.isFile()) return file;
        }
        return null;
    }

    /**
     * Downloads an item's image if it is not cached yet, then runs {@code ready}. {@code png}:
     * kept lossless with its transparency (stickers), otherwise as JPEG.
     */
    static void fetch(String itemId, String url, boolean png, Runnable ready) {
        if (TextUtils.isEmpty(itemId) || TextUtils.isEmpty(url) || !enabled()) return;
        worker().post(() -> {
            Context context = Utils.getContext();
            if (cached(itemId) != null) return;
            File file = file(context, itemId, png);
            try {
                if (!purged) purge(dir(context));
                byte[] data = download(url);
                Bitmap bitmap = decode(data);
                if (bitmap == null) {
                    NotificationLog.event("IMAGE not decoded item=" + itemId);
                    return;
                }
                File tmp = new File(file.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 85, out);
                }
                bitmap.recycle();
                if (!tmp.renameTo(file)) {
                    tmp.delete();
                    return;
                }
                NotificationLog.event("IMAGE cached item=" + itemId + " bytes=" + file.length());
                if (ready != null) ready.run();
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to fetch a notification image", ex);
                NotificationLog.event("FAILED image item=" + itemId + ": " + ex);
            }
        });
    }

    static File dir(Context context) {
        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** Named by a hash of the item id, so the provider can accept only names of this shape. */
    private static File file(Context context, String itemId, boolean png) {
        return new File(dir(context), sha1(itemId) + (png ? ".png" : ".jpg"));
    }

    static boolean isCacheName(String name) {
        return name != null && name.matches("[0-9a-f]{40}\\.(jpg|png)");
    }

    /** A downloaded image scaled to at most {@code maxSide}, or null. Blocking: call off the main thread. */
    static Bitmap downloadBitmap(String url, int maxSide) throws Exception {
        Bitmap bitmap = decode(download(url));
        if (bitmap == null || Math.max(bitmap.getWidth(), bitmap.getHeight()) <= maxSide) return bitmap;
        float scale = (float) maxSide / Math.max(bitmap.getWidth(), bitmap.getHeight());
        return Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                Math.round(bitmap.getHeight() * scale), true);
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_BYTES) throw new IllegalStateException("image too large");
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= MAX_SIDE) {
            options.inSampleSize *= 2;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) return null;
        int side = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (side <= MAX_SIDE) return bitmap;
        float scale = (float) MAX_SIDE / side;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                Math.round(bitmap.getHeight() * scale), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    /** Drops images older than a week, then the oldest beyond the file limit. */
    private static void purge(File dir) {
        purged = true;
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = 0; i < files.length; i++) {
            if (i >= MAX_FILES || files[i].lastModified() < cutoff) files[i].delete();
        }
    }

    private static String sha1(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("piko-notification-images");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }
}
