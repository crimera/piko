/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter;

import android.util.Log;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.settings.Settings;
import com.google.android.material.tabs.TabLayout;
import java.util.*;

@SuppressWarnings("unused")
public class Pref {
    public static final boolean HIDE_DETAILED_METRICS,HIDE_INLINE_METRICS,ROUND_OFF_NUMBERS,ENABLE_FORCE_HD, HIDE_COMM_BADGE,SHOW_SRC_LBL;
    public static final float POST_FONT_SIZE;
    static{
        ROUND_OFF_NUMBERS = isRoundOffNumbersEnabled();
        ENABLE_FORCE_HD = enableForceHD();
        POST_FONT_SIZE = setPostFontSize();
        HIDE_COMM_BADGE = hideCommBadge();
        SHOW_SRC_LBL = showSourceLabel();
        HIDE_INLINE_METRICS = hidePostInlineMetrics();
        HIDE_DETAILED_METRICS = hidePostDetailedMetrics();
    }

    public static String getLatestChangelogVersion(){
        return Utils.getStringPref(Settings.LAST_CHANGELOG_VERSION);
    }
    public static void setLatestChangelogVersion(String version){
        Utils.setStringPref(Settings.LAST_CHANGELOG_VERSION.key,version);
    }
    public static String getChangelog(){
        return Utils.getStringPref(Settings.LAST_CHANGELOG);
    }
    public static void setChangelog(String msg){
        Utils.setStringPref(Settings.LAST_CHANGELOG.key,msg);
    }

    public static float setPostFontSize() {
        float fontSize;
        try{
            fontSize = Float.parseFloat(Utils.getStringPref(Settings.CUSTOM_POST_FONT_SIZE));
        }catch (Exception ex){
            fontSize = ResourceUtils.getDimension("font_size_normal");
        }
        return fontSize;
    }

    public static boolean hideAds() {
        return Utils.getBooleanPref(Settings.HIDE_ADS);
    }

    public static boolean hideGrok() {
        return Utils.getBooleanPref(Settings.HIDE_GROK);
    }

    public static boolean hideWTF() {
        return Utils.getBooleanPref(Settings.HIDE_WTF);
    }

    public static boolean hideCTS() {
        return Utils.getBooleanPref(Settings.HIDE_CTS);
    }

    public static boolean hideCTJ() {
        return Utils.getBooleanPref(Settings.HIDE_CTJ);
    }

    public static boolean hideDetailedPosts() {
        return Utils.getBooleanPref(Settings.HIDE_DETAILED_POSTS);
    }

    public static boolean hideRBMK() {
        return Utils.getBooleanPref(Settings.HIDE_RBMK);
    }

    public static boolean hideRPinnedPosts() {
        return Utils.getBooleanPref(Settings.HIDE_RPINNED_POSTS);
    }

    public static boolean hidePremiumPrompt() {
        return Utils.getBooleanPref(Settings.HIDE_PREMIUM_PROMPT);
    }

    public static boolean hideTopPeopleSearch() {
        return Utils.getBooleanPref(Settings.HIDE_TOP_PEOPLE_SEARCH);
    }

    public static boolean hideTodaysNews() {
        return Utils.getBooleanPref(Settings.HIDE_TODAYS_NEWS);
    }

    public static boolean hideNavbarBadge() {
        return Utils.getBooleanPref(Settings.HIDE_NAVBAR_BADGE);
    }

    public static boolean hidePostInlineMetrics() {
        return Utils.getBooleanPref(Settings.HIDE_POST_INLINE_METRICS);
    }

    public static boolean hidePostDetailedMetrics() {
        return Utils.getBooleanPref(Settings.HIDE_POST_DETAILED_METRICS);
    }

    public static boolean hideNudgeButton() {
        return Utils.getBooleanPref(Settings.HIDE_NUDGE_BUTTON);
    }

    public static boolean hideSocialProof() {
        return Utils.getBooleanPref(Settings.HIDE_SOCIAL_PROOF);
    }

    public static boolean hideCommBadge() {
        return Utils.getBooleanPref(Settings.HIDE_COMM_BADGE);
    }

    public static boolean hideCommNote() {
        return Utils.getBooleanPref(Settings.HIDE_COMM_NOTE);
    }

    public static boolean hideLiveThreads() {
        return Utils.getBooleanPref(Settings.HIDE_LIVE_THREADS);
    }

    public static boolean hideBanner() {
        return Utils.getBooleanPref(Settings.HIDE_BANNER);
    }

    public static boolean hideInlineBmk() {
        return Utils.getBooleanPref(Settings.HIDE_INLINE_BMK);
    }

    public static boolean hidePromoteButton() {
        return Utils.getBooleanPref(Settings.HIDE_PROMOTE_BUTTON);
    }

    public static boolean hideImmersivePlayer() {
        return Utils.getBooleanPref(Settings.HIDE_IMMERSIVE_PLAYER);
    }

    public static boolean showPollResults() {
        return Utils.getBooleanPref(Settings.SHOW_POLL_RESULTS);
    }

    public static boolean showSensitiveMedia() {
        return Utils.getBooleanPref(Settings.SHOW_SENSITIVE_MEDIA);
    }

    public static boolean selectableText() {
        return Utils.getBooleanPref(Settings.SELECTABLE_TEXT);
    }

    public static boolean isRoundOffNumbersEnabled() {
        return Utils.getBooleanPref(Settings.ROUND_OFF_NUMBERS);
    }

    public static boolean enableChirpFont() {
        return Utils.getBooleanPref(Settings.ENABLE_CHIRP_FONT);
    }

    public static boolean enableForceHD() {
        return Utils.getBooleanPref(Settings.ENABLE_FORCE_HD);
    }

    public static boolean enableVidAutoAdvance() {
        return Utils.getBooleanPref(Settings.ENABLE_VID_AUTO_ADVANCE);
    }

    public static boolean enableVidDownload() {
        return Utils.getBooleanPref(Settings.ENABLE_VID_DOWNLOAD);
    }

    public static boolean removePremiumUpsell() {
        return Utils.getBooleanPref(Settings.REMOVE_PREMIUM_UPSELL);
    }

    public static boolean enableUndoPosts() {
        return Utils.getBooleanPref(Settings.ENABLE_UNDO_POSTS);
    }

    public static boolean enableForcePip() {
        return Utils.getBooleanPref(Settings.ENABLE_FORCE_PIP);
    }

    public static boolean showSourceLabel() {
        return Utils.getBooleanPref(Settings.SHOW_SOURCE_LABEL);
    }

    public static boolean forceTranslate() {
        return Utils.getBooleanPref(Settings.FORCE_TRANSLATE);
    }

    public static boolean hideHiddenReplies() {
        return Utils.getBooleanPref(Settings.HIDE_HIDDEN_REPLIES);
    }

    public static boolean legacyShareLink() {
        return Utils.getBooleanPref(Settings.LEGACY_SHARE_LINK);
    }

    public static boolean disableAutoTimelineScroll() {
        return Utils.getBooleanPref(Settings.DISABLE_AUTO_TIMELINE_SCROLL);
    }

    public static boolean disUnifyXChatSystem() {
        return Utils.getBooleanPref(Settings.DISUNIFY_XCHAT_SYSTEM);
    }

    public static boolean showChangelogs() {
        return Utils.getBooleanPref(Settings.SHOW_CHANGELOGS);
    }

    public static boolean logServerResponse() {
        return Utils.getBooleanPref(Settings.LOG_SERVER_RESPONSE);
    }

    public static boolean logServerResponseOverwrite() {
        return Utils.getBooleanPref(Settings.LOG_SERVER_RESPONSE_OVERWRITE);
    }

    public static boolean isChangeDownloadDirEnabled() {
        return Utils.getBooleanPref(Settings.CHANGE_DOWNLOAD_DIR_ENABLED);
    }

    public static String getCustomDownloadDir() {
        return Utils.getStringPref(Settings.CUSTOM_DOWNLOAD_DIR);
    }

    public static String getDownloadFilenameFormat() {
        return Utils.getStringPref(Settings.DOWNLOAD_FILENAME_FORMAT);
    }

    public static String getMediaLinkHandle() {
        return Utils.getStringPref(Settings.MEDIA_LINK_HANDLE);
    }

    public static boolean enableNativeDownloader() {
        return Utils.getBooleanPref(Settings.ENABLE_NATIVE_DOWNLOADER);
    }

    public static boolean enableInlineDownloadButton() {
        return Utils.getBooleanPref(Settings.ENABLE_INLINE_DOWNLOAD_BUTTON);
    }

    public static boolean enableNativeTranslator() {
        return Utils.getBooleanPref(Settings.ENABLE_NATIVE_TRANSLATOR);
    }

    public static boolean enableNativeReaderMode() {
        return Utils.getBooleanPref(Settings.ENABLE_NATIVE_READER_MODE);
    }

    public static boolean enableShareImage() {
        return Utils.getBooleanPref(Settings.ENABLE_SHARE_IMAGE);
    }

    public static boolean enableBrowseObject() {
        return Utils.getBooleanPref(Settings.ENABLE_BROWSE_OBJECT);
    }

    public static boolean deleteFromDb() {
        return Utils.getBooleanPref(Settings.DELETE_FROM_DB);
    }

    public static boolean clearTrackingParams() {
        return Utils.getBooleanPref(Settings.CLEAR_TRACKING_PARAMS);
    }

    public static boolean unshortenLink() {
        return Utils.getBooleanPref(Settings.UNSHORTEN_LINK);
    }

    public static boolean enableCustomSharingDomain() {
        return Utils.getBooleanPref(Settings.ENABLE_CUSTOM_SHARING_DOMAIN);
    }

    public static String getCustomSharingDomain() {
        return Utils.getStringPref(Settings.CUSTOM_SHARING_DOMAIN);
    }

    public static boolean hideNativeReaderPostTextOnlyMode() {
        return Utils.getBooleanPref(Settings.NATIVE_READER_MODE_TEXT_ONLY);
    }

    public static boolean hideNativeReaderHideQuotedPosts() {
        return Utils.getBooleanPref(Settings.NATIVE_READER_MODE_HIDE_QUOTED);
    }

    public static boolean hideNativeReaderNoGrok() {
        return Utils.getBooleanPref(Settings.NATIVE_READER_MODE_NO_GROK);
    }

    public static String getNativeReaderTheme() {
        return Utils.getStringPref(Settings.NATIVE_READER_MODE_THEME);
    }

    public static String getNativeTranslatorType() {
        return Utils.getStringPref(Settings.NATIVE_TRANSLATOR_TYPE);
    }

    public static boolean enableFontMod() {
        return Utils.getBooleanPref(Settings.ENABLE_FONT_MOD);
    }

    public static String getCustomFontPath() {
        return Utils.getStringPref(Settings.CUSTOM_FONT_PATH);
    }

    public static String getCustomEmojiFontPath() {
        return Utils.getStringPref(Settings.CUSTOM_EMOJI_FONT_PATH);
    }

    public static String getCustomNavbarTabs() {
        return Utils.getStringPref(Settings.CUSTOM_NAVBAR_TABS);
    }

    public static String getCustomSidebarTabs() {
        return Utils.getStringPref(Settings.CUSTOM_SIDEBAR_TABS);
    }

    public static String getCustomProfileTabs() {
        return Utils.getStringPref(Settings.CUSTOM_PROFILE_TABS);
    }

    public static String getCustomTimelineTabs() {
        return Utils.getStringPref(Settings.CUSTOM_TIMELINE_TABS);
    }

    public static String getCustomExploreTabs() {
        return Utils.getStringPref(Settings.CUSTOM_EXPLORE_TABS);
    }

    public static String getCustomSearchTabs() {
        return Utils.getStringPref(Settings.CUSTOM_SEARCH_TABS);
    }

    public static String getCustomNotificationTabs() {
        return Utils.getStringPref(Settings.CUSTOM_NOTIFICATION_TABS);
    }

    public static String getCustomInlineTabs() {
        return Utils.getStringPref(Settings.CUSTOM_INLINE_TABS);
    }

    public static String getCustomSearchTypeAhead() {
        return Utils.getStringPref(Settings.CUSTOM_SEARCH_TYPE_AHEAD);
    }

    public static String getDefaultReplySortFilter() {
        return Utils.getStringPref(Settings.DEFAULT_REPLY_SORT_FILTER);
    }

    public static boolean navbarFix() {
        return Utils.getBooleanPref(Settings.NAVBAR_FIX);
    }

    public static String getAppIcon() {
        return Utils.getStringPref(Settings.APP_ICON);
    }

    public static boolean removeSearchSuggestions() {
        return Utils.getBooleanPref(Settings.REMOVE_SEARCH_SUGGESTIONS);
    }

    public static boolean pauseSearchSuggestions() {
        return Utils.getBooleanPref(Settings.PAUSE_SEARCH_SUGGESTIONS);
    }

    public static boolean enableFeatureFlags() {
        return Utils.getBooleanPref(Settings.ENABLE_FEATURE_FLAGS);
    }

    public static boolean hideFAB() {
        return Utils.getBooleanPref(Settings.HIDE_FAB);
    }

    public static boolean hideFABBtns() {
        return Utils.getBooleanPref(Settings.HIDE_FAB_BTNS);
    }

    public static boolean hideViewCount() {
        return Utils.getBooleanPref(Settings.HIDE_VIEW_COUNT);
    }
}
