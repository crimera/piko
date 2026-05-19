/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.browse;

import android.content.Context;
import app.morphe.extension.crimera.ObjectBrowser;

public class BrowseTweetObjectPatch {
    public static void browse(Context activity, Object tweetObj) {
        ObjectBrowser.browseObject(activity, tweetObj);
    }
}
