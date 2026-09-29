/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;

/**
 * Everything Focus Lock can hold on, in the order it is shown.
 *
 * One list drives both the picker and the enforcement, so a setting can never be offered
 * without being enforced, or enforced without being offered. Entries whose patch was not
 * applied are left out, since locking them would do nothing.
 */
public final class FocusLockTargets {

    /** The Reels tab is a navigation tab rather than a switch, so it gets a key of its own. */
    public static final String REELS_TAB_KEY = "navigation_reels_tab";

    public static final class Target {
        public final String key;
        public final String titleKey;
        public final String summaryKey;

        Target(String key, String titleKey, String summaryKey) {
            this.key = key;
            this.titleKey = titleKey;
            this.summaryKey = summaryKey;
        }
    }

    private FocusLockTargets() {
    }

    private static void add(List<Target> out, boolean available, String key, String titleKey, String summaryKey) {
        if (available) out.add(new Target(key, titleKey, summaryKey));
    }

    private static void add(List<Target> out, boolean available, BooleanSetting setting, String titleKey, String summaryKey) {
        add(out, available, setting.key, titleKey, summaryKey);
    }

    public static List<Target> available() {
        List<Target> targets = new ArrayList<>();

        add(targets, SettingsStatus.hideNavigationButtons,
                REELS_TAB_KEY, "piko_focus_lock_reels_tab", "piko_focus_lock_reels_tab_desc");
        add(targets, SettingsStatus.disableReelsScrolling,
                Settings.DISABLE_REELS_SCROLLING, "piko_disable_reels_scrolling", null);
        add(targets, SettingsStatus.hideReelsFollowButton,
                Settings.HIDE_REELS_FOLLOW_BUTTON, "piko_hide_reels_follow_button", null);
        add(targets, SettingsStatus.disableExplore,
                Settings.DISABLE_EXPLORE, "piko_disable_explore", null);
        add(targets, SettingsStatus.disableStories,
                Settings.DISABLE_STORIES, "piko_disable_stories", null);
        add(targets, SettingsStatus.hideStoriesTray,
                Settings.HIDE_STORIES_TRAY, "piko_hide_stories_tray", null);
        add(targets, SettingsStatus.disableHighlights,
                Settings.DISABLE_HIGHLIGHTS, "piko_disable_highlights", null);
        add(targets, SettingsStatus.hideNotesTray,
                Settings.HIDE_NOTES_TRAY, "piko_hide_notes_tray", null);
        add(targets, SettingsStatus.disableComments,
                Settings.DISABLE_COMMENTS, "piko_disable_comments", null);
        add(targets, SettingsStatus.limitFollowingFeed,
                Settings.LIMIT_FOLLOWING_FEED, "piko_limit_following_feed", null);
        add(targets, SettingsStatus.disableSwipeToCreate,
                Settings.DISABLE_SWIPE_TO_CREATE, "piko_disable_swipe_to_create", null);
        add(targets, SettingsStatus.hideGroupCreationOnSharesheet,
                Settings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET,
                "piko_hide_group_creation_button_on_sharesheet", null);
        add(targets, SettingsStatus.disableDoubleTapLike,
                Settings.DISABLE_DOUBLE_TAP_LIKE_POST, "piko_disable_double_tap_like_post", null);
        add(targets, SettingsStatus.disableDoubleTapLike,
                Settings.DISABLE_DOUBLE_TAP_LIKE_REEL, "piko_disable_double_tap_like_reel", null);
        add(targets, SettingsStatus.disableDoubleTapLike,
                Settings.DISABLE_DOUBLE_TAP_LIKE_COMMENT, "piko_disable_double_tap_like_comment", null);
        add(targets, SettingsStatus.disableDoubleTapLike,
                Settings.DISABLE_DOUBLE_TAP_LIKE_MESSAGE, "piko_disable_double_tap_like_message", null);

        return targets;
    }
}
