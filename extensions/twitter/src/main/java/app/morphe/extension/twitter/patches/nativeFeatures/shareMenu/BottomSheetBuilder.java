/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.shareMenu;

import android.content.Intent;
import android.content.Context;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static app.morphe.extension.shared.StringRef.str;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.ObjectBrowser;

import app.morphe.extension.twitter.patches.nativeFeatures.downloader.NativeDownloader;
import app.morphe.extension.twitter.patches.nativeFeatures.shareImage.ShareImageHandler;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.NativeTranslator;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;
import app.morphe.extension.twitter.patches.links.ExternalDownloader;

import app.morphe.extension.twitter.entity.Tweet;
import app.morphe.extension.twitter.patches.links.Urls;
import app.morphe.extension.twitter.Pref;

public class BottomSheetBuilder {

    private static List<BottomSheetAction<Tweet>> actionList(Context context, Tweet tweet) throws Exception{
        Object tweetObject = tweet.getObject();
        List<BottomSheetAction<Tweet>> actions = new ArrayList<>();
        ArrayList itemsToHide = Pref.nativeShareMenuToHide();

        String itemKey = "copy_tweet_link";
        if(!itemsToHide.contains(itemKey)) {
            actions.add(new BottomSheetAction<Tweet>("ic_vector_link", str(itemKey), t -> copyLink(context, t)));
        }

        itemKey = "share_tweet_sheet_title";
        if(!itemsToHide.contains(itemKey)) {
            actions.add(new BottomSheetAction<>("ic_vector_share_android", str(itemKey), t -> shareVia(context, t)));
        }

        itemKey = "label_chat";
        if(!itemsToHide.contains(itemKey)) {
            actions.add(new BottomSheetAction<>("ic_vector_compose_dm", str(itemKey), t -> shareToDM(context, t)));
        }

        itemKey = "piko_share_image_instagram_stories";
        if(!itemsToHide.contains(itemKey)) {
            actions.add(new BottomSheetAction<>("ic_vector_logo_instagram", str(itemKey), t -> ShareImageHandler.shareAsImage(context, tweetObject, 1)));
        }

        itemKey = "piko_pref_external_downloader_text";
        if(Pref.enableExternalDownloader() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_incoming",str(itemKey),t -> ExternalDownloader.sendToExternalDownloader(tweetObject)));
        }

        itemKey = "piko_title_native_downloader";
        if(Pref.enableNativeDownloader() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_incoming",str(itemKey),t -> NativeDownloader.downloader(context, tweetObject)));
        }

        itemKey = "piko_title_native_translator";
        if(Pref.enableNativeTranslator() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_sparkle",str(itemKey),t -> NativeTranslator.translate(context, tweetObject)));
        }

        itemKey = "piko_title_native_reader_mode";
        if(Pref.enableNativeReaderMode() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_book_stroke_on",str(itemKey),t -> ReaderModeUtils.launchReaderMode(context, tweetObject)));
        }

        itemKey = "piko_share_image_title";
        if(Pref.enableShareImage() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_share",str(itemKey),t -> ShareImageHandler.shareAsImage(context, tweetObject,0)));
        }

        itemKey = "piko_debug";
        if(Pref.pikoDebug() && !itemsToHide.contains(itemKey)){
            actions.add(new BottomSheetAction<>("ic_vector_flask_stroke",str(itemKey),t -> ObjectBrowser.browseObject(context, tweet)));
        }

        Collections.reverse(actions);
        return actions;
    }

    /**
     * Shows the "Share post" bottom sheet.
     *
     * @param context Activity context (required for dialog creation).
     * @param tweetObj    The tweet object.
     */
    public static void showShareSheet(Context context, Object tweetObj) {
        try {


            Tweet tweet = new Tweet(tweetObj);
            BottomSheetHelper.show(
                    context,
                    tweet,
                    str("share_tweet_sheet_title"),
                    actionList(context,tweet),
                    null   // onDismiss
            );
        } catch (java.lang.Exception e) {
            PikoUtils.logger(e);
        }
    }

    // ------------------------------------------------------------------
    // Action implementations
    // ------------------------------------------------------------------

    private static String generateShareLink(Tweet tweet) throws Exception{
        String link = tweet.getTweetLink();
        return Urls.changeDomain(link);
    }

    private static void copyLink(Context ctx, Tweet tweet){
        try {
            String link = generateShareLink(tweet);
            Utils.setClipboard(link);
            Utils.showToastShort(link);

        } catch (Exception e) {
            PikoUtils.logger(e);
            Utils.showToastShort(e.getMessage());
        }
    }

    private static void shareVia(Context ctx, Tweet tweet){
        try {
            String link = generateShareLink(tweet);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, link);
            ctx.startActivity(Intent.createChooser(intent,str("room_settings_system_share_via")));

        } catch (Exception e) {
            PikoUtils.logger(e);
        }

    }

    private static void shareToDM(Context ctx, Tweet tweet){
        try {
            String link = tweet.getTweetLink();

            Intent intent = new Intent("com.twitter.app.dm.DMActivity");
            intent.setPackage("com.twitter.android");

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, link);
            intent.setType("text/plain");
            ctx.startActivity(intent);

        } catch (Exception e) {
            PikoUtils.logger(e);
        }

    }
}