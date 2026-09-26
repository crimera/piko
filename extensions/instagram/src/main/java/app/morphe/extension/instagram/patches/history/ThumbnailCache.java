/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.history;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.Logger;

/** Async thumbnail loader with a memory cache, downsampling images to the card width. */
final class ThumbnailCache {

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 16)) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private ThumbnailCache() {
    }

    static void load(ImageView imageView, String url, int targetWidth) {
        imageView.setTag(url);
        imageView.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(download(url), targetWidth);
            } catch (Exception e) {
                Logger.printInfo(() -> "Thumbnail load failed: " + url, e);
            }
            if (bitmap == null) return;

            CACHE.put(url, bitmap);
            Bitmap result = bitmap;
            MAIN_HANDLER.post(() -> {
                if (url.equals(imageView.getTag())) {
                    imageView.setImageBitmap(result);
                }
            });
        });
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap decode(byte[] data, int targetWidth) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        int sampleSize = 1;
        while (targetWidth > 0 && options.outWidth / (sampleSize * 2) >= targetWidth) {
            sampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
