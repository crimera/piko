/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/
package app.morphe.extension.instagram.settings;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;

public class Settings {
    public static final BooleanSetting PIKO_DEBUG = new BooleanSetting("piko_debug", false);
    // It should be true always. It will be handled upon opening the piko settings for the first time.
    public static final BooleanSetting FIRST_TIME_PIKO = new BooleanSetting("first_time_piko", true);

    public static final BooleanSetting DISABLE_ADS = new BooleanSetting("disable_ads", true);
    public static final BooleanSetting OPEN_LINKS_EXTERNALLY = new BooleanSetting("open_links_externally", true);
    public static final BooleanSetting SANITIZE_SHARE_LINKS = new BooleanSetting("sanitize_share_links", true);
    public static final BooleanSetting HIDE_SUGGESTED_CONTENT = new BooleanSetting("hide_suggested_content", true);
    public static final BooleanSetting DEVELOPER_OPTIONS = new BooleanSetting("enable_developer_options", true);
    public static final BooleanSetting DIRECTLY_OPEN_METACONFIG = new BooleanSetting("directly_open_metaconfig",false);
    public static final BooleanSetting ENABLE_EMP_OPTIONS = new BooleanSetting("enable_employee_options",false);
    public static final BooleanSetting ALLOW_USER_NETWORK_CERTIFICATE = new BooleanSetting("allow_user_network_certificate",false);
    public static final BooleanSetting DISABLE_DISCOVER_PEOPLE = new BooleanSetting("disable_discover_people", true);
    public static final BooleanSetting LIMIT_FOLLOWING_FEED = new BooleanSetting("limit_following_feed", false);
    public static final BooleanSetting REMOVE_BUILD_EXPIRE_POPUP = new BooleanSetting("remove_build_expire_popup", true);
    public static final BooleanSetting DISABLE_ANALYTICS = new BooleanSetting("disable_analytics", true);
    public static final BooleanSetting TURN_ON_ALL_GHOST_MODES = new BooleanSetting("turn_on_all_ghost_modes", false);
    public static final BooleanSetting GHOST_MODES_QUICK_TOGGLE = new BooleanSetting("ghost_mode_quick_toggle", true);
    public static final BooleanSetting VIEW_STORIES_ANONYMOUSLY = new BooleanSetting("view_stories_anonymously", false);
    public static final BooleanSetting VIEW_LIVE_ANONYMOUSLY = new BooleanSetting("view_live_anonymously", true);
    public static final BooleanSetting DISABLE_SCREENSHOT_DETECTION = new BooleanSetting("disable_screenshot_detection", true);
    public static final BooleanSetting VIEW_DM_ANONYMOUSLY = new BooleanSetting("view_dm_anonymously", false);
    public static final BooleanSetting DISABLE_STORIES = new BooleanSetting("disable_stories", false);
    public static final BooleanSetting DISABLE_HIGHLIGHTS = new BooleanSetting("disable_highlights", false);
    public static final BooleanSetting HIDE_STORIES_TRAY = new BooleanSetting("hide_stories_tray", false);
    public static final BooleanSetting DISABLE_EXPLORE = new BooleanSetting("disable_explore", false);
    public static final BooleanSetting DISABLE_COMMENTS = new BooleanSetting("disable_comments", false);
    public static final BooleanSetting FOLLOW_BACK_INDICATOR = new BooleanSetting("follow_back_indicator", true);
    public static final BooleanSetting VIEW_STORY_MENTIONS = new BooleanSetting("view_story_mentions", true);
    public static final BooleanSetting DISABLE_STORY_FLIPPING = new BooleanSetting("disable_story_flipping", false);
    public static final StringSetting CUSTOMISE_STORY_TIMESTAMP = new StringSetting("customise_story_timestamp", "default");
    public static final BooleanSetting UNLIMITED_REPLAYS = new BooleanSetting("unlimited_replays", true);
    public static final BooleanSetting HIDE_RESHARE_BUTTON = new BooleanSetting("hide_reshare_button", false);
    public static final BooleanSetting IMPROVE_IMAGE_VIEWING = new BooleanSetting("improve_image_viewing", false);
    public static final BooleanSetting COMMENT_COPY_BUTTON = new BooleanSetting("comment_copy_button", true);
    public static final BooleanSetting COMMENT_SAVE_MEDIA_BUTTON = new BooleanSetting("comment_save_media_button", true);
    public static final BooleanSetting HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET = new BooleanSetting("hide_group_creation_button_on_sharesheet", true);
    public static final BooleanSetting DISABLE_REELS_SCROLLING = new BooleanSetting("disable_reels_scrolling", false);
    public static final BooleanSetting REMOVE_EMPTY_BOTTOM_SPACE = new BooleanSetting("remove_empty_bottom_space", true);
    public static final BooleanSetting MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE = new BooleanSetting("more_profile_options_action_bar_toggle", false);
    public static final BooleanSetting DISABLE_TYPING_STATUS = new BooleanSetting("disable_typing_status", false);
    public static final BooleanSetting HIDE_NOTES_TRAY = new BooleanSetting("hide_notes_tray", false);
    public static final BooleanSetting DISABLE_VIDEO_AUTOPLAY = new BooleanSetting("disable_video_autoplay", false);
    public static final BooleanSetting STORIES_AUDIO_AUTOPLAY = new BooleanSetting("stories_audio_autoplay", false);
    public static final BooleanSetting UNLOCK_PLUS_BENEFITS = new BooleanSetting("unlock_plus_benefits", false);
    public static final StringSetting CHANGE_LIKE_ANIMATION = new StringSetting("change_like_animation", "ARES_LIKE_ACTIVATION");
    public static final StringSetting CUSTOMISE_STORY_RING_SIZE = new StringSetting("customise_story_ring_size", "100");
    public static final BooleanSetting ENABLE_MORE_OPTIONS_ON_POST = new BooleanSetting("enable_more_option_on_post", true);

    public static final BooleanSetting DISABLE_DOUBLE_TAP_LIKE_POST = new BooleanSetting("disable_double_tap_like_post", false);
    public static final BooleanSetting DISABLE_DOUBLE_TAP_LIKE_REEL = new BooleanSetting("disable_double_tap_like_reel", false);
    public static final BooleanSetting DISABLE_DOUBLE_TAP_LIKE_COMMENT = new BooleanSetting("disable_double_tap_like_comment", false);
    public static final BooleanSetting DISABLE_DOUBLE_TAP_LIKE_MESSAGE = new BooleanSetting("disable_double_tap_like_message", false);

    public static final BooleanSetting ENABLE_DOWNLOAD = new BooleanSetting("enable_download", true);
    public static final BooleanSetting ENABLE_DIRECT_DOWNLOAD = new BooleanSetting("enable_direct_download", false);
    public static final BooleanSetting DOWNLOAD_USERNAME_FOLDER = new BooleanSetting("download_username_folder", false);
    // Should be kept empty by default as its handled in `StorageUtils.java`
    public static final StringSetting CUSTOM_DOWNLOAD_PATH = new StringSetting("custom_download_path", "");
    public static final BooleanSetting PIKO_SETTINGS_ON_ACTION_BAR = new BooleanSetting("piko_settings_on_action_bar", false);
    public static final StringSetting EXTERNAL_DOWNLOADER_PACKAGE_NAME = new StringSetting("external_downloader_package_name", "");
    public static final BooleanSetting DOWNLOAD_WITH_EXTERNAL_DOWNLOADER = new BooleanSetting("download_with_external_downloader", true);

    public static final BooleanSetting HIDE_NAVIGATION_FEED = new BooleanSetting("hide_navigation_feed", false);
    public static final BooleanSetting HIDE_NAVIGATION_REELS = new BooleanSetting("hide_navigation_reels", false);
    public static final BooleanSetting HIDE_NAVIGATION_DIRECT = new BooleanSetting("hide_navigation_direct", false);
    public static final BooleanSetting HIDE_NAVIGATION_SEARCH = new BooleanSetting("hide_navigation_search", false);
    public static final BooleanSetting HIDE_NAVIGATION_CREATE = new BooleanSetting("hide_navigation_create", false);
}
