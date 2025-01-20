package app.revanced.integrations.twitter.patches;


import com.twitter.api.model.json.core.JsonApiTweet;
import com.twitter.model.json.core.JsonTweetQuickPromoteEligibility;
import app.revanced.integrations.twitter.Pref;
import app.revanced.integrations.twitter.settings.SettingsStatus;
import java.lang.reflect.Field;

public class TweetInfo {
    private static final boolean hideCommNotes,hidePromoteBtn,forceTranslate;
    private static String commNotesFieldName, promoteBtnFieldName, translateFieldName;

    static {
        hideCommNotes = (Pref.hideCommNotes() && SettingsStatus.hideCommunityNote);
        hidePromoteBtn = (Pref.hidePromoteBtn() && SettingsStatus.hidePromoteButton);
        forceTranslate = (Pref.enableForceTranslate() && SettingsStatus.forceTranslate);
        commNotesFieldName = "";
        promoteBtnFieldName = "";
        translateFieldName = "";

    }

    private static void loader(Class jsonApiTweetCls){
        commNotesFieldName = "";
        promoteBtnFieldName = "";
        translateFieldName = "";

        Field[] fields = jsonApiTweetCls.getDeclaredFields();
        for(Field field : fields){
            if (field.getType() == boolean.class) {
                if(commNotesFieldName.length()==0){
                    commNotesFieldName = field.getName();
                    continue;
                }
                translateFieldName = field.getName();
                continue;
            }
            if (field.getType() == JsonTweetQuickPromoteEligibility.class) {
                promoteBtnFieldName = field.getName();
            }
        }
    }
    public static JsonApiTweet checkEntry(JsonApiTweet jsonApiTweet) {
        try {
            Class jsonApiTweetCls = jsonApiTweet.getClass();
            if( commNotesFieldName.length()==0 || promoteBtnFieldName.length()==0 || translateFieldName.length()==0 ){
                loader(jsonApiTweetCls);
            }
            if(hideCommNotes){
                Field f = jsonApiTweetCls.getDeclaredField(commNotesFieldName);
                f.set(jsonApiTweet,null);
            }
            if(hidePromoteBtn){
                Field f = jsonApiTweetCls.getDeclaredField(promoteBtnFieldName);
                f.set(jsonApiTweet,null);
            }
            if(forceTranslate){
                Field f = jsonApiTweetCls.getDeclaredField(translateFieldName);
                f.set(jsonApiTweet,true);
            }

        } catch (Exception unused) {

        }
        return jsonApiTweet;
    }
}