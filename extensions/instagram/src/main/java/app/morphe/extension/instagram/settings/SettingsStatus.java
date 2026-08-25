/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.instagram.settings;

import java.util.TreeMap;
import static app.morphe.extension.instagram.utils.IgStr.str;
import static app.morphe.extension.instagram.settings.Settings.*;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.instagram.utils.Pref;

public class SettingsStatus {
    public static TreeMap<String,Boolean> FLAGS = new TreeMap();

    // Developer section.
    public static boolean enableDeveloperOptions = false;
    public static void enableDeveloperOptions() {
        enableDeveloperOptions = true;
    }
    public static boolean removeBuildExpirePopup = false;
    public static void removeBuildExpirePopup() {
        removeBuildExpirePopup = true;
    }
    public static boolean unlockEmployeeOptions = false;
    public static void unlockEmployeeOptions() {
        unlockEmployeeOptions = true;
    }
    public static boolean allowUserNetworkCertificate = false;
    public static void allowUserNetworkCertificate() {
        allowUserNetworkCertificate = true;
    }
    public static boolean recommendedFlags = false;
    public static void recommendedFlags() {recommendedFlags = true;}
    public static boolean developerOptionsSection() {
        return (allowUserNetworkCertificate || unlockEmployeeOptions || enableDeveloperOptions || removeBuildExpirePopup || recommendedFlags);
    }

    //Ads section.
    public static boolean disableAds = false;
    public static void disableAds() {
        disableAds = true;
    }

    public static boolean hideSuggestedContent = false;
    public static void hideSuggestedContent() {
        hideSuggestedContent = true;
    }
    public static boolean adsSection() {
        return (disableAds || hideSuggestedContent);
    }

    //Links section.
    public static boolean openLinksExternally = false;
    public static void openLinksExternally() {
        openLinksExternally = true;
    }
    public static boolean sanitizeShareLinks = false;
    public static void sanitizeShareLinks() {sanitizeShareLinks = true;}
    public static boolean customSharingDomain = false;
    public static void customSharingDomain() {customSharingDomain = true;}
    public static boolean linksSection() {
        return (openLinksExternally || sanitizeShareLinks || customSharingDomain);
    }


    public static boolean viewStoriesAnonymously = false;
    public static void viewStoriesAnonymously() {
        viewStoriesAnonymously = true;
    }
    public static boolean viewLiveAnonymously = false;
    public static void viewLiveAnonymously() {
        viewLiveAnonymously = true;
    }
    public static boolean disableScreenshotDetection = false;
    public static void disableScreenshotDetection() {
        disableScreenshotDetection = true;
    }
    public static boolean disableTypingStatus = false;
    public static void disableTypingStatus() {
        disableTypingStatus = true;
    }
    public static boolean viewDmAnonymously = false;
    public static void viewDmAnonymously() {
        viewDmAnonymously = true;
    }
    public static boolean saveDeletedMessages = false;
    public static void saveDeletedMessages() {
        saveDeletedMessages = true;
    }
    public static boolean ghostSection() {
        return (viewStoriesAnonymously || viewLiveAnonymously || disableScreenshotDetection || disableTypingStatus || viewDmAnonymously);
    }

    public static boolean disableStories = false;
    public static void disableStories() {
        disableStories = true;
    }
    public static boolean disableHighlights = false;
    public static void disableHighlights() {
        disableHighlights = true;
    }
    public static boolean disableExplore = false;
    public static void disableExplore() {
        disableExplore = true;
    }
    public static boolean disableComments = false;
    public static void disableComments() {
        disableComments = true;
    }
    public static boolean hideStoriesTray = false;
    public static void hideStoriesTray() {
        hideStoriesTray = true;
    }
    public static boolean hideNotesTray = false;
    public static void hideNotesTray() {
        hideNotesTray = true;
    }
    public static boolean limitFollowingFeed = false;
    public static void limitFollowingFeed() {
        limitFollowingFeed = true;
    }
    public static boolean hideGroupCreationOnSharesheet = false;
    public static void hideGroupCreationOnSharesheet() {
        hideGroupCreationOnSharesheet = true;
    }
    public static boolean disableReelsScrolling = false;
    public static void disableReelsScrolling() {
        disableReelsScrolling = true;
    }
    public static boolean disableSwipeToCreate = false;
    public static void disableSwipeToCreate() {
        disableSwipeToCreate = true;
    }
    public static boolean disableDoubleTapLike = false;
    public static void disableDoubleTapLike() {
        disableDoubleTapLike = true;
    }
    public static boolean distractionFreeSection() {
        return (disableDoubleTapLike || hideNotesTray || disableHighlights || disableStories || disableExplore || disableComments || hideStoriesTray || limitFollowingFeed || hideGroupCreationOnSharesheet || disableReelsScrolling || disableSwipeToCreate);
    }

    //Misc section.
    public static boolean unlockPlusBenefits = false;
    public static void unlockPlusBenefits() { unlockPlusBenefits = true; }
    public static boolean changeLikeAnimation = false;
    public static void changeLikeAnimation() { changeLikeAnimation = true; }
    public static boolean customiseStoryRingSize = false;
    public static void customiseStoryRingSize() { customiseStoryRingSize = true; }
    public static boolean disableAnalytics = false;
    public static void disableAnalytics() { disableAnalytics = true; }
    public static boolean disableDiscoverPeople = false;
    public static void disableDiscoverPeople() {
        disableDiscoverPeople = true;
    }
    public static boolean followBackIndicator = false;
    public static void followBackIndicator() { followBackIndicator = true; }
    public static boolean viewStoryMentions = false;
    public static void viewStoryMentions() {
        viewStoryMentions = true;
    }
    public static boolean disableStoryFlipping = false;
    public static void disableStoryFlipping() {
        disableStoryFlipping = true;
    }
    public static boolean loopStory = false;
    public static void loopStory() {
        loopStory = true;
    }
    public static boolean customiseStoryTimestamp = false;
    public static void customiseStoryTimestamp() {
        customiseStoryTimestamp = true;
    }
    public static boolean improveImageViewing = false;
    public static void improveImageViewing() {
        improveImageViewing = true;
    }
    public static boolean hideReshareButton = false;
    public static void hideReshareButton() {
        hideReshareButton = true;
    }
    public static boolean copyCommentButton = false;
    public static void copyCommentButton() {
        copyCommentButton = true;
    }
    public static boolean saveMediaCommentButton = false;
    public static void saveMediaCommentButton() {
        saveMediaCommentButton = true;
    }
    public static boolean removeEmptyBottomSpace = false;
    public static void removeEmptyBottomSpace() {
        removeEmptyBottomSpace = true;
    }
    public static boolean disableVideoAutoplay = false;
    public static void disableVideoAutoplay() { disableVideoAutoplay = true; }
    public static boolean moreOptionsOnPost = false;
    public static void moreOptionsOnPost() { moreOptionsOnPost = true; }
    public static boolean moreOptionsOnProfile = false;
    public static void moreOptionsOnProfile() { moreOptionsOnProfile = true; }
    public static boolean miscSection() {return ( saveMediaCommentButton || moreOptionsOnProfile || moreOptionsOnPost || customiseStoryRingSize || changeLikeAnimation || unlockPlusBenefits || disableVideoAutoplay || removeEmptyBottomSpace || copyCommentButton || improveImageViewing || customiseStoryTimestamp || disableAnalytics || disableDiscoverPeople || followBackIndicator || viewStoryMentions || disableStoryFlipping || loopStory || hideReshareButton);}

    //DM section
    public static boolean unlimitedReplaysOnEphemeralMedia = false;
    public static void unlimitedReplaysOnEphemeralMedia() {unlimitedReplaysOnEphemeralMedia = true;}
    public static boolean markChatAsRead = false;
    public static void markChatAsRead() { markChatAsRead = true; }
    public static boolean dmSection(){ return markChatAsRead || unlimitedReplaysOnEphemeralMedia || saveDeletedMessages ;}

    //Download section.
    public static boolean downloadMedia = false;
    public static void downloadMedia() {downloadMedia = true;}
    public static boolean downloadVoiceMessage = false;
    public static void downloadVoiceMessage() { downloadVoiceMessage = true; }
    public static boolean downloadWithExternalDownloader = false;
    public static void downloadWithExternalDownloader() { downloadWithExternalDownloader = true; }
    public static boolean downloadSection(){return (downloadMedia || downloadWithExternalDownloader);}

    public static boolean hideNavigationButtons = false;
    public static void hideNavigationButtons() { hideNavigationButtons = true; }

    // Filter content
    public static boolean storyFilters = false;
    public static void storyFilters(){storyFilters = true;}
    public static boolean filterContentSection(){return storyFilters; }

    // Each entry reports the effective state: the patch must be applied (flag
    // set at startup) AND its toggle(s) are currently on. Multi-preference
    // features (e.g. nav buttons) count as on when any toggle is enabled;
    // entries with a single toggle pass one setting and report exactly that
    // preference value. Entries without a boolean toggle (list preferences,
    // multi-selects, or features with no switch) report application status only.
    // Ghost-mode entries report via their Pref getters so the "turn on all ghost
    // modes" override is reflected.
    // aboutSection() refreshes this map every time the About screen is opened.
    public static TreeMap<String, Boolean> loadStatusMap() {
        TreeMap<String, Boolean> flags = new TreeMap<>();

        flags.put(str("piko_disable_ads"), effective(disableAds, DISABLE_ADS));
        flags.put(str("piko_hide_suggested_content"), effective(hideSuggestedContent, HIDE_SUGGESTED_CONTENT));

        flags.put(str("piko_category_hide_navigation_buttons"), effective(hideNavigationButtons,
                HIDE_NAVIGATION_FEED, HIDE_NAVIGATION_SEARCH, HIDE_NAVIGATION_REELS,
                HIDE_NAVIGATION_DIRECT, HIDE_NAVIGATION_CREATE));

        flags.put(str("piko_category_download_media"), effective(downloadMedia, ENABLE_DOWNLOAD));
        flags.put(str("piko_download_voice_media"), downloadVoiceMessage);
        flags.put(str("piko_download_with_external_downloader"), effective(downloadWithExternalDownloader, DOWNLOAD_WITH_EXTERNAL_DOWNLOADER));
        flags.put(str("piko_more_profile_options"), moreOptionsOnProfile);
        flags.put(str("piko_enable_more_options_on_post"), effective(moreOptionsOnPost, ENABLE_MORE_OPTIONS_ON_POST));

        flags.put(str("piko_disable_video_autoplay"), effective(disableVideoAutoplay, DISABLE_VIDEO_AUTOPLAY));
        flags.put(str("piko_remove_empty_bottom_space"), effective(removeEmptyBottomSpace, REMOVE_EMPTY_BOTTOM_SPACE));
        flags.put(str("piko_save_media_comment"), effective(saveMediaCommentButton, COMMENT_SAVE_MEDIA_BUTTON));
        flags.put(str("piko_copy_comment"), effective(copyCommentButton, COMMENT_COPY_BUTTON));
        flags.put(str("piko_hide_reshare_button"), effective(hideReshareButton, HIDE_RESHARE_BUTTON));
        flags.put(str("piko_improve_image_viewing"), effective(improveImageViewing, IMPROVE_IMAGE_VIEWING));
        flags.put(str("piko_unlimited_replays"), effective(unlimitedReplaysOnEphemeralMedia, UNLIMITED_REPLAYS));

        flags.put(str("piko_customise_story_timestamp"), customiseStoryTimestamp);
        flags.put(str("piko_disable_story_flipping"), effective(disableStoryFlipping, DISABLE_STORY_FLIPPING));
        flags.put(str("piko_loop_story"), effective(loopStory, LOOP_STORY));
        flags.put(str("piko_view_story_mentions"), effective(viewStoryMentions, VIEW_STORY_MENTIONS));
        flags.put(str("piko_follow_back_indicator"), effective(followBackIndicator, FOLLOW_BACK_INDICATOR));
        flags.put(str("piko_disable_discover_people"), effective(disableDiscoverPeople, DISABLE_DISCOVER_PEOPLE));
        flags.put(str("piko_disable_analytics"), effective(disableAnalytics, DISABLE_ANALYTICS));
        flags.put(str("piko_customise_story_ring_size"), customiseStoryRingSize);

        flags.put(str("piko_change_like_animation"), changeLikeAnimation);
        flags.put(str("piko_unlock_plus_benefits"), effective(unlockPlusBenefits, UNLOCK_PLUS_BENEFITS));

        flags.put(str("piko_disable_double_tap_to_like"), effective(disableDoubleTapLike,
                DISABLE_DOUBLE_TAP_LIKE_POST, DISABLE_DOUBLE_TAP_LIKE_REEL,
                DISABLE_DOUBLE_TAP_LIKE_COMMENT, DISABLE_DOUBLE_TAP_LIKE_MESSAGE));
        flags.put(str("piko_hide_group_creation_button_on_sharesheet"), effective(hideGroupCreationOnSharesheet, HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET));
        flags.put(str("piko_limit_following_feed"), effective(limitFollowingFeed, LIMIT_FOLLOWING_FEED));
        flags.put(str("piko_hide_notes_tray"), effective(hideNotesTray, HIDE_NOTES_TRAY));
        flags.put(str("piko_hide_stories_tray"), effective(hideStoriesTray, HIDE_STORIES_TRAY));
        flags.put(str("piko_disable_comments"), effective(disableComments, DISABLE_COMMENTS));
        flags.put(str("piko_disable_explore"), effective(disableExplore, DISABLE_EXPLORE));
        flags.put(str("piko_disable_highlights"), effective(disableHighlights, DISABLE_HIGHLIGHTS));
        flags.put(str("piko_disable_stories"), effective(disableStories, DISABLE_STORIES));
        flags.put(str("piko_disable_reels_scrolling"), effective(disableReelsScrolling, DISABLE_REELS_SCROLLING));
        flags.put(str("piko_disable_swipe_to_create"), effective(disableSwipeToCreate, DISABLE_SWIPE_TO_CREATE));

        flags.put(str("piko_view_dm_anonymously"), effective(viewDmAnonymously, VIEW_DM_ANONYMOUSLY));
        flags.put(str("piko_save_deleted_messages"), effective(saveDeletedMessages, SAVE_DELETED_MESSAGES));
        flags.put(str("piko_view_live_anonymously"), Pref.viewLiveAnonymously());
        flags.put(str("piko_disable_screenshot_detection"), Pref.disableScreenshotDetection());
        flags.put(str("piko_disable_typing_status"), effective(disableTypingStatus, DISABLE_TYPING_STATUS));
        flags.put(str("piko_view_stories_anonymously"), Pref.viewStoriesAnonymously());

        flags.put(str("piko_sanitize_share_links"), effective(sanitizeShareLinks, SANITIZE_SHARE_LINKS));
        flags.put(str("piko_custom_sharing_domain"), customSharingDomain);
        flags.put(str("piko_open_links_externally"), effective(openLinksExternally, OPEN_LINKS_EXTERNALLY));

        flags.put(str("piko_enable_dev_options"), effective(enableDeveloperOptions, DEVELOPER_OPTIONS));
        flags.put(str("piko_remove_build_expire_popup"), effective(removeBuildExpirePopup, REMOVE_BUILD_EXPIRE_POPUP));
        flags.put(str("piko_enable_emp_options"), effective(unlockEmployeeOptions, ENABLE_EMP_OPTIONS));
        flags.put(str("piko_allow_user_network_certificate"), effective(allowUserNetworkCertificate, ALLOW_USER_NETWORK_CERTIFICATE));

        flags.put(str("piko_enable_mark_chat_as_read"), effective(markChatAsRead, ENABLE_MARK_CHAT_AS_READ));
        flags.put(str("piko_category_filter_content"), storyFilters);
        flags.put(str("piko_category_rec_flags"), recommendedFlags);

        return flags;
    }

    private static boolean effective(boolean applied, BooleanSetting... prefs) {
        if (!applied) return false;
        for (BooleanSetting pref : prefs) {
            if (SharedPref.getBooleanPref(pref)) return true;
        }
        return false;
    }

    public static void load() {
        loadStatusMap();
    }
}
