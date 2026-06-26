/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.settings.preference;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import java.util.TreeMap;
import java.util.Map;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.constants.Constants;

public class ScreenBuilder {
    private final Context context;
    private final PreferenceScreen screen;
    private final Helper helper;

    public ScreenBuilder(Context context, PreferenceScreen screen, Helper helper) {
        this.context = context;
        this.screen = screen;
        this.helper = helper;
    }

    private void addPreference(Preference pref) {
        addPreference(null,pref);
    }

    // Adding preference category might be usedin the future
    // to segregate the preference inside the fragment
    private void addPreference(PreferenceCategory category,  Preference pref) {
        if (category != null) {
            category.addPreference(pref);
        } else {
            screen.addPreference(pref);
        }
    }

    private PreferenceCategory addCategory(String title) {
        CategoryPref preferenceCategory = new CategoryPref(context);
        preferenceCategory.setFirstCategory(screen.getPreferenceCount() == 0);
        preferenceCategory.setTitle(title);
        screen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    public void buildAdsSection() {
        if (!(SettingsStatus.adsSection())) return;

        if (SettingsStatus.disableAds) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_ads"),
                            "",
                            Settings.DISABLE_ADS
                    )
            );
        }
        if (SettingsStatus.hideSuggestedContent) {
            addPreference(
                    helper.switchPreference(
                            str("piko_hide_suggested_content"),
                            str("piko_hide_suggested_content_desc"),
                            Settings.HIDE_SUGGESTED_CONTENT
                    )
            );
        }
    }

    public void buildDeveloperSection() {
        if (!(SettingsStatus.developerOptionsSection())) return;

        // PreferenceCategory category= addCategory(str("piko_category_dev_options"));

        if (SettingsStatus.removeBuildExpirePopup) {
            addPreference(
                    helper.switchPreference(
                            str("piko_remove_build_expire_popup"),
                            str("piko_remove_build_expire_popup_desc"),
                            Settings.REMOVE_BUILD_EXPIRE_POPUP
                    )
            );
        }
        if (SettingsStatus.unlockEmployeeOptions) {
            addPreference(
                    helper.switchPreference(
                            str("piko_enable_emp_options"),
                            str("piko_enable_emp_options_desc"),
                            Settings.ENABLE_EMP_OPTIONS
                    )
            );
        }
        if (SettingsStatus.allowUserNetworkCertificate) {
            addPreference(
                    helper.switchPreference(
                            str("piko_allow_user_network_certificate"),
                            str("piko_allow_user_network_certificate_desc"),
                            Settings.ALLOW_USER_NETWORK_CERTIFICATE
                    )
            );
        }
        if (SettingsStatus.enableDeveloperOptions) {
            addPreference(
                    helper.switchPreference(
                            str("piko_enable_dev_options"),
                            str("piko_enable_dev_options_desc"),
                            Settings.DEVELOPER_OPTIONS
                    )
            );
            addPreference(
                    helper.switchPreference(
                            str("piko_directly_open_metaconfig"),
                            str("piko_directly_open_metaconfig_desc"),
                            Settings.DIRECTLY_OPEN_METACONFIG
                    )
            );
            addPreference(
                    helper.buttonPreference(
                            str("piko_export_dev_overrides"),
                            "",
                            "piko_export_dev_overrides"
                    )
            );
            addPreference(
                    helper.buttonPreference(
                            str("piko_import_dev_overrides"),
                            "",
                            "piko_import_dev_overrides"
                    )
            );
            addPreference(
                    helper.buttonPreference(
                            str("piko_import_id_mapping"),
                            "",
                            "piko_import_id_mapping"
                    )
            );
            addPreference(
                    helper.buttonPreference(
                            str("piko_download_id_mapping"),
                            "",
                            "piko_download_id_mapping"
                    )
            );

            if(Pref.pikoDebug()) {
                addPreference(
                        helper.buttonPreference(
                                str("piko_export_experiment_list"),
                                "",
                                "piko_export_experiment_list"
                        )
                );

                addPreference(
                        helper.buttonPreference(
                                str("piko_export_experiment_mappings"),
                                "",
                                "piko_export_experiment_mappings"
                        )
                );
            }
        }
    }

    public void ghostSection() {
        if (!(SettingsStatus.ghostSection())) return;

        // PreferenceCategory category= addCategory(str("piko_category_ghost"));

        addPreference(
                helper.switchPreference(
                        str("piko_turn_on_all_ghost_modes"),
                        "",
                        Settings.TURN_ON_ALL_GHOST_MODES
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_ghost_modes_quick_toggle"),
                        str("piko_ghost_modes_quick_toggle_desc"),
                        Settings.GHOST_MODES_QUICK_TOGGLE
                )
        );

        if (SettingsStatus.viewStoriesAnonymously) {
            addPreference(
                    helper.switchPreference(
                            str("piko_view_stories_anonymously"),
                            "",
                            Settings.VIEW_STORIES_ANONYMOUSLY
                    )
            );
        }
        if (SettingsStatus.viewLiveAnonymously) {
            addPreference(
                    helper.switchPreference(
                            str("piko_view_live_anonymously"),
                            "",
                            Settings.VIEW_LIVE_ANONYMOUSLY
                    )
            );
        }
        if (SettingsStatus.disableTypingStatus) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_typing_status"),
                            "",
                            Settings.DISABLE_TYPING_STATUS
                    )
            );
        }
        if (SettingsStatus.disableScreenshotDetection) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_screenshot_detection"),
                            "",
                            Settings.DISABLE_SCREENSHOT_DETECTION
                    )
            );
        }
        if (SettingsStatus.viewDmAnonymously) {
            addPreference(
                    helper.switchPreference(
                            str("piko_view_dm_anonymously"),
                            "",
                            Settings.VIEW_DM_ANONYMOUSLY
                    )
            );
        }

    }

    public void linksSection() {
        if (!(SettingsStatus.linksSection())) return;

        // PreferenceCategory category= addCategory(str("piko_category_links"));
        if (SettingsStatus.openLinksExternally) {
            addPreference(
                    helper.switchPreference(
                            str("piko_open_links_externally"),
                            str("piko_open_links_externally_desc"),
                            Settings.OPEN_LINKS_EXTERNALLY
                    )
            );
        }
        if (SettingsStatus.sanitizeShareLinks) {
            addPreference(
                    helper.switchPreference(
                            str("piko_sanitize_share_links"),
                            "",
                            Settings.SANITIZE_SHARE_LINKS
                    )
            );
        }
    }

    public void distractionFreeSection() {
        if (!(SettingsStatus.distractionFreeSection())) return;

        // PreferenceCategory category= addCategory(str("piko_category_distraction_free"));

        if (SettingsStatus.disableStories) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_stories"),
                            "",
                            Settings.DISABLE_STORIES
                    )
            );
        }
        if (SettingsStatus.hideStoriesTray) {
            addPreference(
                    helper.switchPreference(
                            str("piko_hide_stories_tray"),
                            str("piko_hide_stories_tray_desc"),
                            Settings.HIDE_STORIES_TRAY
                    )
            );
        }
        if (SettingsStatus.disableHighlights) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_highlights"),
                            "",
                            Settings.DISABLE_HIGHLIGHTS
                    )
            );
        }
        if (SettingsStatus.hideNotesTray) {
            addPreference(
                    helper.switchPreference(
                            str("piko_hide_notes_tray"),
                            str("piko_hide_notes_tray_desc"),
                            Settings.HIDE_NOTES_TRAY
                    )
            );
        }
        if (SettingsStatus.disableExplore) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_explore"),
                            "",
                            Settings.DISABLE_EXPLORE
                    )
            );
        }
        if (SettingsStatus.disableComments) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_comments"),
                            "",
                            Settings.DISABLE_COMMENTS
                    )
            );
        }
        if (SettingsStatus.limitFollowingFeed) {
            addPreference(
                    helper.switchPreference(
                            str("piko_limit_following_feed"),
                            str("piko_limit_following_feed_desc"),
                            Settings.LIMIT_FOLLOWING_FEED
                    )
            );
        }
        if (SettingsStatus.disableReelsScrolling) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_reels_scrolling"),
                            str("piko_disable_reels_scrolling_desc"),
                            Settings.DISABLE_REELS_SCROLLING
                    )
            );
        }
        if (SettingsStatus.hideGroupCreationOnSharesheet) {
            addPreference(
                    helper.switchPreference(
                            str("piko_hide_group_creation_button_on_sharesheet"),
                            "",
                            Settings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET
                    )
            );
        }

        if (SettingsStatus.disableDoubleTapLike) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_double_tap_like_post"),
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_POST
                    )
            );

            addPreference(
                    helper.switchPreference(
                            str("piko_disable_double_tap_like_reel"),
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_REEL
                    )
            );

            addPreference(
                    helper.switchPreference(
                            str("piko_disable_double_tap_like_comment"),
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_COMMENT
                    )
            );

            addPreference(
                    helper.switchPreference(
                            str("piko_disable_double_tap_like_message"),
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_MESSAGE
                    )
            );
        }
    }

    public void buildMiscSection() {
        if (!(SettingsStatus.miscSection())) return;

        // PreferenceCategory category= addCategory(str("piko_category_misc"));
        if (SettingsStatus.unlockPlusBenefits) {
            addPreference(
                    helper.switchPreference(
                            str("piko_unlock_plus_benefits"),
                            str("piko_unlock_plus_benefits_desc"),
                            Settings.UNLOCK_PLUS_BENEFITS
                    )
            );
        }
        if (SettingsStatus.changeLikeAnimation) {
            addPreference(
                    helper.listPreference(
                            str("piko_change_like_animation"),
                            str("piko_change_like_animation_desc"),
                            Settings.CHANGE_LIKE_ANIMATION
                    )
            );
        }
        if (SettingsStatus.disableAnalytics) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_analytics"),
                            str("piko_disable_analytics_desc"),
                            Settings.DISABLE_ANALYTICS
                    )
            );
            addPreference(
                    helper.buttonPreference(
                            str("piko_delete_analytics_cache"),
                            "",
                            "piko_delete_analytics_cache"
                    )
            );
        }
        if (SettingsStatus.moreOptionsOnProfile) {
            addPreference(
                    helper.switchPreference(
                            str("piko_more_profile_options_action_bar_toggle"),
                            str("piko_more_profile_options_action_bar_toggle_desc"),
                            Settings.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE
                    )
            );
        }
        if (SettingsStatus.moreOptionsOnPost) {
            addPreference(
                    helper.switchPreference(
                            str("piko_enable_more_options_on_post"),
                            str("piko_enable_more_options_on_post_desc"),
                            Settings.ENABLE_MORE_OPTIONS_ON_POST
                    )
            );
        }
        if (SettingsStatus.disableVideoAutoplay) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_video_autoplay"),
                            "",
                            Settings.DISABLE_VIDEO_AUTOPLAY
                    )
            );
        }
        if (SettingsStatus.storiesAudioAutoplay) {
            addPreference(
                    helper.switchPreference(
                            str("piko_stories_audio_autoplay"),
                            "",
                            Settings.STORIES_AUDIO_AUTOPLAY
                    )
            );
        }
        if (SettingsStatus.disableDiscoverPeople) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_discover_people"),
                            str("piko_disable_discover_people_desc"),
                            Settings.DISABLE_DISCOVER_PEOPLE
                    )
            );
        }
        if (SettingsStatus.followBackIndicator) {
            addPreference(
                    helper.switchPreference(
                            str("piko_follow_back_indicator"),
                            str("piko_follow_back_indicator_desc"),
                            Settings.FOLLOW_BACK_INDICATOR
                    )
            );
        }
        if (SettingsStatus.viewStoryMentions) {
            addPreference(
                    helper.switchPreference(
                            str("piko_view_story_mentions"),
                            "",
                            Settings.VIEW_STORY_MENTIONS
                    )
            );
        }
        if (SettingsStatus.disableStoryFlipping) {
            addPreference(
                    helper.switchPreference(
                            str("piko_disable_story_flipping"),
                            str("piko_disable_story_flipping_desc"),
                            Settings.DISABLE_STORY_FLIPPING
                    )
            );
        }

        if (SettingsStatus.customiseStoryTimestamp) {
            addPreference(
                    helper.listPreference(
                            str("piko_customise_story_timestamp"),
                            str("piko_customise_story_timestamp_desc"),
                            Settings.CUSTOMISE_STORY_TIMESTAMP
                    )
            );
        }
        if(SettingsStatus.customiseStoryRingSize) {
            addPreference(
                    helper.editTextNumPreference(
                            str("piko_customise_story_ring_size"),
                            str("piko_customise_story_ring_size_desc"),
                            Settings.CUSTOMISE_STORY_RING_SIZE
                    ));
        }

        if (SettingsStatus.unlimitedReplaysOnEphemeralMedia) {
            addPreference(
                    helper.switchPreference(
                            str("piko_unlimited_replays"),
                            str("piko_unlimited_replays_desc"),
                            Settings.UNLIMITED_REPLAYS
                    )
            );
        }

        if (SettingsStatus.improveImageViewing) {
            addPreference(
                    helper.switchPreference(
                            str("piko_improve_image_viewing"),
                            str("piko_improve_image_viewing_desc"),
                            Settings.IMPROVE_IMAGE_VIEWING
                    )
            );
        }

        if (SettingsStatus.hideReshareButton) {
            addPreference(
                    helper.switchPreference(
                            str("piko_hide_reshare_button"),
                            "",
                            Settings.HIDE_RESHARE_BUTTON
                    )
            );
        }
        if (SettingsStatus.copyCommentButton) {
            addPreference(
                    helper.switchPreference(
                            str("piko_copy_comment"),
                            str("piko_copy_comment_desc"),
                            Settings.COMMENT_COPY_BUTTON
                    )
            );
        }
        if (SettingsStatus.saveMediaCommentButton) {
            addPreference(
                    helper.switchPreference(
                            str("piko_save_media_comment"),
                            str("piko_save_media_comment_desc"),
                            Settings.COMMENT_SAVE_MEDIA_BUTTON
                    )
            );
        }
        if (SettingsStatus.removeEmptyBottomSpace) {
            addPreference(
                    helper.switchPreference(
                            str("piko_remove_empty_bottom_space"),
                            "",
                            Settings.REMOVE_EMPTY_BOTTOM_SPACE
                    )
            );
        }
    }

    public void buildDownloadSection() {
        if (!SettingsStatus.downloadSection()) return;

        // PreferenceCategory category= addCategory(str("piko_category_download_media"));

        addPreference(
                helper.switchPreference(
                        str("piko_enable_download"),
                        "",
                        Settings.ENABLE_DOWNLOAD
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_enable_direct_download"),
                        str("piko_enable_direct_download_desc"),
                        Settings.ENABLE_DIRECT_DOWNLOAD
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_download_username_folder"),
                        str("piko_download_username_folder_desc"),
                        Settings.DOWNLOAD_USERNAME_FOLDER
                )
        );

        addPreference(
                helper.buttonPreference(
                        str("piko_download_set_path"),
                        Pref.getCustomDownloadPath(),
                        "piko_download_set_path"
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_download_with_external_downloader"),
                        "",
                        Settings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER
                )
        );

        addPreference(
                helper.editTextPreference(
                        str("piko_external_downloader_package_name"),
                        Pref.externalDownloaderPackageName(),
                        Settings.EXTERNAL_DOWNLOADER_PACKAGE_NAME
                )
        );
    }

    public void buildNavigationSection() {
        if (!(SettingsStatus.hideNavigationButtons)) return;

        //  PreferenceCategory category = addCategory(str("piko_category_hide_navigation_buttons"));

        addPreference(
                helper.switchPreference(
                        str("piko_hide_navigation_feed"),
                        "",
                        Settings.HIDE_NAVIGATION_FEED
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_hide_navigation_reels"),
                        "",
                        Settings.HIDE_NAVIGATION_REELS
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_hide_navigation_direct"),
                        "",
                        Settings.HIDE_NAVIGATION_DIRECT
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_hide_navigation_search"),
                        "",
                        Settings.HIDE_NAVIGATION_SEARCH
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_hide_navigation_create"),
                        "",
                        Settings.HIDE_NAVIGATION_CREATE
                )
        );
    }

    public void aboutSection(TreeMap<String, Boolean> flags) {

        String appVersionText = String.format(str("piko_app_version"), Utils.getAppVersionName());
        String patchVersionText = String.format(str("piko_patch_version"), Utils.getPatchesReleaseVersion());

        addPreference(
                helper.buttonPreference(
                        appVersionText,
                        "",
                        appVersionText
                )
        );

        addPreference(
                helper.buttonPreference(
                        patchVersionText,
                        "",
                        patchVersionText
                )
        );

        addPreference(
                helper.buttonPreference(
                        str("piko_export_pref"),
                        "",
                        "piko_export_pref"
                )
        );

        addPreference(
                helper.buttonPreference(
                        str("piko_import_pref"),
                        "",
                        "piko_import_pref"
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_settings_on_action_bar"),
                        str("piko_settings_on_action_bar_desc"),
                        Settings.PIKO_SETTINGS_ON_ACTION_BAR
                )
        );

        addPreference(
                helper.switchPreference(
                        str("piko_debug"),
                        str("piko_debug_desc"),
                        Settings.PIKO_DEBUG
                )
        );

        PreferenceCategory category = addCategory(str("piko_patch_info_title"));
        String enabledStr = str("piko_patch_enabled");
        String disabledStr = str("piko_patch_disabled");

        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            String resName = entry.getKey();
            boolean sts = (boolean) entry.getValue();
            String status = sts ? enabledStr : disabledStr;

            addPreference(category,
                    helper.buttonPreference(
                            resName,
                            status,
                            resName
                    )
            );
        }

    }

    public void buildSettingsPage() {
        if (SettingsStatus.adsSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_ads"),
                            "",
                            Constants.PIKO_FRAGMENT_ADS
                    )
            );
        }

        if (SettingsStatus.ghostSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_ghost"),
                            "",
                            Constants.PIKO_FRAGMENT_GHOST
                    )
            );
        }

        if (SettingsStatus.linksSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_links"),
                            "",
                            Constants.PIKO_FRAGMENT_LINKS
                    )
            );
        }

        if (SettingsStatus.distractionFreeSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_distraction_free"),
                            "",
                            Constants.PIKO_FRAGMENT_DISTRACTION_FREE
                    )
            );
        }

        if (SettingsStatus.miscSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_misc"),
                            "",
                            Constants.PIKO_FRAGMENT_MISC
                    )
            );
        }

        if (SettingsStatus.downloadSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_download_media"),
                            "",
                            Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA
                    )
            );
        }

        if (SettingsStatus.hideNavigationButtons){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_hide_navigation_buttons"),
                            "",
                            Constants.PIKO_FRAGMENT_NAV_BTNS
                    )
            );
        }

        if (SettingsStatus.developerOptionsSection()){
            addPreference(
                    helper.buttonPreference(
                            str("piko_category_dev_options"),
                            "",
                            Constants.PIKO_FRAGMENT_DEV_OPTIONS
                    )
            );
        }


        addPreference(
                helper.buttonPreference(
                        str("piko_category_about"),
                        "",
                        Constants.PIKO_FRAGMENT_ABOUT
                )
        );

    }

    //end
}