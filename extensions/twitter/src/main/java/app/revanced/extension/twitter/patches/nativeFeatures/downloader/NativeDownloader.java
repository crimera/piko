package app.revanced.extension.twitter.patches.nativeFeatures.downloader;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.LinearLayout;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.twitter.Pref;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import app.revanced.extension.twitter.entity.Video;
import app.revanced.extension.twitter.entity.Media;
import app.revanced.extension.twitter.entity.Tweet;
import app.revanced.extension.twitter.entity.ExtMediaEntities;

public class NativeDownloader {
    public static String downloadString() {
        return Utils.strRes("piko_pref_native_downloader_alert_title");
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
        String photo = Utils.strRes("drafts_empty_photo");
        String video = Utils.strRes("drafts_empty_video");

        LinearLayout ln = new LinearLayout(ctx);
        ln.setOrientation(LinearLayout.VERTICAL);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(Utils.strRes("piko_pref_native_downloader_alert_title"));

        int n = mediaData.size();
        String[] choices = new String[n];
        for (int i = 0; i < n; i++) {
            Media media = mediaData.get(i);
            String typ = media.type == 0?photo:video;
            choices[i] = "• " + typ + " " + (i + 1);
        }

        builder.setItems(choices, (dialogInterface, which) -> {
            Media media = mediaData.get(which);

            Utils.toast(Utils.strRes("download_started"));
            Utils.downloadFile(media.url, filename + (which + 1), media.ext);
        });

        builder.setNegativeButton(Utils.strRes("piko_pref_native_downloader_download_all"), (dialogInterface, index) -> {
            Utils.toast(Utils.strRes("download_started"));

            int i = 1;
            for (Media media : mediaData) {
                Utils.downloadFile(media.url, filename + i, media.ext);
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
                Utils.toast(Utils.strRes("piko_pref_native_downloader_no_media"));
                return;
            }

            String fileName = generateFileName(tweet);

            if (media.size() == 1) {
                Media item = media.get(0);
                Utils.toast(Utils.strRes("download_started"));
                Utils.downloadFile(item.url, fileName, item.ext);
                return;
            }

            alertBox(activity, fileName + "-", media);
        } catch (Exception ex) {
            Utils.logger(ex);
        }
    }

}
