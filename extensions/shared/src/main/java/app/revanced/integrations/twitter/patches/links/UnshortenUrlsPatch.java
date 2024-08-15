package app.revanced.integrations.twitter.patches.links;


import android.util.Log;

import com.twitter.model.json.core.JsonUrlEntity;
import com.twitter.model.json.card.JsonCardInstanceData;
import app.revanced.integrations.twitter.Pref;
import app.revanced.integrations.twitter.settings.SettingsStatus;


public class UnshortenUrlsPatch {
    private static String TAG = "030-unshort";
    private static boolean unShortUrl;
    static {
        unShortUrl = SettingsStatus.unshortenlink && Pref.unShortUrl();
    }
    public static JsonUrlEntity unshort(JsonUrlEntity entity) {
        try {
            if(unShortUrl){
                entity.e = entity.c;
            }
        } catch (Exception ex) {
            Log.e(TAG, ";-;", ex);
        }
        return entity;
    }
}
