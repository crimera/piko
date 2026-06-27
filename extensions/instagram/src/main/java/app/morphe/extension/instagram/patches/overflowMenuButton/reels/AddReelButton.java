/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton.reels;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.view.View;
import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.constants.UI;

import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.ReelButton;
import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.DownloadButton;
import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.DebugButton;
import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.InfoButton;
import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.ExternalDownloadButton;

public class AddReelButton {

    private static void addReelButton(Context context, ReelOverflowButton reelOverflowButton, Object helperObject){
        try {
                int icon = ResourceUtils.getIdentifier(ResourceType.DRAWABLE,reelOverflowButton.drawableResId);

                Class<?> clazz = helperObject.getClass();
                Method method = clazz.getDeclaredMethod(
                        "A01",
                        Context.class,
                        View.OnClickListener.class,
                        String.class,
                        int.class
                );

                method.setAccessible(true);

                method.invoke(helperObject, context, reelOverflowButton.reelButton, reelOverflowButton.buttonText, icon);

        } catch (Exception e) {
            Logger.printException(() -> "Error at addReelButton",e);
        }
    }

    private static void addDownloadButton(Context context, Object helperObject, Object mediaObject){
        String icon = UI.DRAWABLE_DOWNLOAD_ICON;
        ReelButton reelButton = new DownloadButton(context,mediaObject);
        String DOWNLOAD_BUTTON_TEXT = str("piko_download_options");
        if(Pref.enableDirectDownload()){
            DOWNLOAD_BUTTON_TEXT = str("piko_category_download_media");
        }

        ReelOverflowButton reelOverflowButton = new ReelOverflowButton(icon,reelButton,DOWNLOAD_BUTTON_TEXT);

        AddReelButton.addReelButton(context,reelOverflowButton,helperObject);
    }

    private static void addInfoButton(Context context, Object helperObject, Object mediaObject){
        String icon = UI.DRAWABLE_BLUB_ICON;
        ReelButton reelButton = new InfoButton(context,mediaObject);
        String buttonText = str("piko_more_options");

        ReelOverflowButton reelOverflowButton = new ReelOverflowButton(icon,reelButton,buttonText);

        AddReelButton.addReelButton(context,reelOverflowButton,helperObject);
    }

    private static void addDebugButton(Context context, Object helperObject, Object mediaObject) {
        String icon = UI.DRAWABLE_DEBUG_ICON;
        ReelButton reelButton = new DebugButton(context, mediaObject);
        String buttonText = str("piko_debug");

        ReelOverflowButton reelOverflowButton = new ReelOverflowButton(icon, reelButton, buttonText);

        AddReelButton.addReelButton(context, reelOverflowButton, helperObject);

    }

    private static void addExternalDownloadButton(Context context, Object helperObject, Object mediaObject){
        String icon = UI.DRAWABLE_DOWNLOAD_ICON;
        ReelButton reelButton = new ExternalDownloadButton(context,mediaObject);
        String buttonText = str("piko_download_with_external_downloader");

        ReelOverflowButton reelOverflowButton = new ReelOverflowButton(icon,reelButton,buttonText);

        AddReelButton.addReelButton(context,reelOverflowButton,helperObject);
    }

    public static void includeCustomReelOverflowButtons(Context context, Object helperObject, Object mediaObject){
        if(Pref.pikoDebug()){
            AddReelButton.addDebugButton(context, helperObject, mediaObject);
        }
        if(Pref.enableDownload()){
            AddReelButton.addDownloadButton(context, helperObject, mediaObject);
        }
        if(Pref.downloadWithExternalDownloader()){
            AddReelButton.addExternalDownloadButton(context, helperObject, mediaObject);
        }
        if(Pref.moreOptionsOnPost()){
            AddReelButton.addInfoButton(context, helperObject, mediaObject);
        }
    }


}