/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.story;


import java.util.ArrayList;
import android.content.Context;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.entity.MediaData;

import com.instagram.common.session.UserSession;

public class StoryButton {
    private static boolean VIEW_STORY_MENTIONS;
    private static boolean ENABLE_DOWNLOAD;
    private static boolean ENABLE_DIRECT_DOWNLOAD;
    private static boolean DEBUG;

    static{
        VIEW_STORY_MENTIONS = Pref.viewStoryMentions() && SettingsStatus.viewStoryMentions;
        ENABLE_DOWNLOAD = Pref.enableDownload() && SettingsStatus.downloadMedia;
        ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
        DEBUG = Pref.pikoDebug();
    }

    public static ArrayList addButtons(ArrayList buttonList){
        if(DEBUG){
            buttonList.add(Strings.PIKO_DEBUG);
        }
        if(VIEW_STORY_MENTIONS){
            buttonList.add(Strings.VIEW_STORY_MENTIONS);
        }
        if(ENABLE_DOWNLOAD){
            if(ENABLE_DIRECT_DOWNLOAD){
                buttonList.add(Strings.CATEGORY_DOWNLOAD_MEDIA);
            }else{
                buttonList.add(Strings.DOWNLOAD_OPTIONS);
            }
        }
        if(Pref.downloadWithExternalDownloader()){
            buttonList.add(Strings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER);
        }

        return buttonList;
    }

    public static boolean storyButtonAction(CharSequence buttonText, Context ctx, Object mediaObject){
        try {
            if (buttonText.equals(Strings.VIEW_STORY_MENTIONS)) {
                ViewStoryMentionsPatch.viewMentions(ctx, mediaObject);
                return true;
            } else if (buttonText.equals(Strings.DOWNLOAD_OPTIONS) || buttonText.equals(Strings.CATEGORY_DOWNLOAD_MEDIA)) {
                DownloadUtils.downloadPost(ctx,null,mediaObject,0);
                return true;
            } else if (buttonText.equals(Strings.PIKO_DEBUG)) {
                ObjectBrowser.browseObject(ctx, new MediaData(mediaObject));
                return true;
            } else if (buttonText.equals(Strings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER)) {
                DownloadUtils.externalDownloader(mediaObject,0);

                return true;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed storyButtonAction", ex);
        }
        return false;
    }
}