/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;

@SuppressWarnings("unused")
public class Pref {
    public static boolean pikoDebug() {
        return SharedPref.getBooleanPref(Settings.PIKO_DEBUG);
    }

    public static boolean firstTimePiko() {
        return SharedPref.getBooleanPref(Settings.FIRST_TIME_PIKO);
    }
    public static boolean setFirstTimePiko(boolean bool) {
        return SharedPref.setBooleanPref(Settings.FIRST_TIME_PIKO.key,bool);
    }

    public static boolean unlockPlusBenefits() {
        return SharedPref.getBooleanPref(Settings.UNLOCK_PLUS_BENEFITS);
    }

    public static boolean disableAds() {
        return SharedPref.getBooleanPref(Settings.DISABLE_ADS);
    }

    public static boolean hideSuggestedContent() {
        return SharedPref.getBooleanPref(Settings.HIDE_SUGGESTED_CONTENT);
    }

    public static boolean openLinksExternally() {
        return SharedPref.getBooleanPref(Settings.OPEN_LINKS_EXTERNALLY);
    }

    public static boolean sanitizeShareLinks() {
        return SharedPref.getBooleanPref(Settings.SANITIZE_SHARE_LINKS);
    }

    public static boolean viewStoriesAnonymously() {
        return SharedPref.getBooleanPref(Settings.VIEW_STORIES_ANONYMOUSLY);
    }

    public static boolean viewLiveAnonymously() {
        return SharedPref.getBooleanPref(Settings.VIEW_LIVE_ANONYMOUSLY);
    }

    public static boolean disableScreenshotDetection() {
        return SharedPref.getBooleanPref(Settings.DISABLE_SCREENSHOT_DETECTION);
    }

    public static boolean disableTypingStatus() {
        return SharedPref.getBooleanPref(Settings.DISABLE_TYPING_STATUS);
    }

    public static boolean viewDmAnonymously() {
        return SharedPref.getBooleanPref(Settings.VIEW_DM_ANONYMOUSLY);
    }

    public static boolean disableVideoAutoplay() {
        return SharedPref.getBooleanPref(Settings.DISABLE_VIDEO_AUTOPLAY);
    }

    public static boolean storiesAudioAutoplay() {
        return SharedPref.getBooleanPref(Settings.STORIES_AUDIO_AUTOPLAY);
    }

    public static boolean disableStories() {
        return SharedPref.getBooleanPref(Settings.DISABLE_STORIES);
    }

    public static boolean disableHighlights() {
        return SharedPref.getBooleanPref(Settings.DISABLE_HIGHLIGHTS);
    }

    public static boolean disableExplore() {
        return SharedPref.getBooleanPref(Settings.DISABLE_EXPLORE);
    }

    public static boolean disableComments() {
        return SharedPref.getBooleanPref(Settings.DISABLE_COMMENTS);
    }

    public static boolean limitFollowingFeed() {
        return SharedPref.getBooleanPref(Settings.LIMIT_FOLLOWING_FEED);
    }

    public static boolean hideStoriesTray() {
        return SharedPref.getBooleanPref(Settings.HIDE_STORIES_TRAY) && SettingsStatus.hideStoriesTray;
    }

    public static boolean hideNotesTray() {
        return SharedPref.getBooleanPref(Settings.HIDE_NOTES_TRAY) && SettingsStatus.hideNotesTray;
    }

    public static boolean disableReelsScrolling() {
        return SharedPref.getBooleanPref(Settings.DISABLE_REELS_SCROLLING) && SettingsStatus.disableReelsScrolling;
    }

    public static boolean makeEphemeralMediaPermanent() {
        return SharedPref.getBooleanPref(Settings.UNLIMITED_REPLAYS) && SettingsStatus.unlimitedReplaysOnEphemeralMedia;
    }

    public static boolean hideReshareButton() {
        return SharedPref.getBooleanPref(Settings.HIDE_RESHARE_BUTTON) && SettingsStatus.hideReshareButton;
    }

    public static boolean hideGroupCreationOnSharesheet() {
        return SharedPref.getBooleanPref(Settings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET);
    }

    public static boolean enableDevOptions() {
        return SharedPref.getBooleanPref(Settings.DEVELOPER_OPTIONS);
    }
    public static boolean directlyOpenMetaConfig() {
        return SharedPref.getBooleanPref(Settings.DIRECTLY_OPEN_METACONFIG);
    }
    public static boolean enableEmployeeOptions() {
        return SharedPref.getBooleanPref(Settings.ENABLE_EMP_OPTIONS);
    }
    public static boolean allowUserNetworkCertificate() {
        return SharedPref.getBooleanPref(Settings.ALLOW_USER_NETWORK_CERTIFICATE);
    }

    public static int buildAge(int appAge) {
        return SharedPref.getBooleanPref(Settings.REMOVE_BUILD_EXPIRE_POPUP) ? 1 : appAge;
    }

    public static boolean disableAnalytics() {
        return SharedPref.getBooleanPref(Settings.DISABLE_ANALYTICS);
    }

    public static boolean disableDiscoverPeople() {
        return SharedPref.getBooleanPref(Settings.DISABLE_DISCOVER_PEOPLE);
    }

    public static boolean followBackIndicator() {
        return SharedPref.getBooleanPref(Settings.FOLLOW_BACK_INDICATOR);
    }

    public static boolean disableStoryFlipping() {
        return SharedPref.getBooleanPref(Settings.DISABLE_STORY_FLIPPING);
    }

    public static boolean viewStoryMentions() {
        return SharedPref.getBooleanPref(Settings.VIEW_STORY_MENTIONS);
    }

    public static String customiseStoryTimestamp() {
        return SharedPref.getStringPref(Settings.CUSTOMISE_STORY_TIMESTAMP);
    }

    public static int improveImageViewing(int defaultSize) {
        return SharedPref.getBooleanPref(Settings.IMPROVE_IMAGE_VIEWING) ? 2048 : defaultSize;
    }

    public static Integer improveImageViewing(Integer defaultSize) {
        return SharedPref.getBooleanPref(Settings.IMPROVE_IMAGE_VIEWING) ? 2048 : defaultSize;
    }

    public static boolean enableDownload() {
        return SharedPref.getBooleanPref(Settings.ENABLE_DOWNLOAD);
    }

    public static boolean enableDirectDownload() {
        return SharedPref.getBooleanPref(Settings.ENABLE_DIRECT_DOWNLOAD);
    }

    public static boolean downloadUsernameFolder() {
        return SharedPref.getBooleanPref(Settings.DOWNLOAD_USERNAME_FOLDER);
    }

    public static boolean downloadPicturesFolder() {
        return SharedPref.getBooleanPref(Settings.DOWNLOAD_PICTURES_FOLDER);
    }

    public static boolean hideNavigationFeed() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_FEED);
    }

    public static boolean hideNavigationReels() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_REELS);
    }

    public static boolean hideNavigationDirect() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_DIRECT);
    }

    public static boolean hideNavigationSearch() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_SEARCH);
    }

    public static boolean hideNavigationCreate() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_CREATE);
    }

    public static boolean removeEmptyBottomSpace() {
        return SharedPref.getBooleanPref(Settings.REMOVE_EMPTY_BOTTOM_SPACE);
    }

    public static boolean commentCopyButton() {
        return SharedPref.getBooleanPref(Settings.COMMENT_COPY_BUTTON) && SettingsStatus.copyCommentButton;
    }

    public static String changeLikeAnimation() {
        return SharedPref.getStringPref(Settings.CHANGE_LIKE_ANIMATION);
    }

    public static float customiseStoryRingSize() {
        try {
            return Float.valueOf(SharedPref.getStringPref(Settings.CUSTOMISE_STORY_RING_SIZE));
        } catch (Exception ex) {
            return 100.0f;
        }
    }

    public static boolean disableDoubleTapPost() {
        return SharedPref.getBooleanPref(Settings.DISABLE_DOUBLE_TAP_LIKE_POST);
    }
    public static boolean disableDoubleTapReel() {
        return SharedPref.getBooleanPref(Settings.DISABLE_DOUBLE_TAP_LIKE_REEL);
    }
    public static boolean disableDoubleTapComment() {
        return SharedPref.getBooleanPref(Settings.DISABLE_DOUBLE_TAP_LIKE_COMMENT);
    }
    public static boolean disableDoubleTapMessage() {
        return SharedPref.getBooleanPref(Settings.DISABLE_DOUBLE_TAP_LIKE_MESSAGE);
    }
    public static boolean moreOptionsOnPost() {
        return SharedPref.getBooleanPref(Settings.ENABLE_MORE_OPTIONS_ON_POST);
    }

    //end
}
