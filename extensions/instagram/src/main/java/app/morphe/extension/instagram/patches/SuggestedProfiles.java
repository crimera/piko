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
    // Unrelated API requests can reach the path check before settings initialization.
    private static final class Preference {
        static final boolean DISABLED = SettingsStatus.disableDiscoverPeople && Pref.disableDiscoverPeople();
    }

    private SuggestedProfiles() {}

    public static boolean hideAdditionalAccounts(String path) {
        return ("discover/ayml/".equals(path) || "discover/chaining/".equals(path)) && Preference.DISABLED;
    }

    public static String filterSuggestedSection(String key) {
        if (Preference.DISABLED && ("suggested_users".equals(key)
                || "more_suggestions".equals(key)
                || "follow_back".equals(key)
                || "from_your_school".equals(key))) {
            // Both response parsers skip unrecognized keys without dropping follow requests.
            return Constants.PIKO;
        }
        return key;
    }

    public static boolean showSuggestedUsers(boolean original) {
        return original && !Preference.DISABLED;
    }

    public static boolean hideReelsSuggestedUsers(boolean suggested) {
        return Preference.DISABLED && suggested;
    }

    public static boolean hideReelsSuggestedUsers(Enum<?> itemType) {
        if (!Preference.DISABLED || itemType == null) return false;
        String type = itemType.name();
        return "SUGGESTED_USERS".equals(type) || "NETEGO_SUGGESTED_USERS".equals(type);
    }

    public static boolean hideActivityFeedSuggestions(String category) {
        return Preference.DISABLED && !"FOLLOW_REQUEST".equals(category);
    }

    public static <T> List<T> filterActivityFeedUsers(List<T> users) {
        return Preference.DISABLED ? Collections.emptyList() : users;
    }
}
