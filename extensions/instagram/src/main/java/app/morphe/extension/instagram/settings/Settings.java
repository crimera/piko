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
    public static final BooleanSetting REMOVE_BUILD_EXPIRE_POPUP = new BooleanSetting("remove_build_expire_popup", true);
    public static final BooleanSetting DISABLE_ANALYTICS = new BooleanSetting("disable_analytics", true);
    public static final BooleanSetting VIEW_STORIES_ANONYMOUSLY = new BooleanSetting("view_stories_anonymously", false);
    public static final BooleanSetting VIEW_LIVE_ANONYMOUSLY = new BooleanSetting("view_live_anonymously", false);
    public static final BooleanSetting DISABLE_STORIES = new BooleanSetting("disable_stories", false);
    public static final BooleanSetting HIDE_STORIES_TRAY = new BooleanSetting("hide_stories_tray", true);
    public static final BooleanSetting DISABLE_EXPLORE = new BooleanSetting("disable_explore", false);
    public static final BooleanSetting DISABLE_COMMENTS = new BooleanSetting("disable_comments", false);
    public static final BooleanSetting FOLLOW_BACK_INDICATOR = new BooleanSetting("follow_back_indicator", true);
    public static final BooleanSetting VIEW_STORY_MENTIONS = new BooleanSetting("view_story_mentions", true);
    public static final BooleanSetting DISABLE_STORY_FLIPPING = new BooleanSetting("disable_story_flipping", true);
    public static final StringSetting CUSTOMISE_STORY_TIMESTAMP = new StringSetting("customise_story_timestamp", "default");

    public static final BooleanSetting ENABLE_DOWNLOAD = new BooleanSetting("enable_download", true);
    public static final BooleanSetting ENABLE_DIRECT_DOWNLOAD = new BooleanSetting("enable_direct_download", false);
    public static final BooleanSetting DOWNLOAD_USERNAME_FOLDER = new BooleanSetting("download_username_folder", false);

}