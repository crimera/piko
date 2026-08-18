/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

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
            String chooserTitle = str(isVideo ? "piko_open_video_with" : "piko_open_image_with");
            String extension = isVideo ? ".mp4" : ".jpg";
            String fileName = "piko_tmp_" + System.currentTimeMillis() + extension;

            DownloadManager downloadManager =
                    (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) return;

            // Registered BEFORE enqueue: a fast download (small image, good connection) can
            // complete before the next line runs, and we'd miss the completion broadcast.
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
            // App-private, scoped destination: no storage permission needed, and it never shows
            // up in the user's own Downloads app or Gallery — genuinely temporary.
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    "piko_tmp/" + fileName);
            // VISIBILITY_HIDDEN requires the DOWNLOAD_WITHOUT_NOTIFICATION permission, which we
            // don't declare — this is the closest allowed option: no notification while
            // downloading, just a brief one on completion (cleared once we remove the entry).
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            long[] downloadIdHolder = new long[1];
            registerCompletionReceiver(context, downloadManager, downloadIdHolder, dataType, chooserTitle);

            downloadIdHolder[0] = downloadManager.enqueue(request);
            String mediaLabel = str(isVideo ? "piko_media_video" : "piko_media_photo");
            Utils.showToastShort(String.format(str("piko_opening_media"), mediaLabel));
        } catch (Exception e) {
            Logger.printException(() -> "handleUrlIntent failure", e);
            PikoUtils.logger(e);
        }
    }

    /** Registers the completion listener before the download is enqueued, so a fast download
     *  can never finish before we're listening. downloadIdHolder[0] is filled in by the caller
     *  right after enqueue() returns — always before the download can actually complete. */
    private static void registerCompletionReceiver(
            Context context,
            DownloadManager downloadManager,
            long[] downloadIdHolder,
            String dataType,
            String chooserTitle
    ) {
        Context appContext = context.getApplicationContext();
        BroadcastReceiver[] receiverHolder = new BroadcastReceiver[1];

        receiverHolder[0] = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                long downloadId = downloadIdHolder[0];
                if (downloadId == 0 || finishedId != downloadId) return;
                try {
                    appContext.unregisterReceiver(receiverHolder[0]);
                } catch (Exception ignored) {}

                try {
                    Uri contentUri = downloadManager.getUriForDownloadedFile(downloadId);
                    if (contentUri == null) {
                        Utils.showToastShort(str("piko_download_error"));
                        downloadManager.remove(downloadId);
                        return;
                    }

                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(contentUri, dataType);
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Intent chooserIntent = Intent.createChooser(viewIntent, chooserTitle);
                    // Launching from a BroadcastReceiver, not an Activity — NEW_TASK is mandatory
                    // here or startActivity throws, and we don't rely on PikoUtils.launchIntent
                    // having already set it.
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        PikoUtils.launchIntent(chooserIntent);
                    } catch (Exception launchEx) {
                        // Fallback in case PikoUtils.launchIntent expects an Activity context
                        // internally — start it directly off the application context instead.
                        Logger.printException(() -> "launchIntent failed, falling back", launchEx);
                        appContext.startActivity(chooserIntent);
                    }

                    // Genuinely temporary — never left behind permanently.
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> {
                                try {
                                    downloadManager.remove(downloadId);
                                } catch (Exception ignored) {}
                            },
                            TEMP_MEDIA_TTL_MS
                    );
                } catch (Exception e) {
                    Logger.printException(() -> "temp media open failure", e);
                    Utils.showToastShort(str("piko_download_error"));
                }
            }
        };

        // ACTION_DOWNLOAD_COMPLETE is sent by the system's download provider process, not by
        // this app — RECEIVER_NOT_EXPORTED would silently drop it on API 33+ since that flag
        // blocks broadcasts from other apps/processes, which is exactly what this is.
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiverHolder[0], filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiverHolder[0], filter);
        }
    }

}
