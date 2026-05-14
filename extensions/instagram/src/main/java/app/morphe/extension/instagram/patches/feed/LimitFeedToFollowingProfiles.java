/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.instagram.patches.feed;

import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;

@SuppressWarnings("unused")
public final class LimitFeedToFollowingProfiles {
    private static final boolean LIMIT_FOLLOWING_FEED;

    static {
        LIMIT_FOLLOWING_FEED = Pref.limitFollowingFeed() && SettingsStatus.limitFollowingFeed;
    }

    /**
     * Injection point.
     */
    public static Map<String, String> setFollowingHeader(Map<String, String> requestHeaderMap) {
        if (!LIMIT_FOLLOWING_FEED) return requestHeaderMap;

        String paginationHeaderName = "pagination_source";

        // Patch the header only if it's trying to fetch the default feed
        String currentHeader = requestHeaderMap.get(paginationHeaderName);
        if (currentHeader != null && !currentHeader.equals("feed_recs")) {
            return requestHeaderMap;
        }

        // Create new map as original is unmodifiable.
        Map<String, String> patchedRequestHeaderMap = new HashMap<>(requestHeaderMap);
        patchedRequestHeaderMap.put(paginationHeaderName, "following");
        return patchedRequestHeaderMap;
    }
}
