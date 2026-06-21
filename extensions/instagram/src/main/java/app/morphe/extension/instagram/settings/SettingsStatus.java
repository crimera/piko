/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.instagram.settings;

import java.util.TreeMap;
import static app.morphe.extension.instagram.utils.IgStr.str;

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
    public static boolean developerOptionsSection() {
        return (allowUserNetworkCertificate || unlockEmployeeOptions || enableDeveloperOptions || removeBuildExpirePopup);
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
    public static boolean linksSection() {
        return (openLinksExternally || sanitizeShareLinks);
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
    public static boolean disableDoubleTapLike = false;
    public static void disableDoubleTapLike() {
        disableDoubleTapLike = true;
    }
    public static boolean distractionFreeSection() {
        return (disableDoubleTapLike || hideNotesTray || disableHighlights || disableStories || disableExplore || disableComments || hideStoriesTray || limitFollowingFeed || hideGroupCreationOnSharesheet || disableReelsScrolling);
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
    public static boolean customiseStoryTimestamp = false;
    public static void customiseStoryTimestamp() {
        customiseStoryTimestamp = true;
    }
    public static boolean unlimitedReplaysOnEphemeralMedia = false;
    public static void unlimitedReplaysOnEphemeralMedia() {
        unlimitedReplaysOnEphemeralMedia = true;
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
    public static boolean storiesAudioAutoplay = false;
    public static void storiesAudioAutoplay() { storiesAudioAutoplay = true; }
    public static boolean moreOptionsOnPost = false;
    public static void moreOptionsOnPost() { moreOptionsOnPost = true; }
    public static boolean moreOptionsOnProfile = false;
    public static void moreOptionsOnProfile() { moreOptionsOnProfile = true; }
    public static boolean miscSection() {return (saveMediaCommentButton || moreOptionsOnProfile || moreOptionsOnPost || customiseStoryRingSize || changeLikeAnimation || unlockPlusBenefits || storiesAudioAutoplay || disableVideoAutoplay || removeEmptyBottomSpace || copyCommentButton || improveImageViewing || unlimitedReplaysOnEphemeralMedia || customiseStoryTimestamp || disableAnalytics || disableDiscoverPeople || followBackIndicator || viewStoryMentions || disableStoryFlipping || hideReshareButton);}

    //Download section.
    public static boolean downloadMedia = false;
    public static void downloadMedia() {downloadMedia = true;}
    public static boolean downloadVoiceMessage = false;
    public static void downloadVoiceMessage() { downloadVoiceMessage = true; }
    public static boolean downloadWithExternalDownloader = false;
    public static void downloadWithExternalDownloader() { downloadWithExternalDownloader = true; }
    public static boolean downloadSection(){return (SettingsStatus.downloadMedia || SettingsStatus.downloadWithExternalDownloader);}

    public static boolean hideNavigationButtons = false;
    public static void hideNavigationButtons() { hideNavigationButtons = true; }

    public static void loadStatusMap(){
        FLAGS.put(str("piko_disable_ads"),SettingsStatus.disableAds);
        FLAGS.put(str("piko_hide_suggested_content"),SettingsStatus.hideSuggestedContent);

        FLAGS.put(str("piko_category_hide_navigation_buttons"),SettingsStatus.hideNavigationButtons);

        FLAGS.put(str("piko_category_download_media"),SettingsStatus.downloadMedia);
        FLAGS.put(str("piko_download_voice_media"),SettingsStatus.downloadVoiceMessage);
        FLAGS.put(str("piko_download_with_external_downloader"),SettingsStatus.downloadWithExternalDownloader);
        FLAGS.put(str("piko_more_profile_options"),SettingsStatus.moreOptionsOnProfile);
        FLAGS.put(str("piko_enable_more_options_on_post"),SettingsStatus.moreOptionsOnPost);
        FLAGS.put(str("piko_stories_audio_autoplay"),SettingsStatus.storiesAudioAutoplay);

        FLAGS.put(str("piko_disable_video_autoplay"),SettingsStatus.disableVideoAutoplay);
        FLAGS.put(str("piko_remove_empty_bottom_space"),SettingsStatus.removeEmptyBottomSpace);
        FLAGS.put(str("piko_save_media_comment"),SettingsStatus.saveMediaCommentButton);
        FLAGS.put(str("piko_copy_comment"),SettingsStatus.copyCommentButton);
        FLAGS.put(str("piko_hide_reshare_button"),SettingsStatus.hideReshareButton);
        FLAGS.put(str("piko_improve_image_viewing"),SettingsStatus.improveImageViewing);
        FLAGS.put(str("piko_unlimited_replays"),SettingsStatus.unlimitedReplaysOnEphemeralMedia);

        FLAGS.put(str("piko_customise_story_timestamp"),SettingsStatus.customiseStoryTimestamp);
        FLAGS.put(str("piko_disable_story_flipping"),SettingsStatus.disableStoryFlipping);
        FLAGS.put(str("piko_view_story_mentions"),SettingsStatus.viewStoryMentions);
        FLAGS.put(str("piko_follow_back_indicator"),SettingsStatus.followBackIndicator);
        FLAGS.put(str("piko_disable_discover_people"),SettingsStatus.disableDiscoverPeople);
        FLAGS.put(str("piko_disable_analytics"),SettingsStatus.disableAnalytics);
        FLAGS.put(str("piko_customise_story_ring_size"),SettingsStatus.customiseStoryRingSize);

        FLAGS.put(str("piko_change_like_animation"),SettingsStatus.changeLikeAnimation);
        FLAGS.put(str("piko_unlock_plus_benefits"),SettingsStatus.unlockPlusBenefits);

        FLAGS.put(str("piko_disable_double_tap_to_like"),SettingsStatus.disableDoubleTapLike);
        FLAGS.put(str("piko_hide_group_creation_button_on_sharesheet"),SettingsStatus.hideGroupCreationOnSharesheet);
        FLAGS.put(str("piko_limit_following_feed"),SettingsStatus.limitFollowingFeed);
        FLAGS.put(str("piko_hide_notes_tray"),SettingsStatus.hideNotesTray);
        FLAGS.put(str("piko_hide_stories_tray"),SettingsStatus.hideStoriesTray);
        FLAGS.put(str("piko_disable_comments"),SettingsStatus.disableComments);
        FLAGS.put(str("piko_disable_explore"),SettingsStatus.disableExplore);
        FLAGS.put(str("piko_disable_highlights"),SettingsStatus.disableHighlights);
        FLAGS.put(str("piko_disable_stories"),SettingsStatus.disableStories);

        FLAGS.put(str("piko_view_dm_anonymously"),SettingsStatus.viewDmAnonymously);
        FLAGS.put(str("piko_view_live_anonymously"),SettingsStatus.disableScreenshotDetection);
        FLAGS.put(str("piko_disable_typing_status"),SettingsStatus.disableTypingStatus);
        FLAGS.put(str("piko_more_profile_options"),SettingsStatus.viewLiveAnonymously);
        FLAGS.put(str("piko_view_stories_anonymously"),SettingsStatus.viewStoriesAnonymously);

        FLAGS.put(str("piko_sanitize_share_links"),SettingsStatus.sanitizeShareLinks);
        FLAGS.put(str("piko_open_links_externally"),SettingsStatus.openLinksExternally);
        FLAGS.put(str("piko_download_voice_media"),SettingsStatus.downloadVoiceMessage);
        FLAGS.put(str("piko_download_with_external_downloader"),SettingsStatus.downloadWithExternalDownloader);
        FLAGS.put(str("piko_more_profile_options"),SettingsStatus.moreOptionsOnProfile);
        FLAGS.put(str("piko_enable_more_options_on_post"),SettingsStatus.moreOptionsOnPost);
        FLAGS.put(str("piko_enable_more_options_on_post"),SettingsStatus.storiesAudioAutoplay);

        FLAGS.put(str("piko_disable_ads"),SettingsStatus.disableAds);
        FLAGS.put(str("piko_category_download_media"),SettingsStatus.downloadMedia);
        FLAGS.put(str("piko_download_voice_media"),SettingsStatus.downloadVoiceMessage);
        FLAGS.put(str("piko_download_with_external_downloader"),SettingsStatus.downloadWithExternalDownloader);
        FLAGS.put(str("piko_more_profile_options"),SettingsStatus.moreOptionsOnProfile);
        FLAGS.put(str("piko_enable_more_options_on_post"),SettingsStatus.moreOptionsOnPost);
        FLAGS.put(str("piko_enable_more_options_on_post"),SettingsStatus.storiesAudioAutoplay);

        FLAGS.put(str("piko_enable_dev_options"),SettingsStatus.enableDeveloperOptions);
        FLAGS.put(str("piko_remove_build_expire_popup"),SettingsStatus.removeBuildExpirePopup);
        FLAGS.put(str("piko_enable_emp_options"),SettingsStatus.unlockEmployeeOptions);
        FLAGS.put(str("piko_allow_user_network_certificate"),SettingsStatus.allowUserNetworkCertificate);
    }

    public static void load() {
        loadStatusMap();
    }
}
