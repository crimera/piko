package app.morphe.extension.instagram.patches.story;


import java.util.ArrayList;
import android.content.Context;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import com.instagram.common.session.UserSession;


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

    public boolean storyButtonAction(CharSequence buttonText, Context ctx, Object reelItemObject,UserSession userSession){
        if(buttonText.equals(Strings.VIEW_STORY_MENTIONS)){
            ViewStoryMentionsPatch.viewMentions(ctx, reelItemObject);
            return true;
        }
        return false;
    }
}