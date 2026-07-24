/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.downloader;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.twitter.Pref;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import app.morphe.extension.twitter.entity.Media;
import app.morphe.extension.twitter.entity.Tweet;
import java.util.List;

public class NativeDownloader {
    public static String downloadString() {
        return str("piko_pref_native_downloader_alert_title");
    }

    private static String generateFileName(Tweet tweet) throws Exception {
        String tweetId = String.valueOf(tweet.getTweetId());
        int fileNameType = Pref.nativeDownloaderFileNameType();
        switch (fileNameType) {
            case 1:
                return tweet.getTweetUsername() + "_" + tweetId;
            case 2:
                return tweet.getTweetProfileName() + "_" + tweetId;
            case 3:
                return tweet.getTweetUserId() + "_" + tweetId;
            case 5:
                return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            default:
                return tweetId;
        }
    }

    public static ArrayList<DownloadItem> buildDownloadItems(
            Context context, String filename, ArrayList<ArrayList<Media>> mediaData
    ) {
        String photoLabel = str("drafts_empty_photo");
        String videoLabel = str("drafts_empty_video");

        ArrayList<DownloadItem> items = new ArrayList<>();
        boolean multipleMedia = mediaData.size() > 1;

        for (int i = 0; i < mediaData.size(); i++) {
            ArrayList<DownloadItem> variantList = new ArrayList<>();
            ArrayList<Media> mediaList = mediaData.get(i);
            boolean multipleVariants = mediaList.size() > 1;

            for (int j = 0; j < mediaList.size(); j++) {
                Media media = mediaList.get(j);
                String resolution = media.resolution;
                // Title for variants dialog
                String variantLabel = resolution;

                StringBuilder sb = new StringBuilder(filename);
                if (multipleMedia) {
                    sb.append("_").append(i + 1);
                }
                if (multipleVariants) {
                    sb.append("_").append(resolution);
                }
                String itemFileName = sb.toString();

                variantList.add(new DownloadItem(variantLabel, null, itemFileName, media));
            }

            DownloadItem highestResMedia = variantList.get(0);
            Media mainMedia = highestResMedia.media;

            // Title and subtitle for the main dialog list
            String mainLabel = (mainMedia.type == 0 ? photoLabel : videoLabel) + " " + (i + 1);
            String mainSubtitle = mainMedia.resolution;

            items.add(new DownloadItem(mainLabel, mainSubtitle, highestResMedia.fileName, mainMedia, variantList));
        }
        return items;
    }

    private static void alertBox(Context context, String filename, ArrayList<ArrayList<Media>> mediaData) {
        ArrayList<DownloadItem> items = buildDownloadItems(context, filename, mediaData);

        if (items.size() == 1 && items.get(0).hasVariants()) {
            DownloadDialog.buildDialog(context, str("piko_video_variants"), items.get(0).variants);
        } else {
            DownloadDialog.buildDialog(context, str("piko_pref_native_downloader_alert_title"), items);
        }
    }

    public static void downloader(Context activity, Object tweetObj) {
        try {
            Tweet tweet = new Tweet(tweetObj);
            ArrayList<ArrayList<Media>> media = tweet.getMediaList();

            if (media == null || media.isEmpty()) {
                PikoUtils.toast(str("piko_pref_native_downloader_no_media"));
                return;
            }

            String fileName = generateFileName(tweet);

            // Skip dialog if there is only one media and one variant
            if (media.size() == 1 && media.get(0).size() == 1) {
                Media singleMedia = media.get(0).get(0);
                PikoUtils.toast(str("download_started"));
                app.morphe.extension.twitter.Utils.downloadFile(singleMedia.url, fileName, singleMedia.ext);
                return;
            }

            // Skip resolution dialog if autodownload highest res is enabled and only one video is present
            if (Pref.nativeDownloaderAutodownloadHighestVideoRes() && media.size() == 1 && media.get(0).size() > 1) {
                Media highestResMedia = media.get(0).get(0);
                PikoUtils.toast(str("download_started"));
                app.morphe.extension.twitter.Utils.downloadFile(highestResMedia.url, fileName + "_" + highestResMedia.resolution, highestResMedia.ext);
                return;
            }

            alertBox(activity, fileName, media);
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
    }

    public static void downloadAllFromTweet(Context activity, Object tweetObj) {
        try {
            Tweet tweet = new Tweet(tweetObj);
            ArrayList<ArrayList<Media>> mediaList = tweet.getMediaList();

            if (mediaList == null || mediaList.isEmpty()) {
                PikoUtils.toast(str("piko_pref_native_downloader_no_media"));
                return;
            }

            String fileName = generateFileName(tweet);
            List<DownloadItem> items = buildDownloadItems(activity, fileName, mediaList);
            DownloadDialog.downloadAll(items);
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
    }
}
