/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity;
import app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity;
import app.morphe.extension.crimera.downloader.FolderPickerActivity;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Constants;

@SuppressWarnings("deprecation")
public class ActivityHook {

    private static void launchActivity(Context context, Intent intent){
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.printException(() -> "launchActivity failure", e);
            PikoUtils.logger(e);
        }
    }

    public static void startPikoActivity(String fragment_name,String title){
        Context context = PikoUtils.getContext();
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(Constants.PIKO_FRAGMENT_NAME, fragment_name);
        intent.putExtra(Constants.PIKO_FRAGMENT_TITLE, title);
        launchActivity(context,intent);
    }

    public static void launchFragment(Context ctx, String bundleKey){
        Intent intent = null;
        if (bundleKey.equals("piko_export_dev_overrides") || bundleKey.equals("piko_export_pref")) {
            intent = new Intent(ctx,BackupPrefActivity.class);
        } else if (bundleKey.equals("piko_import_dev_overrides") || bundleKey.equals("piko_import_id_mapping")  || bundleKey.equals("piko_import_pref")) {
            intent = new Intent(ctx,RestorePrefActivity.class);
        } else if (bundleKey.equals("piko_download_set_path")) {
            intent = new Intent(ctx,FolderPickerActivity.class);
        }
        if(intent!=null){
            intent.putExtra(bundleKey,true);
            launchActivity(ctx,intent);
        }
    }

    // How long a temp-opened file is kept before DownloadManager deletes it — gives the external
    // viewer app time to actually load/cache it before we drop it.
    private static final long TEMP_MEDIA_TTL_MS = 5 * 60 * 1000L;

    public static void handleUrlIntent(Boolean isVideo, String mediaUrl) {
        try {
            Context context = PikoUtils.getContext();
            if (context == null || mediaUrl == null) return;

            String dataType = isVideo ? "video/*" : "image/*";
            String mimeType = isVideo ? "video/mp4" : "image/jpeg";
            String chooserTitle = str(isVideo ? "piko_open_video_with" : "piko_open_image_with");
            String extension = isVideo ? ".mp4" : ".jpg";
            String fileName = "piko_tmp_" + System.currentTimeMillis() + extension;

            String mediaLabel = str(isVideo ? "piko_media_video" : "piko_media_photo");
            Utils.showToastShort(str("piko_downloading_media") + mediaLabel);

            // Download ourselves — no DownloadManager queue/scheduling overhead, starts
            // immediately — then just REGISTER the finished file with DownloadManager
            // (addCompletedDownload) to get a shareable content:// URI synchronously, no
            // broadcast wait needed.
            new Thread(() -> {
                try {
                    File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "piko_tmp");
                    if (!dir.exists()) dir.mkdirs();
                    File outFile = new File(dir, fileName);

                    downloadTo(mediaUrl, outFile);

                    DownloadManager downloadManager =
                            (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (downloadManager == null) return;

                    long downloadId = downloadManager.addCompletedDownload(
                            fileName, fileName, false, mimeType,
                            outFile.getAbsolutePath(), outFile.length(), true
                    );
                    Uri contentUri = downloadManager.getUriForDownloadedFile(downloadId);
                    if (contentUri == null) {
                        postToast(str("piko_download_failed_media"));
                        return;
                    }

                    new Handler(Looper.getMainLooper()).post(() ->
                            openAndScheduleCleanup(context, downloadManager, downloadId, contentUri, dataType, chooserTitle));
                } catch (Exception e) {
                    Logger.printException(() -> "temp media download failure", e);
                    postToast(str("piko_download_failed_media"));
                }
            }).start();
        } catch (Exception e) {
            Logger.printException(() -> "handleUrlIntent failure", e);
            PikoUtils.logger(e);
        }
    }

    private static void downloadTo(String url, File outFile) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void openAndScheduleCleanup(
            Context context,
            DownloadManager downloadManager,
            long downloadId,
            Uri contentUri,
            String dataType,
            String chooserTitle
    ) {
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(contentUri, dataType);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooserIntent = Intent.createChooser(viewIntent, chooserTitle);
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                PikoUtils.launchIntent(chooserIntent);
            } catch (Exception launchEx) {
                Logger.printException(() -> "launchIntent failed, falling back", launchEx);
                context.getApplicationContext().startActivity(chooserIntent);
            }

            // Genuinely temporary — never left behind permanently.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    downloadManager.remove(downloadId);
                } catch (Exception ignored) {}
            }, TEMP_MEDIA_TTL_MS);
        } catch (Exception e) {
            Logger.printException(() -> "temp media open failure", e);
            Utils.showToastShort(str("piko_download_failed_media"));
        }
    }

    private static void postToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Utils.showToastShort(message));
    }

}
