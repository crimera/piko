/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;


import com.twitter.api.model.json.core.JsonApiTweet;
import com.twitter.model.json.core.JsonTweetQuickPromoteEligibility;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
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
    private static Field commNotesField, promoteBtnField, translateField;

    /** Per post: the three fields are looked up once (by name), not on every post. */
    public static JsonApiTweet checkEntry(JsonApiTweet jsonApiTweet) {
        if (!hideCommNotes && !hidePromoteBtn && !forceTranslate) return jsonApiTweet;
        try {
            Class jsonApiTweetCls = jsonApiTweet.getClass();
            if (commNotesField == null || promoteBtnField == null || translateField == null) {

                if( commNotesFieldName.length()==0 || promoteBtnFieldName.length()==0 || translateFieldName.length()==0 ){
                    loader(jsonApiTweetCls);
                }
                commNotesField = jsonApiTweetCls.getDeclaredField(commNotesFieldName);
                promoteBtnField = jsonApiTweetCls.getDeclaredField(promoteBtnFieldName);
                translateField = jsonApiTweetCls.getDeclaredField(translateFieldName);
                commNotesField.setAccessible(true);
                promoteBtnField.setAccessible(true);
                translateField.setAccessible(true);
            }
            // Each on its own: the community-notes field is a boolean, and setting it to null threw, which skipped
            // the steps after it (force translation never applied while notes were hidden: upstream #1796).
            if(hideCommNotes){
                try {
                    if (commNotesField.getType() == boolean.class) commNotesField.setBoolean(jsonApiTweet, false);
                    else commNotesField.set(jsonApiTweet, null);
                } catch (Exception ignored) {
                }
            }
            if(hidePromoteBtn){
                try {
                    promoteBtnField.set(jsonApiTweet,null);
                } catch (Exception ignored) {
                }
            }
            if(forceTranslate){
                try {
                    translateField.set(jsonApiTweet,true);
                } catch (Exception ignored) {
                }
            }

        } catch (Exception unused) {

        }
        return jsonApiTweet;
    }
}