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

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.VideoData;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.AudioMediaInterface;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaDownloader;
import app.morphe.extension.crimera.downloader.DownloadRequest;
import app.morphe.extension.crimera.downloader.MediaType;
import app.morphe.extension.crimera.PikoUtils;

public class DownloadUtils {
    private static boolean ENABLE_DIRECT_DOWNLOAD;
    private static boolean SPLIT_BY_USERNAME;
    private static boolean DEBUG;

    static {
        ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
        SPLIT_BY_USERNAME = Pref.downloadUsernameFolder() && SettingsStatus.downloadMedia;
        DEBUG = Pref.pikoDebug();
    }

    private static void buildVideoVariantDialogBox(Context context, MediaData currentMediaData) throws Exception {
        String username = currentMediaData.getUserData().getUsername();
        List<VideoData> videoDataList = currentMediaData.getVideoVariants();

        InstagramDialogBox dialog = new InstagramDialogBox(context);
        ArrayList<String> options = new ArrayList<>();
        videoDataList.forEach(item -> options.add(item.getVideoVariantTag()));
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                VideoData videoData = videoDataList.get(which);

                try {
                    String filename = currentMediaData.getVideoVariantFileName(videoData);
                    String mediaUrl = videoData.getUrl();
                    downloadMediaUrl(context,mediaUrl,username,filename);
                } catch (Exception e) {
                    PikoUtils.logger(e);
                    Logger.printException(() -> "Error at buildVideoVariantDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }

            }
        });

        dialog.setTitle(Strings.VIDEO_VARIANTS);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();

    }

    private static void downloadDialogBox(Context context, MediaData mediaInfo, int position) throws Exception {
        int carouselSize = mediaInfo.getCarouselSize();
        MediaData currentMediaData = mediaInfo.getMediaAt(position);
        String username = mediaInfo.getUserData().getUsername();
        Boolean isCurrentMediaVideo = currentMediaData.isVideo();
        Boolean currentMediaHasAudio = currentMediaData.hasAudio();

        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(Strings.DOWNLOAD_CURRENT_MEDIA);
        options.add(Strings.DOWNLOAD_AS_IMAGE);
        if (currentMediaHasAudio) options.add(Strings.DOWNLOAD_AUDIO);
        options.add(Strings.COPY_MEDIA_LINK);
        if (isCurrentMediaVideo) {
            options.add(Strings.VIDEO_VARIANTS);
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
                        downloadMedia(context, mediaInfo, position, MediaType.ANY);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_AS_IMAGE)) {
                        downloadMedia(context, mediaInfo, position, MediaType.IMAGE);

                    } else if (selectedOption.equals(Strings.COPY_MEDIA_LINK)) {
                        Utils.setClipboard(currentMediaData.getMediaLink());
                        Utils.showToastShort(Strings.COPIED_MEDIA_LINK);

                    } else if (selectedOption.equals(Strings.OPEN_VIDEO_EXTERNALLY) || selectedOption.equals(Strings.OPEN_IMAGE_EXTERNALLY)) {
                        ActivityHook.handleUrlIntent(isCurrentMediaVideo, currentMediaData.getMediaLink());

                    } else if (selectedOption.equals(Strings.DOWNLOAD_ALL)) {
                        downloadMedia(context, mediaInfo, -1, MediaType.ANY);

                    } else if (selectedOption.equals(Strings.PIKO_DEBUG)) {
                        ObjectBrowser.browseObject(context, currentMediaData);

                    } else if (selectedOption.equals(Strings.DOWNLOAD_AUDIO)) {
                        downloadMedia(context, mediaInfo, position, MediaType.AUDIO);

                    } else if (selectedOption.equals(Strings.VIDEO_VARIANTS)) {
                        buildVideoVariantDialogBox(context, currentMediaData);

                    }
                } catch (Exception e) {
                    PikoUtils.logger(e);
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


    public static void downloadPost(Context context, Object mediaObject, int position) {
        try {
            position = position < 1 ? 0 : position;
            MediaData mediaInfo = new MediaData(mediaObject);
            if (ENABLE_DIRECT_DOWNLOAD) {
                downloadMedia(context, mediaInfo, position, MediaType.ANY);
            } else {
                downloadDialogBox(context, mediaInfo, position);
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
            Logger.printException(() -> "Error at downloadPost", e);
        }
    }

    // Position is set to -1 if we want to download all medias from the media info object.
    public static void downloadMedia(Context context, MediaData mediaInfo, int position, MediaType mediaType) throws Exception {
        if(!Utils.isNetworkConnected()){
            Utils.showToastShort(Strings.NO_INTERNET);
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        String username = mediaInfo.getUserData().getUsername();

        if (mediaType.equals(MediaType.AUDIO)) {
            AudioMediaInterface audioMedia = mediaInfo.getMediaAt(position).getAudioMedia();
            String audioUrl = audioMedia.getAudioUrl();
            String fileName = audioMedia.getDownloadName() + ".mp3";
            downloader.enqueue(new DownloadRequest(audioUrl, Strings.DEFAULT_AUDIO_FOLDER, fileName));

        } else if (position != -1) {
            MediaData mediaData = mediaInfo.getMediaAt(position);
            String mediaUrl;
            if (mediaType.equals(MediaType.IMAGE)) {
                mediaUrl = mediaData.getPhotoLink();
            } else {
                mediaUrl = mediaData.getMediaLink();
            }
            String fileName = mediaData.getDownloadFilename(mediaType);
            downloader.enqueue(new DownloadRequest(mediaUrl, username, fileName));

        } else if (position == -1) {
            int carouselSize = mediaInfo.getCarouselSize();

            for (int index = 0; index < carouselSize; index++) {
                MediaData currentMediaData = mediaInfo.getMediaAt(index);
                String fileName = currentMediaData.getDownloadFilename(MediaType.ANY);
                String mediaUrl = currentMediaData.getMediaLink();
                downloader.enqueue(new DownloadRequest(mediaUrl, username, fileName));
            }
        } else {
            Utils.showToastShort("There is nothing to download");
        }

    }


    public static void downloadMediaUrl(Context context, String mediaUrl, String username, String fileName) throws Exception {
        if(!Utils.isNetworkConnected()){
            Utils.showToastShort(Strings.NO_INTERNET);
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        downloader.enqueue(new DownloadRequest(mediaUrl, username, fileName));
    }

}
