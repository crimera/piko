/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import app.morphe.extension.crimera.settings.BooleanSetting;
import app.morphe.extension.crimera.settings.StringSetting;

public class Settings {
    // Patches
    public static final BooleanSetting HIDE_ADS = new BooleanSetting("hide_ads", true);
    public static final BooleanSetting HIDE_GROK = new BooleanSetting("hide_grok", true);
    public static final BooleanSetting HIDE_WTF = new BooleanSetting("hide_wtf", true);
    public static final BooleanSetting HIDE_CTS = new BooleanSetting("hide_cts", true);
    public static final BooleanSetting HIDE_CTJ = new BooleanSetting("hide_ctj", true);
    public static final BooleanSetting HIDE_DETAILED_POSTS = new BooleanSetting("hide_detailed_posts", true);
    public static final BooleanSetting HIDE_RBMK = new BooleanSetting("hide_rbmk", true);
    public static final BooleanSetting HIDE_RPINNED_POSTS = new BooleanSetting("hide_rpinned_posts", true);
    public static final BooleanSetting HIDE_PREMIUM_PROMPT = new BooleanSetting("hide_premium_prompt", true);
    public static final BooleanSetting HIDE_TOP_PEOPLE_SEARCH = new BooleanSetting("hide_top_people_search", true);
    public static final BooleanSetting HIDE_TODAYS_NEWS = new BooleanSetting("hide_todays_news", true);

    public static final BooleanSetting HIDE_NAVBAR_BADGE = new BooleanSetting("hide_navbar_badge", true);
    public static final BooleanSetting HIDE_POST_INLINE_METRICS = new BooleanSetting("hide_post_inline_metrics", false);
    public static final BooleanSetting HIDE_POST_DETAILED_METRICS = new BooleanSetting("hide_post_detailed_metrics", false);
    public static final BooleanSetting HIDE_NUDGE_BUTTON = new BooleanSetting("hide_nudge_button", true);
    public static final BooleanSetting HIDE_SOCIAL_PROOF = new BooleanSetting("hide_social_proof", true);
    public static final BooleanSetting HIDE_COMM_BADGE = new BooleanSetting("hide_comm_badge", true);
    public static final BooleanSetting HIDE_COMM_NOTE = new BooleanSetting("hide_comm_note", true);
    public static final BooleanSetting HIDE_LIVE_THREADS = new BooleanSetting("hide_live_threads", true);
    public static final BooleanSetting HIDE_BANNER = new BooleanSetting("hide_banner", true);
    public static final BooleanSetting HIDE_INLINE_BMK = new BooleanSetting("hide_inline_bmk", true);
    public static final BooleanSetting HIDE_PROMOTE_BUTTON = new BooleanSetting("hide_promote_button", true);
    public static final BooleanSetting HIDE_IMMERSIVE_PLAYER = new BooleanSetting("hide_immersive_player", true);
    public static final BooleanSetting SHOW_POLL_RESULTS = new BooleanSetting("show_poll_results", true);
    public static final BooleanSetting SHOW_SENSITIVE_MEDIA = new BooleanSetting("show_sensitive_media", false);
    public static final BooleanSetting SELECTABLE_TEXT = new BooleanSetting("selectable_text", true);
    public static final BooleanSetting ROUND_OFF_NUMBERS = new BooleanSetting("round_off_numbers", true);
    public static final BooleanSetting ENABLE_CHIRP_FONT = new BooleanSetting("enable_chirp_font", true);
    public static final BooleanSetting ENABLE_FORCE_HD = new BooleanSetting("enable_force_hd", false);
    public static final BooleanSetting ENABLE_VID_AUTO_ADVANCE = new BooleanSetting("enable_vid_auto_advance", false);
    public static final BooleanSetting ENABLE_VID_DOWNLOAD = new BooleanSetting("enable_vid_download", true);
    public static final BooleanSetting REMOVE_PREMIUM_UPSELL = new BooleanSetting("remove_premium_upsell", true);
    public static final BooleanSetting ENABLE_UNDO_POSTS = new BooleanSetting("enable_undo_posts", false);
    public static final BooleanSetting ENABLE_FORCE_PIP = new BooleanSetting("enable_force_pip", false);
    public static final BooleanSetting SHOW_SOURCE_LABEL = new BooleanSetting("show_source_label", true);
    public static final BooleanSetting FORCE_TRANSLATE = new BooleanSetting("force_translate", true);
    public static final BooleanSetting HIDE_HIDDEN_REPLIES = new BooleanSetting("hide_hidden_replies", true);
    public static final BooleanSetting LEGACY_SHARE_LINK = new BooleanSetting("legacy_share_link", false);
    public static final BooleanSetting DISABLE_AUTO_TIMELINE_SCROLL = new BooleanSetting("disable_auto_timeline_scroll", false);
    public static final BooleanSetting DISUNIFY_XCHAT_SYSTEM = new BooleanSetting("disunify_xchat_system", false);
    public static final BooleanSetting EXPORT_LOGIN_TOKEN = new BooleanSetting("export_login_token", false);
    public static final BooleanSetting SHOW_CHANGELOGS = new BooleanSetting("show_changelogs", true);
    public static final BooleanSetting LOG_SERVER_RESPONSE = new BooleanSetting("log_server_response", false);
    public static final BooleanSetting LOG_SERVER_RESPONSE_OVERWRITE = new BooleanSetting("log_server_response_overwrite", false);

    // Downloads
    public static final BooleanSetting CHANGE_DOWNLOAD_DIR_ENABLED = new BooleanSetting("change_download_dir_enabled", false);
    public static final StringSetting CUSTOM_DOWNLOAD_DIR = new StringSetting("custom_download_dir", "Piko");
    public static final StringSetting DOWNLOAD_FILENAME_FORMAT = new StringSetting("download_filename_format", "1");
    public static final StringSetting MEDIA_LINK_HANDLE = new StringSetting("media_link_handle", "download_media");

    // Native features
    public static final BooleanSetting ENABLE_NATIVE_DOWNLOADER = new BooleanSetting("enable_native_downloader", true);
    public static final BooleanSetting ENABLE_INLINE_DOWNLOAD_BUTTON = new BooleanSetting("enable_inline_download_button", true);
    public static final BooleanSetting ENABLE_NATIVE_TRANSLATOR = new BooleanSetting("enable_native_translator", true);
    public static final BooleanSetting ENABLE_NATIVE_READER_MODE = new BooleanSetting("enable_native_reader_mode", true);
    public static final BooleanSetting ENABLE_SHARE_IMAGE = new BooleanSetting("enable_share_image", true);
    public static final BooleanSetting ENABLE_BROWSE_OBJECT = new BooleanSetting("enable_browse_object", true);
    public static final BooleanSetting DELETE_FROM_DB = new BooleanSetting("delete_from_db", false);
    public static final BooleanSetting CLEAR_TRACKING_PARAMS = new BooleanSetting("clear_tracking_params", true);
    public static final BooleanSetting UNSHORTEN_LINK = new BooleanSetting("unshorten_link", true);
    public static final BooleanSetting ENABLE_CUSTOM_SHARING_DOMAIN = new BooleanSetting("enable_custom_sharing_domain", false);
    public static final StringSetting CUSTOM_SHARING_DOMAIN = new StringSetting("custom_sharing_domain", "vxtwitter.com");

    // Native reader mode
    public static final BooleanSetting NATIVE_READER_MODE_TEXT_ONLY = new BooleanSetting("native_reader_mode_text_only", false);
    public static final BooleanSetting NATIVE_READER_MODE_HIDE_QUOTED = new BooleanSetting("native_reader_mode_hide_quoted", false);
    public static final BooleanSetting NATIVE_READER_MODE_NO_GROK = new BooleanSetting("native_reader_mode_no_grok", false);
    public static final StringSetting NATIVE_READER_MODE_THEME = new StringSetting("native_reader_mode_theme", "system");

    // Native translator
    public static final StringSetting NATIVE_TRANSLATOR_TYPE = new StringSetting("native_translator_type", "0");

    // Customisation
    public static final BooleanSetting ENABLE_FONT_MOD = new BooleanSetting("enable_font_mod", false);
    public static final StringSetting CUSTOM_FONT_PATH = new StringSetting("custom_font_path", "");
    public static final StringSetting CUSTOM_EMOJI_FONT_PATH = new StringSetting("custom_emoji_font_path", "");
    public static final StringSetting CUSTOM_POST_FONT_SIZE = new StringSetting("custom_post_font_size", "14");
    public static final StringSetting CUSTOM_NAVBAR_TABS = new StringSetting("custom_navbar_tabs", "");
    public static final StringSetting CUSTOM_SIDEBAR_TABS = new StringSetting("custom_sidebar_tabs", "");
    public static final StringSetting CUSTOM_PROFILE_TABS = new StringSetting("custom_profile_tabs", "");
    public static final StringSetting CUSTOM_TIMELINE_TABS = new StringSetting("custom_timeline_tabs", "show_both");
    public static final StringSetting CUSTOM_EXPLORE_TABS = new StringSetting("custom_explore_tabs", "");
    public static final StringSetting CUSTOM_SEARCH_TABS = new StringSetting("custom_search_tabs", "");
    public static final StringSetting CUSTOM_NOTIFICATION_TABS = new StringSetting("custom_notification_tabs", "");
    public static final StringSetting CUSTOM_INLINE_TABS = new StringSetting("custom_inline_tabs", "");
    public static final StringSetting CUSTOM_SEARCH_TYPE_AHEAD = new StringSetting("custom_search_type_ahead", "");
    public static final StringSetting DEFAULT_REPLY_SORT_FILTER = new StringSetting("default_reply_sort_filter", "Relevance");
    public static final BooleanSetting NAVBAR_FIX = new BooleanSetting("navbar_fix", true);
    public static final StringSetting APP_ICON = new StringSetting("app_icon", "default");

    // Misc
    public static final BooleanSetting REMOVE_SEARCH_SUGGESTIONS = new BooleanSetting("remove_search_suggestions", true);
    public static final BooleanSetting PAUSE_SEARCH_SUGGESTIONS = new BooleanSetting("pause_search_suggestions", false);
    public static final BooleanSetting ENABLE_FEATURE_FLAGS = new BooleanSetting("enable_feature_flags", false);
    public static final StringSetting MISC_FEATURE_FLAGS = new StringSetting("misc_feature_flags", "");
    public static final BooleanSetting HIDE_FAB = new BooleanSetting("hide_fab", false);
    public static final BooleanSetting HIDE_FAB_BTNS = new BooleanSetting("hide_fab_btns", false);
    public static final BooleanSetting HIDE_VIEW_COUNT = new BooleanSetting("hide_view_count", true);

    // Internal
    public static final StringSetting LAST_CHANGELOG_VERSION = new StringSetting("last_changelog_version", "0");
    public static final StringSetting LAST_CHANGELOG = new StringSetting("last_changelog", "");

}
