/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures;

import android.content.Context;

import app.morphe.extension.twitter.patches.nativeFeatures.browse.BrowseTweetObjectPatch;
import app.morphe.extension.twitter.patches.nativeFeatures.downloader.NativeDownloader;
import app.morphe.extension.twitter.patches.nativeFeatures.shareImage.ShareImageHandler;
import app.morphe.extension.twitter.patches.nativeFeatures.translator.NativeTranslator;
import app.morphe.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeUtils;
import app.morphe.extension.twitter.patches.nativeFeatures.shareMenu.BottomSheetBuilder;
import app.morphe.extension.twitter.Pref;

import app.morphe.extension.crimera.PikoUtils;


public class ButtonPressHandler {
    
    private static boolean buttonCheck(Object buttonPressed, String tag){
        return String.valueOf(buttonPressed).equals(tag);
    }
    
    public static boolean isButtonPressed(Object buttonPressed){
        return (buttonCheck(buttonPressed,"ShareImage")) || (buttonCheck(buttonPressed,"ReaderMode")) ||
                (buttonCheck(buttonPressed,"Download")) || (buttonCheck(buttonPressed,"Translate")) ||
                (buttonCheck(buttonPressed,"BrowseObject")) || (buttonCheck(buttonPressed,"TwitterShare") && Pref.enableNativeShareMenu());
    }

    public static void buttonPressAction(Context context, Object buttonPressed, Object tweetObject){
        try {
            if (buttonCheck(buttonPressed, "ShareImage")) {
                ShareImageHandler.shareAsImage(context, tweetObject);

            } else if (buttonCheck(buttonPressed, "ReaderMode")) {
                ReaderModeUtils.launchReaderMode(context, tweetObject);

            } else if (buttonCheck(buttonPressed, "Download")) {
                NativeDownloader.downloader(context, tweetObject);

            } else if (buttonCheck(buttonPressed, "Translate")) {
                NativeTranslator.translate(context, tweetObject);

            } else if (buttonCheck(buttonPressed, "BrowseObject")) {
                BrowseTweetObjectPatch.browse(context, tweetObject);

            } else if (buttonCheck(buttonPressed, "TwitterShare")) {
                BottomSheetBuilder.showShareSheet(context, tweetObject);

            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }

    }
}
