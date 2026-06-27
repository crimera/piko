/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.constants;

import static app.morphe.extension.instagram.utils.IgStr.str;

import app.morphe.extension.crimera.constants.ExtensionStrings;

public class Constants {
    public static final String PIKO = "piko";
    public static final String PIKO_SETTINGS = PIKO + "_settings";
    public static final String SHARED_PREF_NAME = PIKO_SETTINGS;
    public static final String DEFAULT_PIKO_FOLDER = "Piko-Instagram";
    public static final String DEFAULT_AUDIO_FOLDER = "Audio";
    public static final String DEFAULT_DM_FOLDER = "DM";
    public static final String DEFAULT_GIF_FOLDER = "Gif";
    public static final String INSTAGRAM_SHARE_LINK = "https://www.instagram.com/%s/%s/";
    // https://www.instagram.com/p/<short code>/
    // https://www.instagram.com/reel/<short code>/
    // https://www.instagram.com/stories/<user name>/<post id>

    public static final String PIKO_FRAGMENT_TITLE = "piko_frag_title";
    public static final String PIKO_FRAGMENT_NAME = "piko_frag_name";

    public static final String PIKO_FRAGMENT_SETTINGS = "piko_frag_settings";
    public static final String PIKO_FRAGMENT_ADS = "piko_frag_ads";
    public static final String PIKO_FRAGMENT_GHOST = "piko_frag_ghost";
    public static final String PIKO_FRAGMENT_LINKS = "piko_frag_links";
    public static final String PIKO_FRAGMENT_DISTRACTION_FREE = "piko_frag_distraction_free";
    public static final String PIKO_FRAGMENT_MISC = "piko_frag_misc";
    public static final String PIKO_FRAGMENT_DOWNLOAD_MEDIA = "piko_frag_download_media";
    public static final String PIKO_FRAGMENT_NAV_BTNS = "piko_frag_nav_btns";
    public static final String PIKO_FRAGMENT_DEV_OPTIONS = "piko_frag_dev_options";
    public static final String PIKO_FRAGMENT_ABOUT = "piko_frag_about";

    public static void load() {
        ExtensionStrings.setDefaultPikoFolder(Constants.DEFAULT_PIKO_FOLDER);
        ExtensionStrings.setDownloadOngoing(str("piko_downloading_media"));
        ExtensionStrings.setDownloadCompleted(str("piko_downloaded_media"));
        ExtensionStrings.setDownloadError(str("piko_download_failed_media"));
        ExtensionStrings.setDownloadMediaExists(str("piko_media_exists"));
        ExtensionStrings.setDownloadSetPathFailed(str("piko_download_set_path_failed"));
        ExtensionStrings.setDownloadSetPathSuccess(str("piko_download_set_path_success"));
        ExtensionStrings.setDownloadGrantPermission(str("piko_download_grant_permission"));
        ExtensionStrings.setDownloadGrantPermissionFailed(str("piko_download_grant_permission_failed"));
    }
}
