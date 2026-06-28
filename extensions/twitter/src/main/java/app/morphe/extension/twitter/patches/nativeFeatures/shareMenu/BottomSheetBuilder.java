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

import app.morphe.extension.twitter.patches.nativeFeatures.browse.BrowseTweetObjectPatch;
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

        actions.add(new BottomSheetAction<Tweet>("ic_vector_link",str("copy_tweet_link"),t -> copyLink(context, t)));
        actions.add(new BottomSheetAction<>("ic_vector_share_android",str("share_tweet_sheet_title"),t -> shareVia(context, t)));

        actions.add(new BottomSheetAction<>("ic_vector_compose_dm",str("label_chat"),t -> shareToDM(context, t)));

        actions.add(new BottomSheetAction<>("ic_vector_logo_instagram",str("piko_share_image_instagram_stories"),t -> ShareImageHandler.shareAsImage(context, tweetObject,1)));

        if(Pref.enableNativeDownloader()){
            actions.add(new BottomSheetAction<>("ic_vector_incoming",str("piko_title_native_downloader"),t -> NativeDownloader.downloader(context, tweetObject)));
        }

        if(Pref.enableExternalDownloader()){
            actions.add(new BottomSheetAction<>("ic_vector_incoming",str("piko_pref_external_downloader_text"),t -> ExternalDownloader.sendToExternalDownloader(tweetObject)));
        }

        if(Pref.enableNativeTranslator()){
            actions.add(new BottomSheetAction<>("ic_vector_sparkle",str("piko_title_native_translator"),t -> NativeTranslator.translate(context, tweetObject)));
        }

        if(Pref.enableNativeReaderMode()){
            actions.add(new BottomSheetAction<>("ic_vector_book_stroke_on",str("piko_title_native_reader_mode"),t -> ReaderModeUtils.launchReaderMode(context, tweetObject)));
        }

        if(Pref.enableShareImage()){
            actions.add(new BottomSheetAction<>("ic_vector_share",str("piko_share_image_title"),t -> ShareImageHandler.shareAsImage(context, tweetObject,0)));
        }

        if(Pref.browseObject()){
            actions.add(new BottomSheetAction<>("ic_vector_flask_stroke",str("piko_browse_object_title"),t -> BrowseTweetObjectPatch.browse(context, tweetObject)));
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
            String link = generateShareLink(tweet);

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