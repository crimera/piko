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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;

final class DownloadThumbnailLoader implements AutoCloseable {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final ExecutorService cacheWorkers = Executors.newFixedThreadPool(2);
    private final ExecutorService networkWorkers = Executors.newFixedThreadPool(2);
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    void load(List<String> urls, ImageView view) {
        if (closed || urls.isEmpty()) return;
        cacheWorkers.execute(() -> {
            Bitmap cached = findCached(urls);
            if (cached != null) show(cached, urls, view);
            else queueNetwork(urls, view);
        });
    }

    private synchronized void queueNetwork(List<String> urls, ImageView view) {
        if (closed) return;
        networkWorkers.execute(() -> {
            Bitmap bitmap = fetch(urls, view);
            if (bitmap != null) show(bitmap, urls, view);
            else if (!closed) Logger.printInfo(() -> "Could not load download selection preview");
        });
    }

    private void show(Bitmap bitmap, List<String> urls, ImageView view) {
        if (closed) {
            bitmap.recycle();
            return;
        }
        view.post(() -> {
            if (closed || !view.isAttachedToWindow() || urls != view.getTag()) {
                bitmap.recycle();
                return;
            }
            view.clearColorFilter();
            view.setImageBitmap(bitmap);
        });
    }

    private Bitmap findCached(List<String> urls) {
        for (String url : urls) {
            if (closed) return null;
            try (InputStream cached = InstagramImageCache.open(url)) {
                if (cached != null) {
                    Bitmap bitmap = decode(cached);
                    if (bitmap != null) return bitmap;
                }
            } catch (IOException | RuntimeException ignored) {
                // The cache entry can disappear while opening it.
            }
        }
        return null;
    }

    private Bitmap fetch(List<String> urls, ImageView view) {
        String agent = InstagramDownloadBatch.userAgent(view.getContext());
        for (int attempt = 0; attempt < 2 && !closed; attempt++) {
            String url = urls.get(Math.min(attempt, urls.size() - 1));
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connections.add(connection);
                if (closed) return null;
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                if (agent != null) connection.setRequestProperty("User-Agent", agent);
                int status = connection.getResponseCode();
                if (status == 408 || status == 502 || status == 504) continue;
                if (status == HttpURLConnection.HTTP_OK) {
                    if (connection.getContentLengthLong() <= MAX_BYTES) {
                        try (InputStream input = connection.getInputStream()) {
                            Bitmap bitmap = decode(input);
                            if (bitmap != null) return bitmap;
                        }
                    }
                } else if (status != 404 && status != 410) {
                    return null;
                }
                if (urls.size() == 1) return null;
            } catch (IOException ignored) {
                // Retry a transient read failure once, even if the item has only one URL.
            } catch (RuntimeException ignored) {
                return null;
            } finally {
                if (connection != null) {
                    connections.remove(connection);
                    connection.disconnect();
                }
            }
        }
        return null;
    }

    private Bitmap decode(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
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
    }

    @Override
    public synchronized void close() {
        closed = true;
        cacheWorkers.shutdownNow();
        networkWorkers.shutdownNow();
        for (HttpURLConnection connection : connections) connection.disconnect();
    }
}
