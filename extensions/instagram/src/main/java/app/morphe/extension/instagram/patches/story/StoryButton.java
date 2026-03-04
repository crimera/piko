package app.morphe.extension.instagram.patches.story;


import java.util.ArrayList;
import android.content.Context;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import com.instagram.common.session.UserSession;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class StoryButton {
    private static boolean VIEW_STORY_MENTIONS;
    static{
        VIEW_STORY_MENTIONS = Pref.viewStoryMentions() && SettingsStatus.viewStoryMentions;
    }

    public static ArrayList addButtons(ArrayList buttonList){
        if(VIEW_STORY_MENTIONS){
            buttonList.add(Strings.VIEW_STORY_MENTIONS);
        }
        return buttonList;
    }

    public boolean storyButtonAction(CharSequence buttonText, Context ctx, Object mediaObject,UserSession userSession){
        try {
            if (buttonText.equals(Strings.VIEW_STORY_MENTIONS)) {
                ViewStoryMentionsPatch.viewMentions(ctx, mediaObject);
                return true;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed storyButtonAction", ex);
        }
        return false;
    }
}