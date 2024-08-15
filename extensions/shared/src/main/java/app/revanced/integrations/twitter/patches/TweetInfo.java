package app.revanced.integrations.twitter.patches;


import com.twitter.api.model.json.core.JsonApiTweet;
import app.revanced.integrations.twitter.Pref;
import app.revanced.integrations.twitter.settings.SettingsStatus;
import java.lang.reflect.Field;

public class TweetInfo {
    private static final boolean hideCommNotes,hidePromoteBtn,forceTranslate;
    static {
        hideCommNotes = (Pref.hideCommNotes() && SettingsStatus.hideCommunityNote);;
        hidePromoteBtn = (Pref.hidePromoteBtn() && SettingsStatus.hidePromoteButton);;
        forceTranslate = (Pref.enableForceTranslate() && SettingsStatus.forceTranslate);;
    }

    public static JsonApiTweet checkEntry(JsonApiTweet jsonApiTweet) {
        try {
            Class jsonApiTweetCls = jsonApiTweet.getClass();
            if(hideCommNotes){
                Field f = jsonApiTweetCls.getDeclaredField("e0");
                f.set(jsonApiTweet,null);
            }
            if(hidePromoteBtn){
                Field f = jsonApiTweetCls.getDeclaredField("q0");
                f.set(jsonApiTweet,null);
            }
            if(forceTranslate){
                jsonApiTweet.r0 = true;
            }

        } catch (Exception unused) {

        }
        return jsonApiTweet;
    }
}