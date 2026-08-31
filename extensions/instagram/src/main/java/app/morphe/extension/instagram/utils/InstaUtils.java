/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.entity.DeveloperOptions;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.crimera.PikoUtils;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.Logger;
import static app.morphe.extension.shared.requests.Route.Method.GET;

public class InstaUtils {

    // Temp file lifetime before DownloadManager deletes it.
    private static final long TEMP_MEDIA_TTL_MS = 5 * 60 * 1000L;

    public static boolean deleteRecursive(File file) {
        try {
            if (file == null || !file.exists()) {
                PikoUtils.toast(str("piko_fail_no_file"));
                return false;
            }

            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            return file.delete();
        } catch (RuntimeException e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    public static void decompileExperiments(boolean asJson) {
        String appVersionName = Utils.getAppVersionName();
        DeveloperOptions developerOptions = new DeveloperOptions();

        String fileName = appVersionName + " Experiments";
        boolean fileDone = false;
        String data = "";
        String fileDoneTxt = " failed";

        if (asJson) {
            fileName += ".json";
            data = developerOptions.toJSONObject().toString();
        } else {
            fileName += ".txt";
            data = developerOptions.toString();
        }
        fileDone = PikoUtils.pikoWriteFile(fileName, Constants.DEFAULT_PIKO_FOLDER, data, false);
        fileDoneTxt = fileDone ? " created" : fileDoneTxt;
        PikoUtils.toast(fileName + fileDoneTxt);
    }

    public static void showResetSettingsDialog(Context context) {
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_reset_pref_confirm"))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(
                        str("piko_ok"),
                        (dialogInterface, which) -> deletePref()
                )
                .show();
    }

    public static void deletePref(){
        if(Pref.clearAllPreferences()){
            PikoUtils.toast(str("piko_reset_pref_success"));
            Utils.restartApp(Utils.getContext());
        }else{
            PikoUtils.toast(str("piko_reset_pref_failed"));
        }
    }

    public static void downloadFile(String host, String endpoint, File outputFile, Runnable onComplete) {
        Context context = Utils.getContext();

        if (!Utils.isNetworkConnected()) {
            PikoUtils.toast(str("piko_no_internet"));
            return;
        }
        Utils.runOnBackgroundThread(() -> {
            try {
                Route route = new Route(GET, endpoint);
                HttpURLConnection connection = Requester.getConnectionFromRoute(host, route);
                String response = Requester.parseString(connection);

                boolean fileWritten = PikoUtils.writeFile(outputFile, response.getBytes(), false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!fileWritten) {
                        PikoUtils.toast(str("piko_download_failed_media") + outputFile.getName());
                        return;
                    }
                    PikoUtils.toast(str("piko_downloaded_media") + outputFile.getName());

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });

            } catch (Exception e) {
                PikoUtils.logger(e);
                PikoUtils.toast(str("piko_download_failed_media") + outputFile.getName());
            }
        });
    }

    /** Opens a deleted-message image/video externally: downloads directly, then registers with
     *  DownloadManager (addCompletedDownload) for a shareable content:// URI. */
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

            new Thread(() -> {
                try {
                    File dir = new File(context.getCacheDir(), "piko_tmp");
                    if (!dir.exists()) dir.mkdirs();
                    File outFile = new File(dir, fileName);

                    downloadUrlToFile(mediaUrl, outFile);

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
                            openTempMediaAndScheduleCleanup(context, downloadManager, downloadId, contentUri, dataType, chooserTitle));
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

    private static void downloadUrlToFile(String url, File outFile) throws Exception {
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

    private static void openTempMediaAndScheduleCleanup(
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
