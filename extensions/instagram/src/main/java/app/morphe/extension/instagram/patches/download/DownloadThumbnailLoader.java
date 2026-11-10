/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DownloadThumbnailLoader implements AutoCloseable {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    void load(String url, ImageView view) {
        if (closed || url == null || url.isEmpty()) return;
        workers.execute(() -> {
            Bitmap bitmap = fetch(url);
            if (bitmap == null) return;
            if (closed) {
                bitmap.recycle();
                return;
            }
            view.post(() -> {
                if (closed || !view.isAttachedToWindow() || !url.equals(view.getTag())) {
                    bitmap.recycle();
                    return;
                }
                view.clearColorFilter();
                view.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap fetch(String url) {
        HttpURLConnection connection = null;
        try {
            if (closed) return null;
            connection = (HttpURLConnection) new URL(url).openConnection();
            connections.add(connection);
            if (closed) return null;
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK
                    || connection.getContentLengthLong() > MAX_BYTES) return null;
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while (!closed && (length = input.read(buffer)) != -1) {
                    if (output.size() + length > MAX_BYTES) return null;
                    output.write(buffer, 0, length);
                }
                if (closed) return null;
                byte[] data = output.toByteArray();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) return null;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        } finally {
            if (connection != null) {
                connections.remove(connection);
                connection.disconnect();
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        workers.shutdownNow();
        for (HttpURLConnection connection : connections) connection.disconnect();
    }
}
