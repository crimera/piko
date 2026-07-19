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

public class NativeDownloader {
    public static String downloadString() {
        return str("piko_pref_native_downloader_alert_title");
    }
    private static String getExtension(String typ) {
        if (typ.equals("video/mp4")) {
            return "mp4";
        }
        if (typ.equals("video/webm")) {
            return "webm";
        }
        if (typ.equals("application/x-mpegURL")) {
            return "m3u8";
        }
        return "jpg";
    }

    private static String generateFileName(Tweet tweet) throws Exception {
        String tweetId = ""+tweet.getTweetId();
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

    private static void alertBox(Context context, String filename, ArrayList<ArrayList<Media>> mediaData) throws NoSuchFieldException, IllegalAccessException {
        String photoLabel = str("drafts_empty_photo");
        String videoLabel = str("drafts_empty_video");


        ArrayList<DownloadItem> items = new ArrayList();

        for(int i = 0; i < mediaData.size(); i++){
            ArrayList<DownloadItem> variantList = new ArrayList();
            ArrayList<Media> mediaList = mediaData.get(i);

            for(int j=0; j<mediaList.size(); j++){
                Media media = mediaList.get(j);

                String labelText = media.type == 0 ? photoLabel : videoLabel;
                String resolution = media.resolution;
                labelText+=" - "+resolution;
                String fileName = filename+"_"+(i+1)+"_"+resolution;

                variantList.add(new DownloadItem(labelText,fileName, media));
            }
            DownloadItem highestResMedia = variantList.get(0);
            String labelText = highestResMedia.labelText;
            String fileName = highestResMedia.fileName;
            Media media = highestResMedia.media;

            items.add(new DownloadItem(labelText,fileName,media,variantList));
        }
        DownloadDialog.buildDialog(context, str("piko_pref_native_downloader_alert_title"), items);
    }

    public static void downloader(Context activity, Object tweetObj) {
        try {
            Tweet tweet = new Tweet(tweetObj);
            ArrayList<ArrayList<Media>> media = tweet.getMediaList();

            assert media != null;
            if (media.isEmpty()) {
                PikoUtils.toast(str("piko_pref_native_downloader_no_media"));
                return;
            }

            String fileName = generateFileName(tweet);

            alertBox(activity, fileName, media);
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
    }

}
