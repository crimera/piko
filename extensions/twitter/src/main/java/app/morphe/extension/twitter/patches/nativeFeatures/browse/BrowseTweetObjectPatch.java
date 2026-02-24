package app.morphe.extension.twitter.patches.nativeFeatures.browse;

import android.content.Context;
import app.morphe.extension.twitter.Utils;
import app.crimera.utils.ObjectBrowser;

public class BrowseTweetObjectPatch {
    public static void browse(Context activity, Object tweetObj) {
        try {
            if (tweetObj == null) {
                Utils.toast("Tweet object is null");
                return;
            }
            ObjectBrowser.browseObject(activity, tweetObj);
        } catch (Exception ex) {
            Utils.logger(ex);
        }
    }
}
