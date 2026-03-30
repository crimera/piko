/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.browse;

import android.content.Context;
import app.morphe.extension.crimera.ObjectBrowser;

public class BrowseTweetObjectPatch {
    public static void browse(Context activity, Object tweetObj) {
        ObjectBrowser.browseObject(activity, tweetObj);
    }
}
