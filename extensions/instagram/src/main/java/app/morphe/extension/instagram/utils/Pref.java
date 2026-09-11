/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.utils;

import java.util.Set;
import java.util.HashSet;
import android.content.Context;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.settings.StringSetting;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.constants.Constants;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.shared.MarkChatAsReadScope;

@SuppressWarnings("unused")
public class Pref {
    private static final int MAX_IMAGE_SIZE = 4096;

    private static String removeLineBreaks(String value) {
        return value.replace("\r", "").replace("\n", "");
    }

    public static boolean clearAllPreferences() {
        return SharedPref.clearAll();
    }
    
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

    public static boolean saveDeletedMessages() {
        return SharedPref.getBooleanPref(Settings.SAVE_DELETED_MESSAGES);
    }

    public static boolean openLinksExternally() {
        return SharedPref.getBooleanPref(Settings.OPEN_LINKS_EXTERNALLY);
    }

    public static boolean sanitizeShareLinks() {
        return SharedPref.getBooleanPref(Settings.SANITIZE_SHARE_LINKS);
    }

    public static String customSharingDomain() {
        return removeLineBreaks(SharedPref.getStringPref(Settings.CUSTOM_SHARING_DOMAIN));
    }

    public static boolean getTurnOnAllGhostModes() {
        return SharedPref.getBooleanPref(Settings.TURN_ON_ALL_GHOST_MODES);
    }

    public static boolean setTurnOnAllGhostModes(boolean bool) {
        return SharedPref.setBooleanPref(Settings.TURN_ON_ALL_GHOST_MODES.key,bool);
    }

    public static boolean isMoreOptionsOnProfilePatched(){
        return SettingsStatus.moreOptionsOnProfile;
    }

    public static boolean viewStoriesAnonymously() {
        return (SharedPref.getBooleanPref(Settings.VIEW_STORIES_ANONYMOUSLY) && SettingsStatus.viewStoriesAnonymously) || Pref.getTurnOnAllGhostModes();
    }

    public static boolean viewLiveAnonymously() {
        return (SharedPref.getBooleanPref(Settings.VIEW_LIVE_ANONYMOUSLY) && SettingsStatus.viewLiveAnonymously) || Pref.getTurnOnAllGhostModes();
    }

    public static boolean disableScreenshotDetection() {
        return SharedPref.getBooleanPref(Settings.DISABLE_SCREENSHOT_DETECTION) || Pref.getTurnOnAllGhostModes();
    }

    public static boolean disableTypingStatus() {
        return SharedPref.getBooleanPref(Settings.DISABLE_TYPING_STATUS) || Pref.getTurnOnAllGhostModes();
    }

    public static boolean enableMarkChatAsReadOption() {
        return SharedPref.getBooleanPref(Settings.ENABLE_MARK_CHAT_AS_READ) && SettingsStatus.markChatAsRead;
    }

    // Return false = call the message seen api.
    // Return true = blocks the message seen api.
    public static boolean viewDmAnonymously() {
        return shouldBlockDmSeen(
                SharedPref.getBooleanPref(Settings.VIEW_DM_ANONYMOUSLY),
                Pref.getTurnOnAllGhostModes(),
                enableMarkChatAsReadOption()
        );
    }

    static boolean shouldBlockDmSeen(
            boolean viewDmAnonymously,
            boolean allGhostModes,
            boolean manualReadOptionEnabled
    ) {
        if (manualReadOptionEnabled && MarkChatAsReadScope.isActive()) {
            return false;
        }
        return viewDmAnonymously || allGhostModes;
    }

    public static boolean disableVideoAutoplay() {
        return SharedPref.getBooleanPref(Settings.DISABLE_VIDEO_AUTOPLAY);
    }

    public
    static boolean disableStories() {
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

    public static boolean disableSwipeToCreate() {
        return SharedPref.getBooleanPref(Settings.DISABLE_SWIPE_TO_CREATE) && SettingsStatus.disableSwipeToCreate;
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
    public static boolean followBackColorIndicator() {
        return SharedPref.getBooleanPref(Settings.FOLLOW_BACK_COLOR_INDICATOR);
    }

    public static boolean disableStoryFlipping() {
        return SharedPref.getBooleanPref(Settings.DISABLE_STORY_FLIPPING);
    }

    public static boolean loopStory() {
        return SharedPref.getBooleanPref(Settings.LOOP_STORY);
    }

    public static boolean viewStoryMentions() {
        return SharedPref.getBooleanPref(Settings.VIEW_STORY_MENTIONS);
    }

    public static String customiseStoryTimestamp() {
        return SharedPref.getStringPref(Settings.CUSTOMISE_STORY_TIMESTAMP);
    }

    public static int improveImageViewing(int defaultSize) {
        return SharedPref.getBooleanPref(Settings.IMPROVE_IMAGE_VIEWING) ? MAX_IMAGE_SIZE : defaultSize;
    }

    public static Integer improveImageViewing(Integer defaultSize) {
        return SharedPref.getBooleanPref(Settings.IMPROVE_IMAGE_VIEWING) ? MAX_IMAGE_SIZE : defaultSize;
    }

    public static boolean enableDownload() {
        return SharedPref.getBooleanPref(Settings.ENABLE_DOWNLOAD) && SettingsStatus.downloadMedia;
    }

    public static boolean enableDirectDownload() {
        return SharedPref.getBooleanPref(Settings.ENABLE_DIRECT_DOWNLOAD);
    }

    public static boolean downloadUsernameFolder() {
        return SharedPref.getBooleanPref(Settings.DOWNLOAD_USERNAME_FOLDER);
    }

    public static boolean embedDownloadMetadata() {
        return SharedPref.getBooleanPref(Settings.EMBED_DOWNLOAD_METADATA);
    }

    public static boolean hideNavigationFeed() {
        return SharedPref.getBooleanPref(Settings.HIDE_NAVIGATION_FEED);
    }

    public static boolean getHideHomeCreateButton() {
        return !mainFeedActionBarButtons().contains(Constants.AB_CREATE);
    }

    public static boolean getHideHomeNotificationsButton() {
        return !mainFeedActionBarButtons().contains(Constants.AB_NOTIFICATIONS);
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

    public static boolean hasLegacyNavigationSettings() {
        return SharedPref.hasKey(Settings.HIDE_NAVIGATION_FEED.key)
                || SharedPref.hasKey(Settings.HIDE_NAVIGATION_REELS.key)
                || SharedPref.hasKey(Settings.HIDE_NAVIGATION_DIRECT.key)
                || SharedPref.hasKey(Settings.HIDE_NAVIGATION_SEARCH.key)
                || SharedPref.hasKey(Settings.HIDE_NAVIGATION_CREATE.key);
    }

    public static String navigationTabs() {
        return SharedPref.getStringPref(Settings.NAVIGATION_TABS);
    }

    public static boolean setNavigationTabs(String value) {
        return SharedPref.setStringPref(Settings.NAVIGATION_TABS.key, value);
    }

    public static boolean removeEmptyBottomSpace() {
        return SharedPref.getBooleanPref(Settings.REMOVE_EMPTY_BOTTOM_SPACE);
    }

    public static boolean commentCopyButton() {
        return SharedPref.getBooleanPref(Settings.COMMENT_COPY_BUTTON) && SettingsStatus.copyCommentButton;
    }

    public static boolean commentSaveMediaButton() {
        return SharedPref.getBooleanPref(Settings.COMMENT_SAVE_MEDIA_BUTTON) && SettingsStatus.saveMediaCommentButton;
    }

    public static String changeLikeAnimation() {
        return SharedPref.getStringPref(Settings.CHANGE_LIKE_ANIMATION);
    }

    public static float customiseStoryRingSize() {
        try {
            return Float.parseFloat(SharedPref.getStringPref(Settings.CUSTOMISE_STORY_RING_SIZE));
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
        return SharedPref.getBooleanPref(Settings.ENABLE_MORE_OPTIONS_ON_POST) && SettingsStatus.moreOptionsOnPost;
    }
    public static boolean downloadWithExternalDownloader() {
        return SharedPref.getBooleanPref(Settings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER) && SettingsStatus.downloadWithExternalDownloader;
    }

    public static String externalDownloaderPackageName() {
        return removeLineBreaks(SharedPref.getStringPref(Settings.EXTERNAL_DOWNLOADER_PACKAGE_NAME));
    }

    public static Set<String> mainFeedActionBarButtons() {
        return loadAndMigrateActionBarButtons(Settings.ACTION_BAR_MAIN_FEED, true);
    }

    public static Set<String> userProfileActionBarButtons() {
        return loadAndMigrateActionBarButtons(Settings.ACTION_BAR_USER_PROFILE, false);
    }

    private static Set<String> loadAndMigrateActionBarButtons(StringSetting setting, boolean home) {
        Set<String> buttons = SharedPref.getSetPref(setting);
        Context context = Utils.getContext();
        var preferences = context == null ? null
                : context.getSharedPreferences(Constants.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String migratedKey = setting.key + "_visibility_migrated";
        if (preferences != null && preferences.getBoolean(migratedKey, false)) return buttons;

        String createKey = Settings.HIDE_HOME_CREATE_BUTTON.key;
        String notificationsKey = Settings.HIDE_HOME_NOTIFICATIONS_BUTTON.key;
        buttons = new HashSet<>(buttons);
        boolean hideCreate = buttons.remove("HIDE_CREATE");
        if (home && preferences != null) hideCreate |= preferences.getBoolean(createKey, false);
        if (!hideCreate) buttons.add(Constants.AB_CREATE);
        if (home) {
            boolean hideNotifications = buttons.remove("HIDE_NOTIFICATIONS");
            if (preferences != null) hideNotifications |= preferences.getBoolean(notificationsKey, false);
            if (!hideNotifications) buttons.add(Constants.AB_NOTIFICATIONS);
        }
        if (preferences != null) {
            var editor = preferences.edit().putStringSet(setting.key, buttons).putBoolean(migratedKey, true);
            if (home) editor.remove(createKey).remove(notificationsKey);
            editor.apply();
        }
        return buttons;
    }

    public static Set<String> chatActionBarButtons() {
        return SharedPref.getSetPref(Settings.ACTION_BAR_CHAT);
    }

    public static Set<String> inboxActionBarButtons() {
        return SharedPref.getSetPref(Settings.ACTION_BAR_INBOX);
    }

    public static Set<String> filterStoryByType() {
        return SharedPref.getSetPref(Settings.FILTER_STORY_BY_TYPE);
    }

    public static Set<String> filterStoryByUserType() {
        return SharedPref.getSetPref(Settings.FILTER_STORY_BY_USER_TYPE);
    }

    public static Integer filterStoryByMinStoryItems() {
        return Integer.valueOf(SharedPref.getStringPref(Settings.FILTER_STORY_MIN_STORY_ITEMS));
    }

    public static Integer filterStoryByMaxStoryItems() {
        return Integer.valueOf(SharedPref.getStringPref(Settings.FILTER_STORY_MAX_STORY_ITEMS));
    }

    //end
}
