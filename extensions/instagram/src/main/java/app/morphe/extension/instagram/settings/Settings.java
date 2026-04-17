/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;

public class Settings extends BaseSettings {
    public static final BooleanSetting DISABLE_ADS = new BooleanSetting("disable_ads", true);
    public static final BooleanSetting OPEN_LINKS_EXTERNALLY = new BooleanSetting("open_links_externally", true);
    public static final BooleanSetting SANITIZE_SHARE_LINKS = new BooleanSetting("sanitize_share_links", true);
    public static final BooleanSetting HIDE_SUGGESTED_CONTENT = new BooleanSetting("hide_suggested_content", true);
    public static final BooleanSetting DEVELOPER_OPTIONS = new BooleanSetting("enable_developer_options", true);
    public static final BooleanSetting DISABLE_DISCOVER_PEOPLE = new BooleanSetting("disable_discover_people", true);
    public static final BooleanSetting LIMIT_FOLLOWING_FEED = new BooleanSetting("limit_following_feed", false);
    public static final BooleanSetting REMOVE_BUILD_EXPIRE_POPUP = new BooleanSetting("remove_build_expire_popup", true);
    public static final BooleanSetting DISABLE_ANALYTICS = new BooleanSetting("disable_analytics", true);
    public static final BooleanSetting VIEW_STORIES_ANONYMOUSLY = new BooleanSetting("view_stories_anonymously", false);
    public static final BooleanSetting VIEW_LIVE_ANONYMOUSLY = new BooleanSetting("view_live_anonymously", true);
    public static final BooleanSetting DISABLE_SCREENSHOT_DETECTION = new BooleanSetting("disable_screenshot_detection", true);
    public static final BooleanSetting DISABLE_STORIES = new BooleanSetting("disable_stories", false);
    public static final BooleanSetting HIDE_STORIES_TRAY = new BooleanSetting("hide_stories_tray", false);
    public static final BooleanSetting DISABLE_EXPLORE = new BooleanSetting("disable_explore", false);
    public static final BooleanSetting DISABLE_COMMENTS = new BooleanSetting("disable_comments", false);
    public static final BooleanSetting FOLLOW_BACK_INDICATOR = new BooleanSetting("follow_back_indicator", true);
    public static final BooleanSetting VIEW_STORY_MENTIONS = new BooleanSetting("view_story_mentions", true);
    public static final BooleanSetting DISABLE_STORY_FLIPPING = new BooleanSetting("disable_story_flipping", false);
    public static final StringSetting CUSTOMISE_STORY_TIMESTAMP = new StringSetting("customise_story_timestamp", "default");
    public static final BooleanSetting UNLIMITED_REPLAYS = new BooleanSetting("unlimited_replays", true);
    public static final BooleanSetting HIDE_RESHARE_BUTTON = new BooleanSetting("hide_reshare_button", true);
    public static final BooleanSetting IMPROVE_IMAGE_VIEWING = new BooleanSetting("improve_image_viewing", true);
    public static final BooleanSetting COMMENT_COPY_BUTTON = new BooleanSetting("comment_copy_button", true);
    public static final BooleanSetting HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET = new BooleanSetting("hide_group_creation_button_on_sharesheet", true);
    public static final BooleanSetting REMOVE_EMPTY_BOTTOM_SPACE = new BooleanSetting("remove_empty_bottom_space", true);
    public static final BooleanSetting DISABLE_TYPING_STATUS = new BooleanSetting("disable_typing_status", true);

    public static final BooleanSetting ENABLE_DOWNLOAD = new BooleanSetting("enable_download", true);
    public static final BooleanSetting ENABLE_DIRECT_DOWNLOAD = new BooleanSetting("enable_direct_download", false);
    public static final BooleanSetting DOWNLOAD_USERNAME_FOLDER = new BooleanSetting("download_username_folder", false);


    public static final BooleanSetting HIDE_NAVIGATION_FEED = new BooleanSetting("hide_navigation_feed", false);
    public static final BooleanSetting HIDE_NAVIGATION_REELS = new BooleanSetting("hide_navigation_reels", false);
    public static final BooleanSetting HIDE_NAVIGATION_DIRECT = new BooleanSetting("hide_navigation_direct", false);
    public static final BooleanSetting HIDE_NAVIGATION_SEARCH = new BooleanSetting("hide_navigation_search", false);
    public static final BooleanSetting HIDE_NAVIGATION_CREATE = new BooleanSetting("hide_navigation_create", false);
    public static final BooleanSetting HIDE_NAVIGATION_PROFILE = new BooleanSetting("hide_navigation_profile", false);
}
