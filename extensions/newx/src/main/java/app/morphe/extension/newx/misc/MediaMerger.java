package app.morphe.extension.newx.misc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;

/**
 * Downloads multiple image slices in the background, stitches them horizontally
 * in 1-2-3-4 order, and saves the final merged image through {@link DownloadDestination} so a
 * merge lands in the same folder, under the same filename template, as a plain download.
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
            DownloadFileName.PostContext postContext
    ) {
        if (context == null || items == null || items.size() < 2) {
            NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_minimum"), username);
            return;
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;

        NewXInAppNotification.showForUser(
                app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_progress", items.size()),
                username
        );
        MERGE_EXECUTOR.execute(() -> performMerge(safeContext, items, username, postContext));
    }

    private static void performMerge(
            Context context,
            List<InlineDownloadButton.DownloadItem> items,
            String username,
            DownloadFileName.PostContext postContext
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
                    NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_slice_download_failed", sliceIndex), username);
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
                    NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_slice_dimensions_failed", (i + 1)), username);
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
                    NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_memory"), username);
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
                    NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_slice_decode_failed", (i + 1)), username);
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

            // A merge is one output for the whole action, so it renders the template without a
            // media index rather than inheriting the index of the last slice.
            String fileName = DownloadFileName.render(
                    DownloadSettings.filenameTemplate(),
                    postContext,
                    0,
                    1,
                    extension
            );

            // Step 7: Reserve the destination document before encoding, so a skipped merge costs
            // no work and the file lands where the user configured.
            final DownloadDestination.Target target;
            try {
                target = DownloadDestination.reserve(
                        context,
                        DownloadDestination.MediaKind.IMAGES,
                        fileName,
                        mimeType,
                        DownloadDestination.conflictPolicy()
                );
            } catch (IOException | RuntimeException exception) {
                mergedBitmap.recycle();
                NewXLogger.printException(() -> LOG_PREFIX + "Failed to create merged image document", exception);
                NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_save_failed", fileName), username);
                return;
            }
            if (target == null) {
                mergedBitmap.recycle();
                NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_exists", fileName), username);
                return;
            }

            boolean saved = saveMergedBitmap(context, mergedBitmap, target, format);
            mergedBitmap.recycle();

            if (saved) {
                // MediaMerger streams the slices itself rather than going through the URL-based
                // DownloadDestination transfer, so retain its merge-specific completion feedback.
                NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_saved", target.fileName()), username);
                NewXLogger.printInfo(() -> LOG_PREFIX + "Successfully merged and saved " + target.fileName());
            } else {
                NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_save_failed", fileName), username);
            }

        } catch (Throwable t) {
            NewXLogger.printException(() -> LOG_PREFIX + app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_failed"), t);
            NewXInAppNotification.showForUser(app.morphe.extension.shared.StringRef.str("piko_newx_ui_merge_failed"), username);
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
            DownloadDestination.Target target,
            Bitmap.CompressFormat format
    ) {
        int quality = format == Bitmap.CompressFormat.PNG ? 100 : 95;
        return DownloadDestination.save(
                context,
                target,
                output -> bitmap.compress(format, quality, output)
        );
    }
}
