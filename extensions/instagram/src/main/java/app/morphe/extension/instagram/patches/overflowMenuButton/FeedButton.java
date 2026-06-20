/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.crimera.ObjectBrowser;

import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.patches.feed.MoreOptionsOnPostPatch;
import app.morphe.extension.instagram.settings.ActivityHook;

import com.instagram.feed.media.mediaoption.MediaOption$Option;
import com.instagram.common.session.UserSession;

public class FeedButton {

    private static MediaOption$Option initOverflowButton(String tag, int randomIndex, String drawableResName){
        int drawableIconId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,drawableResName);
        return new MediaOption$Option(tag, randomIndex, drawableIconId);
    }

    public static MediaOption$Option[] addToMenuOptionArray() {
        MediaOption$Option[] originalArray = MediaOption$Option.$values();
        List<MediaOption$Option> additionalButtonsList = new ArrayList<>();

        if(SettingsStatus.downloadMedia){
            additionalButtonsList.add(MediaOption$Option.PIKO_DOWNLOAD);
        }
        if(SettingsStatus.moreOptionsOnPost){
            additionalButtonsList.add(MediaOption$Option.PIKO_MORE_POST_OPTION);
        }
        if(SettingsStatus.downloadWithExternalDownloader){
            additionalButtonsList.add(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER);
        }

        int additionalButtonListSize = additionalButtonsList.size();

        if(additionalButtonListSize > 0) {
            int originalLength = originalArray.length;

            // Create a new array that can hold the existing value as well as newly added buttons.
            MediaOption$Option[] newButtonArray = new MediaOption$Option[additionalButtonListSize + originalLength];

            // Copy elements from the old array to the new array.
            System.arraycopy(originalArray, 0, newButtonArray, 0, originalLength);

            for(MediaOption$Option button : additionalButtonsList){
                newButtonArray[originalLength++] = button;
            }
            return newButtonArray;
        }
        // Returns the original array as a fallback.
        return originalArray;
    }


    private static Class<?> getEnumButtonClass() throws Exception {
        return Class.forName("X.6zl");
    }
    private static Object enumNormalButton() throws Exception {
        return (Object) new Entity().getMethod(getEnumButtonClass(),"valueOf","NORMAL");
    }

    private static void addButton(MediaOption$Option overflowButton, String overflowButtonText, Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        Class<?> clazz = buttonAdderObject.getClass();

        Method method = clazz.getDeclaredMethod(
                "A00",
                getEnumButtonClass(),
                MediaOption$Option.class,
                clazz,
                CharSequence.class,
                ArrayList.class,
                boolean.class
        );

        method.setAccessible(true);
        method.invoke(null, enumNormalButton(), overflowButton, buttonAdderObject, overflowButtonText, buttonlist, false);
    }

    public static MediaOption$Option downloadOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DOWNLOAD", 500, UI.DRAWABLE_DOWNLOAD_ICON);
    }

    public static MediaOption$Option morePostOptionOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_MORE_POST_OPTION", 501, UI.DRAWABLE_BLUB_ICON);
    }

    public static MediaOption$Option debugOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DEBUG", 502, UI.DRAWABLE_DEBUG_ICON);
    }

    public static MediaOption$Option externalDownloaderOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_EXTERNAL_DOWNLOADER", 503, UI.DRAWABLE_DOWNLOAD_ICON);
    }


    private static void addDownloadButton(Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        String DOWNLOAD_BUTTON_TEXT = str("piko_download_options");
        if(Pref.enableDirectDownload()){
            DOWNLOAD_BUTTON_TEXT = str("piko_category_download_media");
        }
        addButton(MediaOption$Option.PIKO_DOWNLOAD, DOWNLOAD_BUTTON_TEXT, buttonAdderObject, buttonlist);
    }

    public static void addFeedOverflowButton(Object buttonAdderObject, ArrayList buttonlist){
        try {
            if(Pref.pikoDebug()){
                addButton(MediaOption$Option.PIKO_DEBUG, str("piko_debug"), buttonAdderObject, buttonlist);
            }
            if(Pref.enableDownload()) {
                addDownloadButton(buttonAdderObject, buttonlist);
            }
            if(Pref.downloadWithExternalDownloader()) {
                addButton(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER, str("piko_download_with_external_downloader"), buttonAdderObject, buttonlist);
            }
            if(Pref.moreOptionsOnPost()) {
                addButton(MediaOption$Option.PIKO_MORE_POST_OPTION, str("piko_more_options"), buttonAdderObject, buttonlist);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    public static boolean isCustomButtonPressed(MediaOption$Option pressedButton){
        return (
                pressedButton.equals(MediaOption$Option.PIKO_DEBUG) ||
                (SettingsStatus.downloadMedia && pressedButton.equals(MediaOption$Option.PIKO_DOWNLOAD)) ||
                (SettingsStatus.moreOptionsOnPost && pressedButton.equals(MediaOption$Option.PIKO_MORE_POST_OPTION)) ||
                (SettingsStatus.downloadWithExternalDownloader && pressedButton.equals(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER))
        );
    }

    public static void customButtonOnClick(MediaOption$Option pressedButton, UserSession userSession, Context context, Object mediaObject, int currentMediaIndex){
        try{
            if(pressedButton.equals(MediaOption$Option.PIKO_DEBUG)) {
                ObjectBrowser.browseObject(context, new MediaData(mediaObject, userSession));

            } else if (SettingsStatus.downloadMedia && pressedButton.equals(MediaOption$Option.PIKO_DOWNLOAD)) {
                DownloadUtils.downloadPost(context, userSession, mediaObject, currentMediaIndex);

            } else if (SettingsStatus.moreOptionsOnPost && pressedButton.equals(MediaOption$Option.PIKO_MORE_POST_OPTION)) {
                MoreOptionsOnPostPatch.postMoreOptions(context, userSession, mediaObject, currentMediaIndex);

            } else if (SettingsStatus.downloadWithExternalDownloader && pressedButton.equals(MediaOption$Option.PIKO_EXTERNAL_DOWNLOADER)) {
                DownloadUtils.externalDownloader(mediaObject,currentMediaIndex);

            }

        } catch (Exception e) {
            Logger.printException(() -> "Error at customButtonOnClick",e);
        }
    }

}