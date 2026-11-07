/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import com.instagram.common.session.UserSession;
import java.util.ArrayList;
import java.util.List;

final class StoryDownloadMedia {
    static List<Object> items(Object reel, UserSession session) {
        List<Object> result = new ArrayList<>();
        if (reel == null || session == null) return result;
        for (Object item : nativeItems(reel, session)) {
            if (item == null) continue;
            Object story = media(item);
            if (story != null) result.add(story);
        }
        return result;
    }

    // The patch supplies the viewer's ordered list and the nullable media of each item.
    private static List<?> nativeItems(Object reel, UserSession session) {
        return null;
    }

    private static Object media(Object item) {
        return null;
    }
}
