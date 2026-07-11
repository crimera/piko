/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.ArrayList;
import android.content.Context;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.patches.feed.MoreOptionsOnPostPatch;
import app.morphe.extension.crimera.ObjectBrowser;

import com.instagram.common.session.UserSession;

public class StoryButton {
    private static boolean VIEW_STORY_MENTIONS;
    private static boolean MORE_OPTIONS_ON_POST;
    private static boolean DEBUG;

    static{
        VIEW_STORY_MENTIONS = Pref.viewStoryMentions() && SettingsStatus.viewStoryMentions;
        MORE_OPTIONS_ON_POST = Pref.moreOptionsOnPost() && SettingsStatus.moreOptionsOnPost;
        DEBUG = Pref.pikoDebug();
    }

    public static ArrayList addButtons(ArrayList buttonList){
        if(DEBUG){
            buttonList.add(str("piko_debug"));
        }
        if(VIEW_STORY_MENTIONS){
            buttonList.add(str("piko_view_story_mentions"));
        }
        if(MORE_OPTIONS_ON_POST){
            buttonList.add(str("piko_more_options"));
        }

        return buttonList;
    }

    public static boolean storyButtonAction(CharSequence buttonText, Context ctx, Object mediaObject){
        try {
            if (buttonText.equals(str("piko_view_story_mentions"))) {
                ViewStoryMentionsPatch.viewMentions(ctx, mediaObject);
                return true;
            } else if (buttonText.equals(str("piko_more_options"))) {
                MoreOptionsOnPostPatch.postMoreOptions(ctx, null, mediaObject, 0);
                return true;
            } else if (buttonText.equals(str("piko_debug"))) {
                ObjectBrowser.browseObject(ctx, new MediaData(mediaObject));
                return true;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed storyButtonAction", ex);
        }
        return false;
    }
}