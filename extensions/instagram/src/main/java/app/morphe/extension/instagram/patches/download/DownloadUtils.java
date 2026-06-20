/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.os.Build;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.app.Activity;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.VideoData;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.AudioMediaInterface;
import app.morphe.extension.instagram.entity.MediaInterface;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.crimera.downloader.MediaDownloader;
import app.morphe.extension.crimera.downloader.DownloadRequest;
import app.morphe.extension.crimera.downloader.MediaType;
import app.morphe.extension.crimera.PikoUtils;

import com.instagram.common.session.UserSession;

public class DownloadUtils {

    public static String getSubfolderName(String username){
        boolean SPLIT_BY_USERNAME = Pref.downloadUsernameFolder() && SettingsStatus.downloadMedia;
        return SPLIT_BY_USERNAME ? username : null;
    }

    private static void buildVariantDialogBox(Context context, MediaData currentMediaData, MediaType mediaType) throws Exception {
        String username = currentMediaData.getUserData().getUsername();
        List<MediaInterface> variantList;
        String title = "";
        if(mediaType.equals(MediaType.VIDEO)){
            title = str("piko_video_variants");
            variantList = currentMediaData.getVideoVariants();
        }else{
            title = str("piko_image_variants");
            variantList = currentMediaData.getImageVariants();
        }

        InstagramDialogBox dialog = new InstagramDialogBox(context);
        ArrayList<String> options = new ArrayList<>();
        variantList.forEach(item -> options.add(item.getVariantTag()));
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                MediaInterface data = variantList.get(which);

                try {
                    String filename = username + "_"+currentMediaData.getVariantFileName(data);
                    String mediaUrl = data.getUrl();
                    String subFolder = getSubfolderName(username);
                    downloadMediaUrl(context,mediaUrl,subFolder,filename);
                } catch (Exception e) {
                    PikoUtils.logger(e);
                    Logger.printException(() -> "Error at buildVariantDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }

            }
        });

        dialog.setTitle(title);
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
        options.add(str("piko_download_current_media"));
        options.add(str("piko_download_as_image"));
        if (currentMediaHasAudio) options.add(str("piko_download_audio"));
        options.add(str("piko_copy_media_link"));
        options.add(str("piko_image_variants"));
        if (isCurrentMediaVideo) {
            options.add(str("piko_video_variants"));
            options.add(str("piko_open_video_externally"));
        } else {
            options.add(str("piko_open_image_externally"));
        }

        if (carouselSize > 1) options.add(str("piko_download_all"));

        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(str("piko_download_current_media"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.ANY);

                    } else if (selectedOption.equals(str("piko_download_as_image"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.IMAGE);

                    } else if (selectedOption.equals(str("piko_copy_media_link"))) {
                        Utils.setClipboard(currentMediaData.getMediaLink());
                        Utils.showToastShort(str("piko_copied_media_link"));

                    } else if (selectedOption.equals(str("piko_open_video_externally")) || selectedOption.equals(str("piko_open_image_externally"))) {
                        ActivityHook.handleUrlIntent(isCurrentMediaVideo, currentMediaData.getMediaLink());

                    } else if (selectedOption.equals(str("piko_download_all"))) {
                        downloadMedia(context, mediaInfo, -1, MediaType.ANY);

                    } else if (selectedOption.equals(str("piko_download_audio"))) {
                        downloadMedia(context, mediaInfo, position, MediaType.AUDIO);

                    } else if (selectedOption.equals(str("piko_video_variants"))) {
                        buildVariantDialogBox(context, currentMediaData, MediaType.VIDEO);

                    } else if (selectedOption.equals(str("piko_image_variants"))) {
                        buildVariantDialogBox(context, currentMediaData, MediaType.IMAGE);

                    }
                } catch (Exception e) {
                    PikoUtils.logger(e);
                    Logger.printException(() -> "Error at downloadDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });


        dialog.setTitle(str("piko_download_options"));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }


    public static void downloadPost(Context context,  UserSession userSession, Object mediaObject, int position) {
        try {
            boolean ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
            position = position < 1 ? 0 : position;
            MediaData mediaInfo = new MediaData(mediaObject, userSession);
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
            Utils.showToastShort(str("piko_no_internet"));
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        String username = mediaInfo.getUserData().getUsername();
        String subFolder = getSubfolderName(username);

        if (mediaType.equals(MediaType.AUDIO)) {
            AudioMediaInterface audioMedia = mediaInfo.getMediaAt(position).getAudioMedia();
            String audioUrl = audioMedia.getAudioUrl();
            String fileName = audioMedia.getDownloadName() + ".mp3";
            downloader.enqueue(new DownloadRequest(audioUrl, Constants.DEFAULT_AUDIO_FOLDER, fileName));

        } else if (position != -1) {
            MediaData mediaData = mediaInfo.getMediaAt(position);
            String mediaUrl;
            if (mediaType.equals(MediaType.IMAGE)) {
                mediaUrl = mediaData.getImageLink();
            } else {
                mediaUrl = mediaData.getMediaLink();
            }
            String fileName = username+"_"+mediaData.getDownloadFilename(mediaType);

            downloader.enqueue(new DownloadRequest(mediaUrl, subFolder, fileName));

        } else if (position == -1) {
            int carouselSize = mediaInfo.getCarouselSize();

            for (int index = 0; index < carouselSize; index++) {
                MediaData currentMediaData = mediaInfo.getMediaAt(index);
                String fileName = username+"_"+currentMediaData.getDownloadFilename(MediaType.ANY);
                String mediaUrl = currentMediaData.getMediaLink();
                downloader.enqueue(new DownloadRequest(mediaUrl, subFolder, fileName));
            }
        } else {
            Utils.showToastShort("There is nothing to download");
        }

    }


    public static void downloadMediaUrl(Context context, String mediaUrl, String subFolder, String fileName) throws Exception {
        if(!Utils.isNetworkConnected()){
            Utils.showToastShort(str("piko_no_internet"));
            return;
        }
        MediaDownloader downloader = new MediaDownloader(context);
        downloader.enqueue(new DownloadRequest(mediaUrl, subFolder, fileName));
    }

    public static void externalDownloader(Object mediaObject, int currentMediaIndex){
        try {
            String packageName = Pref.externalDownloaderPackageName();
            packageName = packageName == null ? "" : packageName.trim();
            if(packageName.isEmpty()){
                PikoUtils.toast(str("piko_external_downloader_package_name_not_set"));
                return;
            }
            if(!PikoUtils.isAppInstalledAndEnabled(packageName)){
                PikoUtils.toast(str("piko_external_downloader_package_name_not_found"));
                return;
            }
            String link = Links.generatePostLink(mediaObject, currentMediaIndex);
            ActivityHook.openLink(link, packageName);
        } catch (Exception e){
            PikoUtils.logger(e);
            Logger.printException(() -> "Error at externalDownloader", e);
        }
    }
}
