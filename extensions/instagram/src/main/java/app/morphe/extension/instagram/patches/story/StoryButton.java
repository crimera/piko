/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
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

public class StoryButton {
    private static boolean VIEW_STORY_MENTIONS,ENABLE_DOWNLOAD,ENABLE_DIRECT_DOWNLOAD;
    static{
        VIEW_STORY_MENTIONS = Pref.viewStoryMentions() && SettingsStatus.viewStoryMentions;
        ENABLE_DOWNLOAD = Pref.enableDownload() && SettingsStatus.downloadMedia;
        ENABLE_DIRECT_DOWNLOAD = Pref.enableDirectDownload() && SettingsStatus.downloadMedia;
    }

    public static ArrayList addButtons(ArrayList buttonList){
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

        return buttonList;
    }

    public boolean storyButtonAction(CharSequence buttonText, Context ctx, Object mediaObject){
        try {
            if (buttonText.equals(Strings.VIEW_STORY_MENTIONS)) {
                ViewStoryMentionsPatch.viewMentions(ctx, mediaObject);
                return true;
            } else if (buttonText.equals(Strings.DOWNLOAD_OPTIONS) || buttonText.equals(Strings.CATEGORY_DOWNLOAD_MEDIA)) {
                DownloadUtils.downloadPost(ctx,mediaObject,0);
                return true;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed storyButtonAction", ex);
        }
        return false;
    }
}