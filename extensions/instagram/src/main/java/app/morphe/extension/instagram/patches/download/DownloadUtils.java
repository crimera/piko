/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches.download;

import android.os.Build;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.app.Activity;
import java.util.ArrayList;
import android.media.MediaScannerConnection;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class DownloadUtils {
    private static boolean ENABLE_DIRECT_DOWNLOAD,SPLIT_BY_USERNAME;
    static{
        ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
        SPLIT_BY_USERNAME = Pref.downloadUsernameFolder() && SettingsStatus.downloadMedia;
    }

    private static void downloadDialogBox(Context context, MediaData mediaInfo, int position) throws Exception{
        int carouselSize = mediaInfo.getCarouselSize();
        MediaData currentMediaData = mediaInfo.getMediaAt(position);

        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(Strings.DOWNLOAD_CURRENT_MEDIA);
        options.add(Strings.DOWNLOAD_AS_IMAGE);
        options.add(Strings.COPY_MEDIA_LINK);
        if(carouselSize > 1) options.add(Strings.DOWNLOAD_ALL);
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(Strings.DOWNLOAD_CURRENT_MEDIA)) {
                        downloadMediaAt(context,mediaInfo,position);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_AS_IMAGE)) {
                        String username = mediaInfo.getUserData().getUsername();

                        String downloadFileName = mediaInfo.getDownloadFilename(true);
                        downloadFile(context, currentMediaData.getPhotoLink(), username, downloadFileName);

                    } else if (selectedOption.equals(Strings.COPY_MEDIA_LINK)) {
                        Utils.setClipboard(currentMediaData.getMediaLink());
                        Utils.showToastShort(Strings.COPIED_MEDIA_LINK);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_ALL)) {
                        for(int index=0;index<carouselSize;index++){
                            downloadMediaAt(context,mediaInfo,index);
                        }
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error at downloadDialogBox",e);
                    Utils.showToastShort(Strings.DOWNLOAD_FAILED_MEDIA + e.getMessage());
                }
            }
        });


        dialog.setTitle(Strings.DOWNLOAD_OPTIONS);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    public static void downloadMediaAt(Context context, MediaData mediaInfo, int position) throws Exception{
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        String username = mediaInfo.getUserData().getUsername();
        String downloadFileName = currentMediaData.getDownloadFilename(false);

        downloadFile(context, currentMediaData.getMediaLink(), username, downloadFileName);
    }


    public static void downloadPost(Context context, Object mediaObject, int position){
        try{
            position = position < 1 ? 0 : position;
            MediaData mediaInfo = new MediaData(mediaObject);
            if(ENABLE_DIRECT_DOWNLOAD){
                downloadMediaAt(context,mediaInfo,position);
            }else {
                downloadDialogBox(context, mediaInfo, position);
            }

        } catch (Exception e) {
            Logger.printException(() -> "Error at downloadPost",e);
            Utils.showToastShort(Strings.DOWNLOAD_FAILED_MEDIA + e.getMessage());
        }
    }

    private static final int BUFFER_SIZE = 16384;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private static final String USER_AGENT = "Instagram " + Utils.getAppVersionName()
            + " Android (" + Build.MANUFACTURER + "; " + Build.MODEL + "; " + Build.DISPLAY + ")";

    // Fast download engine — inspiration and core logic adapted from MyInsta by bluepapilte.
    private static void downloadFile(Context ctx, String url, String username, String downloadFilename) {
        String publicFolder = Environment.DIRECTORY_DOWNLOADS;
        String subFolder = "Piko-Instagram";
        final String filename = username + "_" + downloadFilename;

        if (SPLIT_BY_USERNAME) {
            subFolder += "/" + username;
        }

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(publicFolder),
                subFolder
        );

        if (!dir.exists()) {
            dir.mkdirs();
        }

        final File file = new File(dir, filename);
        if (file.exists()) {
            Utils.showToastShort(Strings.MEDIA_EXISTS);
            return;
        }

        Utils.showToastShort(Strings.DOWNLOADING_MEDIA + filename);

        Utils.runOnBackgroundThread(() -> {
            HttpURLConnection connection = null;
            try {
                URL downloadUrl = new URL(url);
                connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Utils.showToastShort(Strings.DOWNLOAD_FAILED_MEDIA + "HTTP " + responseCode);
                    Logger.printException(() -> "Download failed for " + filename
                            + ", HTTP status=" + responseCode, null);
                    return;
                }

                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream(), BUFFER_SIZE);
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                }

                MediaScannerConnection.scanFile(ctx,
                        new String[]{file.getAbsolutePath()}, null, null);

                Utils.showToastShort(Strings.DOWNLOADED_MEDIA + filename);

            } catch (Exception e) {
                if (file.exists()) {
                    file.delete();
                }
                Logger.printException(() -> "Download failed for " + filename, e);
                Utils.showToastShort(Strings.DOWNLOAD_FAILED_MEDIA + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

}
