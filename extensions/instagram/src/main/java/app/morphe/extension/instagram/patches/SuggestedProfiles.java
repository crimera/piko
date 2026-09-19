/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches;

import java.util.Collections;
import java.util.List;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;

public final class SuggestedProfiles {
    private static final boolean DISABLED =
            SettingsStatus.disableDiscoverPeople && Pref.disableDiscoverPeople();

    private SuggestedProfiles() {}

    public static String filterSuggestedSection(String key) {
        if (DISABLED && ("suggested_users".equals(key)
                || "more_suggestions".equals(key)
                || "follow_back".equals(key)
                || "from_your_school".equals(key))) {
            // Both response parsers skip unrecognized keys without dropping follow requests.
            return Constants.PIKO;
        }
        return key;
    }

    public static boolean showSuggestedUsers(boolean original) {
        return original && !DISABLED;
    }

    public static boolean hideReelsSuggestedUsers(boolean suggested) {
        return DISABLED && suggested;
    }

    public static boolean hideActivityFeedSuggestions(String category) {
        return DISABLED && !"FOLLOW_REQUEST".equals(category);
    }

    public static <T> List<T> filterActivityFeedUsers(List<T> users) {
        return DISABLED ? Collections.emptyList() : users;
    }
}
