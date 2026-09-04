package app.morphe.extension.newx.misc;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.newx.misc.InlineDownloadButton.ConflictBehavior;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/**
 * Downloads multiple image slices in the background, stitches them horizontally
 * in 1-2-3-4 order, and publishes the final merged image to MediaStore/Pictures/Twitter.
 * Intermediate splits are stored in cache and deleted immediately after merging.
 */
public final class MediaMerger {
    private static final String LOG_PREFIX = "[PikoNewX][MediaMerger] ";
    private static final ExecutorService MERGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_DIMENSION = 16384;
    private static final long MAX_PIXELS = 40_000_000L; // ~160MB in ARGB_8888

    private MediaMerger() {
    }

    public static void downloadAndMerge(
            Context context,
            List<InlineDownloadButton.DownloadItem> items,
            String username,
            String postId
    ) {
        if (context == null || items == null || items.size() < 2) {
            Utils.showToastShort("At least 2 images are required to merge");
            return;
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;

        Utils.showToastShort("Downloading and merging " + items.size() + " images...");
        MERGE_EXECUTOR.execute(() -> performMerge(safeContext, items, username, postId));
    }

    private static void performMerge(
            Context context,
            List<InlineDownloadButton.DownloadItem> items,
            String username,
            String postId
    ) {
        List<File> tempFiles = new ArrayList<>();
        try {
            // Step 1: Download each slice into a temporary cache file
            File cacheDir = context.getCacheDir();
            for (int i = 0; i < items.size(); i++) {
                InlineDownloadButton.DownloadItem item = items.get(i);
                File tempFile = File.createTempFile("piko_merge_split_" + (i + 1) + "_", ".tmp", cacheDir);
                tempFiles.add(tempFile);

                boolean downloaded = downloadToFile(item.url, tempFile);
                final int sliceIndex = i + 1;
                if (!downloaded) {
                    NewXLogger.printInfo(() -> LOG_PREFIX + "Failed to download slice " + sliceIndex + " from " + item.url);
                    Utils.showToastShort("Failed to download image slice " + sliceIndex);
                    return;
                }
            }

            // Step 2: Read dimensions of all slices without loading full bitmaps
            int count = tempFiles.size();
            int[] widths = new int[count];
            int[] heights = new int[count];
            int maxHeight = 0;

            for (int i = 0; i < count; i++) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(tempFiles.get(i).getAbsolutePath(), opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    Utils.showToastShort("Failed to read dimensions of slice " + (i + 1));
                    return;
                }
                widths[i] = opts.outWidth;
                heights[i] = opts.outHeight;
                if (opts.outHeight > maxHeight) {
                    maxHeight = opts.outHeight;
                }
            }

            // Step 3: Compute scaled width for each slice to normalize all to maxHeight
            int[] scaledWidths = new int[count];
            int totalWidth = 0;
            for (int i = 0; i < count; i++) {
                if (heights[i] == maxHeight) {
                    scaledWidths[i] = widths[i];
                } else {
                    scaledWidths[i] = Math.round((float) widths[i] * maxHeight / heights[i]);
                }
                totalWidth += scaledWidths[i];
            }

            // Step 4: Safety bounds check (avoid exceeding Android canvas limit or OOM)
            float downscale = 1.0f;
            if (totalWidth > MAX_DIMENSION) {
                downscale = Math.min(downscale, (float) MAX_DIMENSION / totalWidth);
            }
            if ((long) totalWidth * maxHeight > MAX_PIXELS) {
                downscale = Math.min(downscale, (float) Math.sqrt((double) MAX_PIXELS / ((long) totalWidth * maxHeight)));
            }

            if (downscale < 1.0f) {
                maxHeight = Math.max(1, Math.round(maxHeight * downscale));
                totalWidth = 0;
                for (int i = 0; i < count; i++) {
                    scaledWidths[i] = Math.max(1, Math.round(scaledWidths[i] * downscale));
                    totalWidth += scaledWidths[i];
                }
            }

            // Step 5: Allocate canvas and stitch slices horizontally 1-2-3-4
            Bitmap mergedBitmap;
            try {
                mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError oom) {
                NewXLogger.printException(() -> LOG_PREFIX + "OOM creating ARGB_8888 merged bitmap, trying RGB_565", oom);
                try {
                    mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.RGB_565);
                } catch (OutOfMemoryError oom2) {
                    Utils.showToastShort("Out of memory while stitching images");
                    return;
                }
            }

            Canvas canvas = new Canvas(mergedBitmap);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

            int currentX = 0;
            for (int i = 0; i < count; i++) {
                Bitmap piece = BitmapFactory.decodeFile(tempFiles.get(i).getAbsolutePath());
                if (piece == null) {
                    mergedBitmap.recycle();
                    Utils.showToastShort("Failed to decode image slice " + (i + 1));
                    return;
                }

                Rect srcRect = new Rect(0, 0, piece.getWidth(), piece.getHeight());
                Rect dstRect = new Rect(currentX, 0, currentX + scaledWidths[i], maxHeight);
                canvas.drawBitmap(piece, srcRect, dstRect, paint);
                piece.recycle(); // Immediately free piece memory
                currentX += scaledWidths[i];
            }

            // Step 6: Determine output format and file name
            boolean isAllPng = true;
            for (InlineDownloadButton.DownloadItem item : items) {
                if (item.extension == null || !item.extension.equalsIgnoreCase("png")) {
                    isAllPng = false;
                    break;
                }
            }

            String extension = isAllPng ? "png" : "jpg";
            String mimeType = isAllPng ? "image/png" : "image/jpeg";
            Bitmap.CompressFormat format = isAllPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;

            String baseFileName = InlineDownloadButton.safeFileSegment(username, "twitter") + "_" +
                    InlineDownloadButton.safeFileSegment(postId, "post") + "." + extension;

            ConflictBehavior behavior = InlineDownloadButton.conflictBehavior();
            String fileName = InlineDownloadButton.resolveTargetFileName(context, baseFileName, behavior, mimeType);
            if (fileName == null) {
                mergedBitmap.recycle();
                Utils.showToastShort("Merged image already exists: " + baseFileName);
                return;
            }

            // Step 7: Save to MediaStore (or legacy external storage)
            boolean saved = saveMergedBitmap(context, mergedBitmap, fileName, mimeType, format);
            mergedBitmap.recycle();

            if (saved) {
                Utils.showToastShort("Merged image saved: " + fileName);
                NewXLogger.printInfo(() -> LOG_PREFIX + "Successfully merged and saved " + fileName);
            } else {
                Utils.showToastShort("Failed to save merged image: " + fileName);
            }

        } catch (Throwable t) {
            NewXLogger.printException(() -> LOG_PREFIX + "Failed to merge images", t);
            Utils.showToastShort("Failed to merge images");
        } finally {
            // Step 8: Clean up all temporary files from cache
            for (File tempFile : tempFiles) {
                try {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean downloadToFile(String url, File targetFile) {
        if (!NewXUtils.isHttpUrl(url)) return false;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                if (url.contains("name=orig")) {
                    String fallback = url.replace("name=orig", "name=4096x4096");
                    return downloadToFile(fallback, targetFile);
                }
                return false;
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[64 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }
            return true;
        } catch (Exception e) {
            NewXLogger.printException(() -> LOG_PREFIX + "Download error for " + url, e);
            if (url.contains("name=orig")) {
                String fallback = url.replace("name=orig", "name=4096x4096");
                return downloadToFile(fallback, targetFile);
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean saveMergedBitmap(
            Context context,
            Bitmap bitmap,
            String fileName,
            String mimeType,
            Bitmap.CompressFormat format
    ) throws IOException {
        int quality = format == Bitmap.CompressFormat.PNG ? 100 : 95;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String relativePath = InlineDownloadButton.relativeDownloadPath(mimeType);

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri destination = resolver.insert(collection, values);
            if (destination == null) return false;

            try (OutputStream output = resolver.openOutputStream(destination, "w")) {
                if (output == null) throw new IOException("Could not open output stream for destination: " + destination);
                bitmap.compress(format, quality, output);
            } catch (IOException | RuntimeException exception) {
                resolver.delete(destination, null, null);
                throw exception;
            }

            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(destination, completed, null, null);

            InlineDownloadButton.deleteExistingMedia(resolver, collection, fileName, relativePath, destination);
            return true;
        } else {
            File primary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File directory = new File(primary, "Twitter");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return false;
            }

            File finalFile = new File(directory, fileName);
            try (FileOutputStream output = new FileOutputStream(finalFile)) {
                bitmap.compress(format, quality, output);
            }
            context.sendBroadcast(
                    new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(finalFile))
            );
            return true;
        }
    }
}
