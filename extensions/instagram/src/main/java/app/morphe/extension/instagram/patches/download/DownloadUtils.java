/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import android.os.Build;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.app.Activity;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;

import java.io.FileOutputStream;
import java.io.File;

import android.net.Uri;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.AudioMediaInterface;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.crimera.ObjectBrowser;

public class DownloadUtils {
    private static boolean ENABLE_DIRECT_DOWNLOAD;
    private static boolean PICTURES_FOLDER;
    private static boolean SPLIT_BY_USERNAME;
    private static boolean DEBUG;

    static {
        ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
        SPLIT_BY_USERNAME = Pref.downloadUsernameFolder() && SettingsStatus.downloadMedia;
        PICTURES_FOLDER = Pref.downloadPicturesFolder() && SettingsStatus.downloadMedia;
        DEBUG = Pref.pikoDebug();
    }

    private static void downloadDialogBox(Context context, MediaData mediaInfo, int position) throws Exception {
        int carouselSize = mediaInfo.getCarouselSize();
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        Boolean isCurrentMediaVideo = currentMediaData.isVideo();
        AudioMediaInterface audioMedia = currentMediaData.getAudioMedia();

        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(Strings.DOWNLOAD_CURRENT_MEDIA);
        options.add(Strings.DOWNLOAD_AS_IMAGE);
        if (audioMedia != null) options.add(Strings.DOWNLOAD_AUDIO);
        options.add(Strings.COPY_MEDIA_LINK);
        if (isCurrentMediaVideo) {
            options.add(Strings.OPEN_VIDEO_EXTERNALLY);
        } else {
            options.add(Strings.OPEN_IMAGE_EXTERNALLY);
        }
        if (carouselSize > 1) options.add(Strings.DOWNLOAD_ALL);
        if (DEBUG) options.add(Strings.PIKO_DEBUG);

        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(Strings.DOWNLOAD_CURRENT_MEDIA)) {
                        downloadMediaAt(context, mediaInfo, position);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_AS_IMAGE)) {
                        String username = mediaInfo.getUserData().getUsername();

                        String downloadFileName = mediaInfo.getDownloadFilename(true);
                        downloadFile(context, currentMediaData.getPhotoLink(), username, downloadFileName);

                    } else if (selectedOption.equals(Strings.COPY_MEDIA_LINK)) {
                        Utils.setClipboard(currentMediaData.getMediaLink());
                        Utils.showToastShort(Strings.COPIED_MEDIA_LINK);

                    } else if (selectedOption.equals(Strings.OPEN_VIDEO_EXTERNALLY) || selectedOption.equals(Strings.OPEN_IMAGE_EXTERNALLY)) {
                        ActivityHook.handleUrlIntent(isCurrentMediaVideo, currentMediaData.getMediaLink());

                    } else if (selectedOption.equals(Strings.DOWNLOAD_ALL)) {
                        downloadAllMedia(context, mediaInfo);

                    } else if (selectedOption.equals(Strings.PIKO_DEBUG)) {
                        ObjectBrowser.browseObject(context, currentMediaData);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_AUDIO)) {
                        downloadAudio(context, audioMedia);

                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error at downloadDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });


        dialog.setTitle(Strings.DOWNLOAD_OPTIONS);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    private static void downloadAudio(Context context, AudioMediaInterface audioMedia) throws Exception {
        String username = Strings.DEFAULT_AUDIO_FOLDER;
        String downloadFileName = audioMedia.getDownloadName();
        String audioUrl = audioMedia.getAudioUrl();

        downloadFile(context, audioUrl, username, downloadFileName);
    }

    public static void downloadMediaAt(Context context, MediaData mediaInfo, int position) throws Exception {
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        String username = mediaInfo.getUserData().getUsername();
        String downloadFileName = currentMediaData.getDownloadFilename(false);

        downloadFile(context, currentMediaData.getMediaLink(), username, downloadFileName);
    }

    public static void downloadAllMedia(Context context, MediaData mediaInfo) throws Exception {
        String username = mediaInfo.getUserData().getUsername();
        int carouselSize = mediaInfo.getCarouselSize();

        for (int position = 0; position < carouselSize; position++) {
            MediaData currentMediaData = mediaInfo.getMediaAt(position);
            String downloadFileName = currentMediaData.getDownloadFilename(false);
            downloadFile(context, currentMediaData.getMediaLink(), username, downloadFileName);
        }
    }


    public static void downloadPost(Context context, Object mediaObject, int position) {
        try {
            position = position < 1 ? 0 : position;
            MediaData mediaInfo = new MediaData(mediaObject);
            if (ENABLE_DIRECT_DOWNLOAD) {
                downloadMediaAt(context, mediaInfo, position);
            } else {
                downloadDialogBox(context, mediaInfo, position);
            }

        } catch (Exception e) {
            Logger.printException(() -> "Error at downloadPost", e);
        }
    }


    private static void downloader(Context context, String filename, File file, File tempFile, Intent intent, long downloadId,
                                   BroadcastReceiver broadcastReceiver) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id == downloadId) {
            tempFile.renameTo(file);
            Utils.showToastShort(Strings.DOWNLOADED_MEDIA + filename);
            context.unregisterReceiver(broadcastReceiver);
        }
    }

    public static void downloadFile(Context ctx, String url, String username, String downloadFilename) {
        String publicFolder = Environment.DIRECTORY_DOWNLOADS;
        if (PICTURES_FOLDER) publicFolder = Environment.DIRECTORY_PICTURES;
        String subFolder = Strings.DEFAULT_PIKO_FOLDER;
        boolean isAudioFile = username.equals(Strings.DEFAULT_AUDIO_FOLDER);
        // If it's audio file, then file name need not have username_.
        final String filename = isAudioFile ? downloadFilename : username + "_" + downloadFilename;

        // If it's audio file, it is always stored it in a separate folder.
        if (SPLIT_BY_USERNAME || isAudioFile) {
            subFolder += "/" + username;
        }

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(publicFolder),
                subFolder
        );

        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, filename);
        if (file.exists()) {
            Utils.showToastShort(Strings.MEDIA_EXISTS);
            return;
        }

        File temp = new File(
                Environment.getExternalStoragePublicDirectory(publicFolder),
                subFolder + "/temp_" + filename
        );

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription(Strings.DOWNLOADING_MEDIA + filename);
        request.setTitle(filename);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        request.setDestinationInExternalPublicDir(publicFolder, subFolder + "/" + filename);

        DownloadManager manager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    downloader(ctx, filename, file, temp, intent, downloadId, this);
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    downloader(ctx, filename, file, temp, intent, downloadId, this);
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

}
