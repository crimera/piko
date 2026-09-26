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
            NewXInAppNotification.showForUser("At least 2 images are required to merge", username);
            return;
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;

        NewXInAppNotification.showForUser(
                "Downloading and merging " + items.size() + " images...",
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
            File cacheDir = context.getCacheDir();
            for (int i = 0; i < items.size(); i++) {
                InlineDownloadButton.DownloadItem item = items.get(i);
                File tempFile = File.createTempFile("piko_merge_split_" + (i + 1) + "_", ".tmp", cacheDir);
                tempFiles.add(tempFile);

                boolean downloaded = downloadToFile(item.url, tempFile);
                final int sliceIndex = i + 1;
                if (!downloaded) {
                    NewXLogger.printInfo(() -> LOG_PREFIX + "Failed to download slice " + sliceIndex + " from " + item.url);
                    NewXInAppNotification.showForUser("Failed to download image slice " + sliceIndex, username);
                    return;
                }
            }

            int count = tempFiles.size();
            int[] widths = new int[count];
            int[] heights = new int[count];
            int maxHeight = 0;

            for (int i = 0; i < count; i++) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(tempFiles.get(i).getAbsolutePath(), opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    NewXInAppNotification.showForUser("Failed to read dimensions of slice " + (i + 1), username);
                    return;
                }
                widths[i] = opts.outWidth;
                heights[i] = opts.outHeight;
                if (opts.outHeight > maxHeight) {
                    maxHeight = opts.outHeight;
                }
            }

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

            Bitmap mergedBitmap;
            try {
                mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError oom) {
                NewXLogger.printException(() -> LOG_PREFIX + "OOM creating ARGB_8888 merged bitmap, trying RGB_565", oom);
                try {
                    mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.RGB_565);
                } catch (OutOfMemoryError oom2) {
                    NewXInAppNotification.showForUser("Out of memory while stitching images", username);
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
                    NewXInAppNotification.showForUser("Failed to decode image slice " + (i + 1), username);
                    return;
                }

                Rect srcRect = new Rect(0, 0, piece.getWidth(), piece.getHeight());
                Rect dstRect = new Rect(currentX, 0, currentX + scaledWidths[i], maxHeight);
                canvas.drawBitmap(piece, srcRect, dstRect, paint);
                piece.recycle(); // Immediately free piece memory
                currentX += scaledWidths[i];
            }

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

            // One output for the whole action, so render without a media index.
            String fileName = DownloadFileName.render(
                    DownloadSettings.filenameTemplate(),
                    postContext,
                    0,
                    1,
                    extension
            );

            // Reserve before encoding so a skipped merge costs no work.
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
                if (DownloadDestination.isDestinationLoss(exception)) {
                    InlineDownloadButton.reportDownloadStatus(
                            InlineDownloadButton.FOLDER_LOST_MESSAGE,
                            username
                    );
                } else {
                    InlineDownloadButton.reportDownloadStatus(
                            "Failed to save merged image: " + fileName,
                            username
                    );
                }
                return;
            }
            if (target == null) {
                mergedBitmap.recycle();
                NewXInAppNotification.showForUser("Merged image already exists: " + fileName, username);
                return;
            }

            DownloadDestination.SaveState saveState = saveMergedBitmap(context, mergedBitmap, target, format);
            mergedBitmap.recycle();

            switch (saveState) {
                case SAVED -> {
                    NewXLogger.printInfo(() -> LOG_PREFIX + "Successfully merged and saved " + target.fileName());
                    InlineDownloadButton.reportDownloadStatus(
                            "Merged image saved: " + target.fileName(), username);
                }
                case DESTINATION_LOST -> InlineDownloadButton.reportDownloadStatus(
                        InlineDownloadButton.FOLDER_LOST_MESSAGE, username);
                case FAILED -> InlineDownloadButton.reportDownloadStatus(
                        "Failed to save merged image: " + fileName, username);
            }

        } catch (Throwable t) {
            NewXLogger.printException(() -> LOG_PREFIX + "Failed to merge images", t);
            NewXInAppNotification.showForUser("Failed to merge images", username);
        } finally {
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

    private static DownloadDestination.SaveState saveMergedBitmap(
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
