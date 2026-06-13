/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton;

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

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;

import com.instagram.feed.media.mediaoption.MediaOption$Option;

public class FeedButton {

    private static MediaOption$Option initOverflowButton(String tag, int randomIndex, String drawableResName){
        int drawableIconId = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,drawableResName);
        return new MediaOption$Option(tag, randomIndex, drawableIconId);
    }

    public static MediaOption$Option downloadOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DOWNLOAD", 500, "instagram_download_outline_24");
    }

    public static MediaOption$Option morePostOptionOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_MORE_POST_OPTION", 501, "instagram_info_outline_24");
    }

    public static MediaOption$Option debugOverflowButton(){
        return FeedButton.initOverflowButton("PIKO_DEBUG", 502, "instagram_app_instagram_pano_outline_24");
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
    private static void addDownloadButton(Object buttonAdderObject, ArrayList buttonlist) throws Exception {
        String DOWNLOAD_BUTTON_TEXT = Strings.DOWNLOAD_OPTIONS;
        if(Pref.enableDirectDownload()){
            DOWNLOAD_BUTTON_TEXT = Strings.CATEGORY_DOWNLOAD_MEDIA;
        }
        addButton(MediaOption$Option.PIKO_DOWNLOAD, DOWNLOAD_BUTTON_TEXT, buttonAdderObject, buttonlist);
    }

    public static void addFeedOverflowButton(Object buttonAdderObject, ArrayList buttonlist){
        try {
            if(Pref.enableDownload() && SettingsStatus.downloadMedia) {
                addDownloadButton(buttonAdderObject, buttonlist);
            }
            if(Pref.moreOptionsOnPost() && SettingsStatus.moreOptionsOnPost) {
                addButton(MediaOption$Option.PIKO_MORE_POST_OPTION, Strings.POST_OPTIONS, buttonAdderObject, buttonlist);
            }
            if(Pref.pikoDebug()){
                addButton(MediaOption$Option.PIKO_DEBUG, Strings.PIKO_DEBUG, buttonAdderObject, buttonlist);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    private static boolean isButtonPressed(MediaOption$Option buttonToCheck, MediaOption$Option pressedButton){
        try {
            return pressedButton.equals(buttonToCheck);
        } catch (Exception e) {
            Logger.printException(() -> "Error at isButtonPressed",e);
        }
        return false;
    }

    public static boolean isDownloadButton(MediaOption$Option buttonOption){
        return FeedButton.isButtonPressed(MediaOption$Option.PIKO_DOWNLOAD, buttonOption);
    }

    public static boolean isMoreOptionsOnPostButton(MediaOption$Option buttonOption){
        return FeedButton.isButtonPressed(MediaOption$Option.PIKO_MORE_POST_OPTION, buttonOption);
    }

    public static boolean isDebugButton(MediaOption$Option buttonOption){
        return FeedButton.isButtonPressed(MediaOption$Option.PIKO_DEBUG, buttonOption);
    }

}