package app.morphe.extension.newx.misc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;

/** Loads small media previews without blocking the UI thread. */
public final class MediaThumbnailLoader {
    private static final int MAX_CACHE_KILOBYTES = 4 * 1024;
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int TARGET_SIZE_PX = 256;
    private static final int CONNECT_TIMEOUT_MILLIS = 6_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final String LOG_PREFIX = "[PikoNewX][Thumbnail] ";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(MAX_CACHE_KILOBYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };

    public interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    private MediaThumbnailLoader() {
    }

    public static void load(String url, Callback callback) {
        load(null, null, url, callback);
    }

    public static void load(
            Context context,
            String cacheUrl,
            String networkUrl,
            Callback callback
    ) {
        if (callback == null) return;
        if (!NewXUtils.isHttpUrl(networkUrl)) {
            logInfo("ignored request with invalid network URL: " + describeUrl(networkUrl));
            return;
        }

        int requestId = NEXT_REQUEST_ID.incrementAndGet();
        logInfo(
                "request #" + requestId + " queued network=" + describeUrl(networkUrl) +
                        " cache=" + describeUrl(cacheUrl) +
                        " context=" + (context == null ? "none" : context.getClass().getName())
        );

        Bitmap cached = CACHE.get(networkUrl);
        if (cached != null && !cached.isRecycled()) {
            logInfo(
                    "request #" + requestId + " hit extension memory cache size=" +
                            dimensions(cached) + " usage=" + CACHE.size() + "/" + CACHE.maxSize() + "KB"
            );
            MAIN_HANDLER.post(() -> {
                logInfo("request #" + requestId + " delivered from extension memory cache");
                callback.onLoaded(cached);
            });
            return;
        }
        if (cached != null) {
            CACHE.remove(networkUrl);
            logInfo("request #" + requestId + " removed recycled extension-cache bitmap");
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = findCachedThumbnail(context, cacheUrl, requestId);
            boolean coilCacheHit = bitmap != null;
            if (bitmap == null) {
                logInfo("request #" + requestId + " Coil miss; falling back to network");
                bitmap = fetch(networkUrl, requestId);
            }
            if (bitmap == null) {
                logInfo("request #" + requestId + " failed; no thumbnail available");
                return;
            }

            String source = coilCacheHit ? "Coil memory cache" : "network";
            CACHE.put(networkUrl, bitmap);
            Bitmap loaded = bitmap;
            logInfo(
                    "request #" + requestId + " completed source=" + source +
                            " size=" + dimensions(loaded) +
                            " extensionCache=" + CACHE.size() + "/" + CACHE.maxSize() + "KB"
            );
            MAIN_HANDLER.post(() -> {
                logInfo("request #" + requestId + " delivered to picker source=" + source);
                callback.onLoaded(loaded);
            });
        });
    }

    private static Bitmap findCachedThumbnail(
            Context context,
            String cacheUrl,
            int requestId
    ) {
        if (context == null || !NewXUtils.isHttpUrl(cacheUrl)) {
            logInfo("request #" + requestId + " skipped Coil lookup: no valid cache URL/context");
            return null;
        }

        logInfo("request #" + requestId + " Coil lookup start key=" + describeUrl(cacheUrl));
        try {
            Object cached = getCachedThumbnail(context, cacheUrl);
            if (!(cached instanceof Bitmap bitmap)) {
                logInfo(
                        "request #" + requestId + " Coil lookup miss result=" +
                                (cached == null ? "null" : cached.getClass().getName())
                );
                return null;
            }
            if (bitmap.isRecycled()) {
                logInfo("request #" + requestId + " Coil lookup returned recycled bitmap");
                return null;
            }

            Bitmap thumbnail = fitToTarget(bitmap);
            logInfo(
                    "request #" + requestId + " Coil lookup hit sourceSize=" + dimensions(bitmap) +
                            " pickerSize=" + dimensions(thumbnail)
            );
            return thumbnail;
        } catch (RuntimeException | LinkageError exception) {
            logException("request #" + requestId + " Coil lookup failed; using network fallback", exception);
            return null;
        }
    }

    // Replaced with a direct Coil memory-cache lookup at patch time.
    private static Object getCachedThumbnail(Object context, String url) {
        return null;
    }

    private static Bitmap fetch(String url, int requestId) {
        HttpURLConnection connection = null;
        logInfo("request #" + requestId + " network fetch start url=" + describeUrl(url));
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK ||
                    responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                logInfo("request #" + requestId + " network rejected HTTP " + responseCode);
                return null;
            }

            int contentLength = connection.getContentLength();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                logInfo(
                        "request #" + requestId + " network response too large bytes=" +
                                contentLength
                );
                return null;
            }

            try (InputStream input = connection.getInputStream()) {
                byte[] data = readAtMost(input, contentLength);
                if (data == null) {
                    logInfo("request #" + requestId + " network response could not be read");
                    return null;
                }

                Bitmap bitmap = decode(data);
                if (bitmap == null) {
                    logInfo(
                            "request #" + requestId + " network response failed to decode bytes=" +
                                    data.length
                    );
                    return null;
                }
                logInfo(
                        "request #" + requestId + " network decode success bytes=" + data.length +
                                " size=" + dimensions(bitmap)
                );
                return bitmap;
            }
        } catch (IOException | RuntimeException exception) {
            logException("request #" + requestId + " network fetch failed", exception);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readAtMost(InputStream input, int contentLength) throws IOException {
        int initialSize = contentLength > 0
                ? Math.min(contentLength, 64 * 1024)
                : 16 * 1024;
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize);
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total > MAX_DOWNLOAD_BYTES - read) return null;
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return bitmap == null ? null : fitToTarget(bitmap);
    }

    private static Bitmap fitToTarget(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largestDimension = Math.max(width, height);
        if (width <= 0 || height <= 0 || largestDimension <= TARGET_SIZE_PX) return bitmap;

        float scale = (float) TARGET_SIZE_PX / largestDimension;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > TARGET_SIZE_PX && height / sample > TARGET_SIZE_PX) {
            sample *= 2;
        }
        return sample;
    }

    static String describeUrl(String url) {
        if (url == null) return "<none>";
        if (url.length() <= 180) return url;
        return url.substring(0, 177) + "...";
    }

    private static String dimensions(Bitmap bitmap) {
        return bitmap.getWidth() + "x" + bitmap.getHeight();
    }

    private static void logInfo(String message) {
        NewXLogger.printInfo(() -> LOG_PREFIX + message);
    }

    private static void logException(String message, Throwable throwable) {
        NewXLogger.printException(() -> LOG_PREFIX + message, throwable);
    }
}
