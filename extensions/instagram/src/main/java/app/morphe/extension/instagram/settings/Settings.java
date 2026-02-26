package app.morphe.extension.instagram.settings;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;

public class Settings extends BaseSettings {
    public static final BooleanSetting DISABLE_ADS = new BooleanSetting("disable_ads", true);
    public static final BooleanSetting OPEN_LINKS_EXTERNALLY = new BooleanSetting("open_links_externally", true);
    public static final BooleanSetting HIDE_SUGGESTED_CONTENT = new BooleanSetting("hide_suggested_content", true);
    public static final BooleanSetting DEVELOPER_OPTIONS = new BooleanSetting("enable_developer_options", true);
    public static final BooleanSetting REMOVE_BUILD_EXPIRE_POPUP = new BooleanSetting("remove_build_expire_popup", true);
    public static final BooleanSetting DISABLE_ANALYTICS = new BooleanSetting("disable_analytics", true);
    public static final BooleanSetting VIEW_STORIES_ANONYMOUSLY = new BooleanSetting("view_stories_anonymously", false);
    public static final BooleanSetting VIEW_LIVE_ANONYMOUSLY = new BooleanSetting("view_live_anonymously", false);
    public static final BooleanSetting DISABLE_STORIES = new BooleanSetting("disable_stories", false);
    public static final BooleanSetting DISABLE_FEED = new BooleanSetting("disable_feed", false);
    public static final BooleanSetting DISABLE_REELS = new BooleanSetting("disable_reels", false);
    public static final BooleanSetting DISABLE_EXPLORE = new BooleanSetting("disable_explore", false);
    public static final BooleanSetting DISABLE_COMMENTS = new BooleanSetting("disable_comments", false);
}