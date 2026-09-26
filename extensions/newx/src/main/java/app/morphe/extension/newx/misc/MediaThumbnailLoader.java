package app.morphe.extension.newx.misc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;

/** Loads small media previews without blocking the UI thread. */
public final class MediaThumbnailLoader {
    private static final int MAX_CACHE_KILOBYTES = 8 * 1024;
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int TARGET_SIZE_PX = 256;
    private static final int CONNECT_TIMEOUT_MILLIS = 6_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final String LOG_PREFIX = "[PikoNewX][Thumbnail] ";
    private static final String COIL_DIAGNOSTIC_LOG_PREFIX =
            "[PikoNewX][Thumbnail][CoilDiag] ";
    private static final String GLIDE_DIAGNOSTIC_LOG_PREFIX =
            "[PikoNewX][Thumbnail][GlideDiag] ";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger();
    private static final AtomicBoolean DISK_CLEANUP_SCHEDULED = new AtomicBoolean(false);
    // Session-tier analytics for the Developer tools stats screen. All increments are
    // lock-free AtomicLong updates on paths that already run; they never block loading.
    private static final AtomicLong EXTENSION_MEMORY_HITS = new AtomicLong();
    private static final AtomicLong IMAGE_LOADER_MEMORY_HITS = new AtomicLong();
    private static final AtomicLong DISK_HITS = new AtomicLong();
    private static final AtomicLong NETWORK_SUCCESS = new AtomicLong();
    private static final AtomicLong NETWORK_FAILED = new AtomicLong();
    private static final AtomicLong BYTES_DOWNLOADED = new AtomicLong();
    private static final AtomicLong ENTRIES_PERSISTED = new AtomicLong();
    private static final AtomicLong OVERSIZED_SKIPPED = new AtomicLong();
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

    /** Process-lifetime request-tier counters for the Developer tools stats screen. */
    public static final class SessionStats {
        public final long extensionMemoryHits;
        public final long imageLoaderMemoryHits;
        public final long diskHits;
        public final long networkSuccess;
        public final long networkFailed;
        public final long bytesDownloaded;
        public final long entriesPersisted;
        public final long oversizedSkipped;
        public final int memoryCacheKilobytes;
        public final int memoryCacheMaxKilobytes;

        SessionStats(
                long extensionMemoryHits,
                long imageLoaderMemoryHits,
                long diskHits,
                long networkSuccess,
                long networkFailed,
                long bytesDownloaded,
                long entriesPersisted,
                long oversizedSkipped,
                int memoryCacheKilobytes,
                int memoryCacheMaxKilobytes
        ) {
            this.extensionMemoryHits = extensionMemoryHits;
            this.imageLoaderMemoryHits = imageLoaderMemoryHits;
            this.diskHits = diskHits;
            this.networkSuccess = networkSuccess;
            this.networkFailed = networkFailed;
            this.bytesDownloaded = bytesDownloaded;
            this.entriesPersisted = entriesPersisted;
            this.oversizedSkipped = oversizedSkipped;
            this.memoryCacheKilobytes = memoryCacheKilobytes;
            this.memoryCacheMaxKilobytes = memoryCacheMaxKilobytes;
        }
    }

    public static SessionStats sessionStats() {
        int used;
        int max;
        try {
            used = CACHE.size();
            max = CACHE.maxSize();
        } catch (RuntimeException e) {
            used = 0;
            max = MAX_CACHE_KILOBYTES;
        }
        return new SessionStats(
                EXTENSION_MEMORY_HITS.get(),
                IMAGE_LOADER_MEMORY_HITS.get(),
                DISK_HITS.get(),
                NETWORK_SUCCESS.get(),
                NETWORK_FAILED.get(),
                BYTES_DOWNLOADED.get(),
                ENTRIES_PERSISTED.get(),
                OVERSIZED_SKIPPED.get(),
                used,
                max
        );
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
        boolean loggingEnabled = NewXLogger.isLoggingEnabled();
        if (!NewXUtils.isHttpUrl(networkUrl)) {
            if (loggingEnabled) {
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "ignored request with invalid network URL: " + describeUrl(networkUrl));
            }
            return;
        }

        int requestId = loggingEnabled ? NEXT_REQUEST_ID.incrementAndGet() : 0;
        if (loggingEnabled) {
            NewXLogger.printInfo(() -> LOG_PREFIX +
                    "request #" + requestId + " queued network=" + describeUrl(networkUrl) +
                            " cache=" + describeUrl(cacheUrl) +
                            " context=" + (context == null ? "none" : context.getClass().getName())
            );
        }

        Bitmap cached = CACHE.get(networkUrl);
        if (cached != null && !cached.isRecycled()) {
            EXTENSION_MEMORY_HITS.incrementAndGet();
            if (loggingEnabled) {
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " hit extension memory cache size=" +
                                dimensions(cached) + " usage=" + CACHE.size() + "/" + CACHE.maxSize() + "KB"
                );
            }
            MAIN_HANDLER.post(() -> {
                if (loggingEnabled) {
                    NewXLogger.printInfo(() -> LOG_PREFIX +
                            "request #" + requestId + " delivered from extension memory cache");
                }
                callback.onLoaded(cached);
            });
            return;
        }
        if (cached != null) {
            CACHE.remove(networkUrl);
        }

        EXECUTOR.execute(() -> {
            // 1. Try Glide/Coil memory cache with exact cacheUrl
            Bitmap bitmap = findCachedThumbnail(context, cacheUrl, requestId);
            if (bitmap != null) IMAGE_LOADER_MEMORY_HITS.incrementAndGet();
            String source = "image-loader memory cache";

            // 2. Try Glide/Coil memory cache with base media key (matches any size cached by the app)
            if (bitmap == null) {
                String baseKey = baseMediaKey(networkUrl);
                if (baseKey != null && !baseKey.equals(cacheUrl)) {
                    bitmap = findCachedThumbnail(context, baseKey, requestId);
                    if (bitmap != null) IMAGE_LOADER_MEMORY_HITS.incrementAndGet();
                }
            }

            // 3. Try local disk cache
            if (bitmap == null) {
                bitmap = findDiskCachedThumbnail(context, networkUrl, cacheUrl, requestId);
                if (bitmap != null) {
                    DISK_HITS.incrementAndGet();
                    source = "disk cache";
                }
            }

            // 4. Fetch from network and save to disk cache
            if (bitmap == null) {
                String fetchUrl = cacheUrl != null ? cacheUrl : networkUrl;
                bitmap = fetchAndCache(context, fetchUrl, networkUrl, requestId);
                if (bitmap != null) {
                    source = "network";
                }
            }

            if (bitmap == null) {
                NETWORK_FAILED.incrementAndGet();
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " failed; no thumbnail available");
                return;
            }

            CACHE.put(networkUrl, bitmap);
            Bitmap loaded = bitmap;
            String loadedSource = source;
            NewXLogger.printInfo(() -> LOG_PREFIX +
                    "request #" + requestId + " completed source=" + loadedSource +
                            " size=" + dimensions(loaded) +
                            " extensionCache=" + CACHE.size() + "/" + CACHE.maxSize() + "KB"
            );
            MAIN_HANDLER.post(() -> {
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.printInfo(() -> LOG_PREFIX +
                            "request #" + requestId + " delivered source=" + loadedSource);
                }
                callback.onLoaded(loaded);
            });
        });
    }

    private static String baseMediaKey(String url) {
        if (url == null) return null;
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash + 1 < path.length()) {
                    String filename = path.substring(lastSlash + 1);
                    int dot = filename.indexOf('.');
                    return dot > 0 ? filename.substring(0, dot) : filename;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return url;
    }

    private static File getDiskCacheDir(Context context) {
        if (context == null) return null;
        try {
            File root = context.getCacheDir();
            if (root == null) return null;
            return MediaDiskCache.resolveDir(root);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Schedules the one-shot bounded-cache cleanup off the UI thread. */
    private static void ensureDiskCleanup(File dir) {
        if (dir == null) return;
        if (!DISK_CLEANUP_SCHEDULED.compareAndSet(false, true)) return;
        try {
            EXECUTOR.execute(() -> {
                try {
                    MediaDiskCache.cleanup(dir);
                } catch (Throwable ignored) {
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private static Bitmap findDiskCachedThumbnail(
            Context context,
            String networkUrl,
            String cacheUrl,
            int requestId
    ) {
        File cacheDir = getDiskCacheDir(context);
        if (cacheDir == null) return null;
        ensureDiskCleanup(cacheDir);

        String secondKey = cacheUrl != null && !cacheUrl.equals(networkUrl) ? cacheUrl : null;
        String[] keys = secondKey == null
                ? new String[]{networkUrl}
                : new String[]{networkUrl, secondKey};
        for (String key : keys) {
            if (key == null) continue;
            byte[] data;
            try {
                data = MediaDiskCache.readEntry(cacheDir, key);
            } catch (Throwable ignored) {
                continue;
            }
            if (data == null) continue;
            Bitmap bitmap;
            try {
                bitmap = decode(data);
            } catch (Throwable ignored) {
                bitmap = null;
            }
            if (bitmap != null) {
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " disk cache hit key=" + describeUrl(key));
                return bitmap;
            }
            try {
                MediaDiskCache.deleteEntry(cacheDir, key);
            } catch (Throwable ignored) {
            }
            NewXLogger.printInfo(() -> LOG_PREFIX +
                    "request #" + requestId + " disk entry undecodable, deleted key=" +
                            describeUrl(key));
        }
        return null;
    }

    private static Bitmap findCachedThumbnail(
            Context context,
            String cacheUrl,
            int requestId
    ) {
        if (context == null || !NewXUtils.isHttpUrl(cacheUrl)) {
            return null;
        }

        NewXLogger.printInfo(() -> LOG_PREFIX +
                "request #" + requestId + " thumbnail cache lookup start key=" + describeUrl(cacheUrl));
        try {
            Object cached = getCachedThumbnail(context, cacheUrl);
            if (!(cached instanceof Bitmap bitmap)) {
                return null;
            }
            if (bitmap.isRecycled()) {
                return null;
            }

            Bitmap thumbnail = fitToTarget(bitmap);
            NewXLogger.printInfo(() -> LOG_PREFIX +
                    "request #" + requestId + " thumbnail cache lookup hit sourceSize=" + dimensions(bitmap) +
                            " pickerSize=" + dimensions(thumbnail)
            );
            return thumbnail;
        } catch (RuntimeException | LinkageError exception) {
            NewXLogger.printException(() -> LOG_PREFIX +
                    "request #" + requestId +
                            " thumbnail cache lookup failed; using network fallback", exception);
            return null;
        }
    }

    /**
     * Receives counters from the patch-time Coil bridge so a null result can be diagnosed.
     * The counters describe the key set examined by that lookup, not the whole loader lifetime.
     */
    private static void logCoilLookupDiagnostics(
            String cacheUrl,
            int keyCount,
            int matchingKeyCount,
            int memoryValueCount,
            int imageCount,
            int bitmapCount
    ) {
        NewXLogger.printInfo(() ->
                COIL_DIAGNOSTIC_LOG_PREFIX +
                        "key=" + describeUrl(cacheUrl) +
                        " keys=" + keyCount +
                        " matches=" + matchingKeyCount +
                        " memoryValues=" + memoryValueCount +
                        " images=" + imageCount +
                        " bitmaps=" + bitmapCount
        );
    }

    /** Receives counters from the patch-time Glide bridge for cache-path diagnostics. */
    private static void logGlideLookupDiagnostics(
            String cacheUrl,
            int memoryKeyCount,
            int activeKeyCount,
            int matchingKeyCount,
            int resourceCount,
            int bitmapCount
    ) {
        NewXLogger.printInfo(() ->
                GLIDE_DIAGNOSTIC_LOG_PREFIX +
                        "key=" + describeUrl(cacheUrl) +
                        " memoryKeys=" + memoryKeyCount +
                        " activeKeys=" + activeKeyCount +
                        " matches=" + matchingKeyCount +
                        " resources=" + resourceCount +
                        " bitmaps=" + bitmapCount
        );
    }

    /** Converts a cached Glide resource (normally a BitmapDrawable) into a bitmap. */
    private static Bitmap bitmapFromGlideResource(Object value) {
        if (value instanceof Bitmap bitmap) return bitmap;
        if (value instanceof BitmapDrawable bitmapDrawable) return bitmapDrawable.getBitmap();
        if (!(value instanceof Drawable drawable)) return null;

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = TARGET_SIZE_PX;
        if (height <= 0) height = TARGET_SIZE_PX;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    // Replaced with a direct image-loader cache lookup at patch time.
    private static Object getCachedThumbnail(Object context, String url) {
        return null;
    }

    // Kept as a separate patch point so Glide can fall back to Coil when the
    // server-side renderer switch changes at runtime.
    private static Object getCachedThumbnailCoil(Object context, String url) {
        return null;
    }

    private static Bitmap fetchAndCache(
            Context context,
            String downloadUrl,
            String networkUrl,
            int requestId
    ) {
        HttpURLConnection connection = null;
        NewXLogger.printInfo(() -> LOG_PREFIX +
                "request #" + requestId + " network fetch start url=" + describeUrl(downloadUrl));
        try {
            connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK ||
                    responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                NETWORK_FAILED.incrementAndGet();
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " network rejected HTTP " + responseCode);
                return null;
            }

            int contentLength = connection.getContentLength();
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                NETWORK_FAILED.incrementAndGet();
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " network response too large bytes=" +
                                contentLength
                );
                return null;
            }

            try (InputStream input = connection.getInputStream()) {
                byte[] data = readAtMost(input, contentLength);
                if (data == null) {
                    NETWORK_FAILED.incrementAndGet();
                    NewXLogger.printInfo(() -> LOG_PREFIX +
                            "request #" + requestId + " network response could not be read");
                    return null;
                }

                // Persist under the canonical network key only; oversized responses are
                // still decoded and displayed but never stored.
                Bitmap bitmap = decode(data);
                if (bitmap == null) {
                    NETWORK_FAILED.incrementAndGet();
                    NewXLogger.printInfo(() -> LOG_PREFIX +
                            "request #" + requestId + " network response failed to decode bytes=" +
                                    data.length
                    );
                    return null;
                }
                if (data.length > MediaDiskCache.MAX_DISK_ENTRY_BYTES) {
                    OVERSIZED_SKIPPED.incrementAndGet();
                    NewXLogger.printInfo(() -> LOG_PREFIX +
                            "request #" + requestId + " network entry too large for disk bytes=" +
                                    data.length);
                } else {
                    try {
                        File cacheDir = getDiskCacheDir(context);
                        if (cacheDir != null) {
                            ensureDiskCleanup(cacheDir);
                            try {
                                if (MediaDiskCache.write(cacheDir, networkUrl, data)) {
                                    ENTRIES_PERSISTED.incrementAndGet();
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                NETWORK_SUCCESS.incrementAndGet();
                BYTES_DOWNLOADED.addAndGet(data.length);
                NewXLogger.printInfo(() -> LOG_PREFIX +
                        "request #" + requestId + " network decode success bytes=" + data.length +
                                " size=" + dimensions(bitmap)
                );
                return bitmap;
            }
        } catch (IOException | RuntimeException exception) {
            NETWORK_FAILED.incrementAndGet();
            NewXLogger.printException(() -> LOG_PREFIX +
                    "request #" + requestId + " network fetch failed", exception);
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

}
