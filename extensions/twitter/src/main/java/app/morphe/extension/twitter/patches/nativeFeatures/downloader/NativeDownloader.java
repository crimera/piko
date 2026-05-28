/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.downloader;

import static app.morphe.extension.shared.StringRef.str;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
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

    private static void alertBox(Context ctx, String filename, ArrayList<Media> mediaData) throws NoSuchFieldException, IllegalAccessException {
        String photo = str("drafts_empty_photo");
        String video = str("drafts_empty_video");

        LinearLayout ln = new LinearLayout(ctx);
        ln.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(str("piko_pref_native_downloader_alert_title"));

        int n = mediaData.size();
        String[] choices = new String[n];
        for (int i = 0; i < n; i++) {
            Media media = mediaData.get(i);
            String typ = media.type == 0?photo:video;
            choices[i] = "• " + typ + " " + (i + 1);
        }

        builder.setItems(choices, (dialogInterface, which) -> {
            Media media = mediaData.get(which);

            PikoUtils.toast(str("download_started"));
            app.morphe.extension.twitter.Utils.downloadFile(media.url, filename + (which + 1), media.ext);
        });

        builder.setNegativeButton(str("piko_pref_native_downloader_download_all"), (dialogInterface, index) -> {
            PikoUtils.toast(str("download_started"));

            int i = 1;
            for (Media media : mediaData) {
                app.morphe.extension.twitter.Utils.downloadFile(media.url, filename + i, media.ext);
                i++;
            }
            dialogInterface.dismiss();
        });

        builder.show();
    }

    public static void downloader(Context activity, Object tweetObj) throws NoSuchMethodException,
            InvocationTargetException, IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        try {
            Tweet tweet = new Tweet(tweetObj);
            ArrayList<Media> media = tweet.getMedias();

            assert media != null;
            if (media.isEmpty()) {
                PikoUtils.toast(str("piko_pref_native_downloader_no_media"));
                return;
            }

            String fileName = generateFileName(tweet);

            if (media.size() == 1) {
                Media item = media.get(0);
                PikoUtils.toast(str("download_started"));
                app.morphe.extension.twitter.Utils.downloadFile(item.url, fileName, item.ext);
                return;
            }

            alertBox(activity, fileName + "-", media);
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
    }

}
