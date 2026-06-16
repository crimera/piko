/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.settings.preference;


import android.content.Context;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.PreferenceCategory;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.utils.Pref;

public class ScreenBuilder {
    private final Context context;
    private final PreferenceScreen screen;
    private final Helper helper;

    public ScreenBuilder(Context context, PreferenceScreen screen, Helper helper) {
        this.context = context;
        this.screen = screen;
        this.helper = helper;
    }

    private void addPreference(PreferenceCategory category, Preference pref) {
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

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_ADS);
        if (SettingsStatus.disableAds) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_ADS,
                            "",
                            Settings.DISABLE_ADS
                    )
            );
        }
        if (SettingsStatus.hideSuggestedContent) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_SUGEESTED_CONTENT,
                            Strings.HIDE_SUGEESTED_CONTENT_DESC,
                            Settings.HIDE_SUGGESTED_CONTENT
                    )
            );
        }
    }

    public void buildDeveloperSection() {
        if (!(SettingsStatus.developerOptionsSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_DEV_OPTIONS);

        if (SettingsStatus.removeBuildExpirePopup) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.REMOVE_BUILD_EXPIRE_POPUP,
                            Strings.REMOVE_BUILD_EXPIRE_POPUP_DESC,
                            Settings.REMOVE_BUILD_EXPIRE_POPUP
                    )
            );
        }
        if (SettingsStatus.unlockEmployeeOptions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ENABLE_EMP_OPTIONS,
                            Strings.ENABLE_EMP_OPTIONS_DESC,
                            Settings.ENABLE_EMP_OPTIONS
                    )
            );
        }
        if (SettingsStatus.unlockEmployeeOptions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ALLOW_USER_NETWORK_CERTIFICATE,
                            Strings.ALLOW_USER_NETWORK_CERTIFICATE_DESC,
                            Settings.ALLOW_USER_NETWORK_CERTIFICATE
                    )
            );
        }
        if (SettingsStatus.enableDeveloperOptions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ENABLE_DEV_OPTIONS,
                            Strings.ENABLE_DEV_OPTIONS_DESC,
                            Settings.DEVELOPER_OPTIONS
                    )
            );
            addPreference(category,
                    helper.switchPreference(
                            Strings.DIRECTLY_OPEN_METACONFIG,
                            Strings.DIRECTLY_OPEN_METACONFIG_DESC,
                            Settings.DIRECTLY_OPEN_METACONFIG
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            Strings.EXPORT_DEV_OVERRIDES,
                            "",
                            Strings.EXPORT_DEV_OVERRIDES
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            Strings.IMPORT_DEV_OVERRIDES,
                            "",
                            Strings.IMPORT_DEV_OVERRIDES
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            Strings.IMPORT_ID_MAPPING,
                            "",
                            Strings.IMPORT_ID_MAPPING
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            Strings.DOWNLOAD_ID_MAPPING,
                            "",
                            Strings.DOWNLOAD_ID_MAPPING
                    )
            );
        }
    }

    public void ghostSection() {
        if (!(SettingsStatus.ghostSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_GHOST);

        addPreference(category,
                helper.switchPreference(
                        Strings.TURN_ON_ALL_GHOST_MODES,
                        "",
                        Settings.TURN_ON_ALL_GHOST_MODES
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.GHOST_MODES_QUICK_TOGGLE,
                        Strings.GHOST_MODES_QUICK_TOGGLE_DESC,
                        Settings.GHOST_MODES_QUICK_TOGGLE
                )
        );

        if (SettingsStatus.viewStoriesAnonymously) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_STORIES_ANONYMOUSLY,
                            "",
                            Settings.VIEW_STORIES_ANONYMOUSLY
                    )
            );
        }
        if (SettingsStatus.viewLiveAnonymously) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_LIVE_ANONYMOUSLY,
                            "",
                            Settings.VIEW_LIVE_ANONYMOUSLY
                    )
            );
        }
        if (SettingsStatus.disableTypingStatus) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_TYPING_STATUS,
                            "",
                            Settings.DISABLE_TYPING_STATUS
                    )
            );
        }
        if (SettingsStatus.disableScreenshotDetection) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_SCREENSHOT_DETECTION,
                            "",
                            Settings.DISABLE_SCREENSHOT_DETECTION
                    )
            );
        }
        if (SettingsStatus.viewDmAnonymously) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_DM_ANONYMOUSLY,
                            "",
                            Settings.VIEW_DM_ANONYMOUSLY
                    )
            );
        }

    }

    public void linksSection() {
        if (!(SettingsStatus.linksSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_LINKS);
        if (SettingsStatus.openLinksExternally) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.OPEN_LINKS_EXTERNALLY,
                            Strings.OPEN_LINKS_EXTERNALLY_DESC,
                            Settings.OPEN_LINKS_EXTERNALLY
                    )
            );
        }
        if (SettingsStatus.sanitizeShareLinks) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.SANITIZE_SHARE_LINKS,
                            "",
                            Settings.SANITIZE_SHARE_LINKS
                    )
            );
        }
    }

    public void distractionFreeSection() {
        if (!(SettingsStatus.distractionFreeSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_DISTRACTION_FREE);

        if (SettingsStatus.disableStories) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_STORIES,
                            "",
                            Settings.DISABLE_STORIES
                    )
            );
        }
        if (SettingsStatus.hideStoriesTray) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_STORIES_TRAY,
                            Strings.HIDE_STORIES_TRAY_DESC,
                            Settings.HIDE_STORIES_TRAY
                    )
            );
        }
        if (SettingsStatus.disableHighlights) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_HIGHLIGHTS,
                            "",
                            Settings.DISABLE_HIGHLIGHTS
                    )
            );
        }
        if (SettingsStatus.hideNotesTray) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_NOTES_TRAY,
                            Strings.HIDE_NOTES_TRAY_DESC,
                            Settings.HIDE_NOTES_TRAY
                    )
            );
        }
        if (SettingsStatus.disableExplore) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_EXPLORE,
                            "",
                            Settings.DISABLE_EXPLORE
                    )
            );
        }
        if (SettingsStatus.disableComments) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_COMMENTS,
                            "",
                            Settings.DISABLE_COMMENTS
                    )
            );
        }
        if (SettingsStatus.limitFollowingFeed) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.LIMIT_FOLLOWING_FEED,
                            Strings.LIMIT_FOLLOWING_FEED_DESC,
                            Settings.LIMIT_FOLLOWING_FEED
                    )
            );
        }
        if (SettingsStatus.disableReelsScrolling) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_REELS_SCROLLING,
                            Strings.DISABLE_REELS_SCROLLING_DESC,
                            Settings.DISABLE_REELS_SCROLLING
                    )
            );
        }
        if (SettingsStatus.hideGroupCreationOnSharesheet) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET,
                            "",
                            Settings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET
                    )
            );
        }

        if (SettingsStatus.disableDoubleTapLike) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DOUBLE_TAP_LIKE_POST,
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_POST
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DOUBLE_TAP_LIKE_REEL,
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_REEL
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DOUBLE_TAP_LIKE_COMMENT,
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_COMMENT
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DOUBLE_TAP_LIKE_MESSAGE,
                            "",
                            Settings.DISABLE_DOUBLE_TAP_LIKE_MESSAGE
                    )
            );
        }
    }

    public void buildMiscSection() {
        if (!(SettingsStatus.miscSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_MISC);
        if (SettingsStatus.unlockPlusBenefits) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.UNLOCK_PLUS_BENEFITS,
                            Strings.UNLOCK_PLUS_BENEFITS_DESC,
                            Settings.UNLOCK_PLUS_BENEFITS
                    )
            );
        }
        if (SettingsStatus.changeLikeAnimation) {
            addPreference(category,
                    helper.listPreference(
                            Strings.CHANGE_LIKE_ANIMATION,
                            Strings.CHANGE_LIKE_ANIMATION_DESC,
                            Settings.CHANGE_LIKE_ANIMATION
                    )
            );
        }
        if (SettingsStatus.disableAnalytics) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_ANALYTICS,
                            Strings.DISABLE_ANALYTICS_DESC,
                            Settings.DISABLE_ANALYTICS
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            Strings.DELETE_ANALYTICS_CACHE,
                            "",
                            Strings.DELETE_ANALYTICS_CACHE
                    )
            );
        }
        if (SettingsStatus.moreOptionsOnProfile) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE,
                            Strings.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE_DESC,
                            Settings.MORE_PROFILE_OPTIONS_ACTION_BAR_TOGGLE
                    )
            );
        }
        if (SettingsStatus.moreOptionsOnPost) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ENABLE_MORE_OPTIONS_ON_POST,
                            Strings.ENABLE_MORE_OPTIONS_ON_POST_DESC,
                            Settings.ENABLE_MORE_OPTIONS_ON_POST
                    )
            );
        }
        if (SettingsStatus.disableVideoAutoplay) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_VIDEO_AUTOPLAY,
                            "",
                            Settings.DISABLE_VIDEO_AUTOPLAY
                    )
            );
        }
        if (SettingsStatus.storiesAudioAutoplay) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.STORIES_AUDIO_AUTOPLAY,
                            "",
                            Settings.STORIES_AUDIO_AUTOPLAY
                    )
            );
        }
        if (SettingsStatus.disableDiscoverPeople) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_DISCOVER_PEOPLE,
                            Strings.DISABLE_DISCOVER_PEOPLE_DESC,
                            Settings.DISABLE_DISCOVER_PEOPLE
                    )
            );
        }
        if (SettingsStatus.followBackIndicator) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.FOLLOW_BACK_INDICATOR,
                            "",
                            Settings.FOLLOW_BACK_INDICATOR
                    )
            );
        }
        if (SettingsStatus.viewStoryMentions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.VIEW_STORY_MENTIONS,
                            "",
                            Settings.VIEW_STORY_MENTIONS
                    )
            );
        }
        if (SettingsStatus.disableStoryFlipping) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.DISABLE_STORY_FLIPPING,
                            Strings.DISABLE_STORY_FLIPPING_DESC,
                            Settings.DISABLE_STORY_FLIPPING
                    )
            );
        }

        if (SettingsStatus.customiseStoryTimestamp) {
            addPreference(category,
                    helper.listPreference(
                            Strings.CUSTOMISE_STORY_TIMESTAMP,
                            Strings.CUSTOMISE_STORY_TIMESTAMP_DESC,
                            Settings.CUSTOMISE_STORY_TIMESTAMP
                    )
            );
        }
        if(SettingsStatus.customiseStoryRingSize) {
            addPreference(category,
                    helper.editTextNumPreference(
                            Strings.CUSTOMISE_STORY_RING_SIZE,
                            Strings.CUSTOMISE_STORY_RING_SIZE_DESC,
                            Settings.CUSTOMISE_STORY_RING_SIZE
                    ));
        }

        if (SettingsStatus.unlimitedReplaysOnEphemeralMedia) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.UNLIMITED_REPLAYS,
                            Strings.UNLIMITED_REPLAYS_DESC,
                            Settings.UNLIMITED_REPLAYS
                    )
            );
        }

        if (SettingsStatus.improveImageViewing) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.IMPROVE_IMAGE_VIEWING,
                            Strings.IMPROVE_IMAGE_VIEWING_DESC,
                            Settings.IMPROVE_IMAGE_VIEWING
                    )
            );
        }

        if (SettingsStatus.hideReshareButton) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_RESHARE_BUTTON,
                            "",
                            Settings.HIDE_RESHARE_BUTTON
                    )
            );
        }
        if (SettingsStatus.copyCommentButton) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.COPY_COMMENT,
                            Strings.COPY_COMMENT_DESC,
                            Settings.COMMENT_COPY_BUTTON
                    )
            );
        }
        if (SettingsStatus.saveMediaCommentButton) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.SAVE_MEDIA_COMMENT,
                            Strings.SAVE_MEDIA_COMMENT_DESC,
                            Settings.COMMENT_SAVE_MEDIA_BUTTON
                    )
            );
        }
        if (SettingsStatus.removeEmptyBottomSpace) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.REMOVE_EMPTY_BOTTOM_SPACE,
                            "",
                            Settings.REMOVE_EMPTY_BOTTOM_SPACE
                    )
            );
        }
    }

    public void buildDownloadSection() {
        if (!(SettingsStatus.downloadMedia || SettingsStatus.downloadWithExternalDownloader)) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_DOWNLOAD_MEDIA);

        addPreference(category,
                helper.switchPreference(
                        Strings.ENABLE_DOWNLOAD,
                        "",
                        Settings.ENABLE_DOWNLOAD
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.ENABLE_DIRECT_DOWNLOAD,
                        Strings.ENABLE_DIRECT_DOWNLOAD_DESC,
                        Settings.ENABLE_DIRECT_DOWNLOAD
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.DOWNLOAD_USERNAME_FOLDER,
                        Strings.DOWNLOAD_USERNAME_FOLDER_DESC,
                        Settings.DOWNLOAD_USERNAME_FOLDER
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        Strings.DOWNLOAD_SET_PATH,
                        Pref.getCustomDownloadPath(),
                        Strings.DOWNLOAD_SET_PATH
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER,
                        "",
                        Settings.DOWNLOAD_WITH_EXTERNAL_DOWNLOADER
                )
        );

        addPreference(category,
                helper.editTextPreference(
                        Strings.EXTERNAL_DOWNLOADER_PACKAGE_NAME,
                        Pref.externalDownloaderPackageName(),
                        Settings.EXTERNAL_DOWNLOADER_PACKAGE_NAME
                )
        );
    }

    public void buildNavigationSection() {
        if (!(SettingsStatus.hideNavigationButtons)) return;

        PreferenceCategory category = addCategory(Strings.CATEGORY_HIDE_NAVIGATION_BUTTONS);

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_FEED,
                        "",
                        Settings.HIDE_NAVIGATION_FEED
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_REELS,
                        "",
                        Settings.HIDE_NAVIGATION_REELS
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_DIRECT,
                        "",
                        Settings.HIDE_NAVIGATION_DIRECT
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_SEARCH,
                        "",
                        Settings.HIDE_NAVIGATION_SEARCH
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_CREATE,
                        "",
                        Settings.HIDE_NAVIGATION_CREATE
                )
        );
    }

    public void aboutSection() {

        PreferenceCategory category = category = addCategory(Strings.PATCH_INFO_TITLE);
        String appVersionText = String.format(Strings.APP_VERSION, Utils.getAppVersionName());
        String patchVersionText = String.format(Strings.PATCH_VERSION, Utils.getPatchesReleaseVersion());

        addPreference(category,
                helper.buttonPreference(
                        appVersionText,
                        "",
                        appVersionText
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        patchVersionText,
                        "",
                        patchVersionText
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        Strings.EXPORT_PIKO_PREF,
                        "",
                        Strings.EXPORT_PIKO_PREF
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        Strings.IMPORT_PIKO_PREF,
                        "",
                        Strings.IMPORT_PIKO_PREF
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.PIKO_SETTINGS_ON_ACTION_BAR,
                        Strings.PIKO_SETTINGS_ON_ACTION_BAR_DESC,
                        Settings.PIKO_SETTINGS_ON_ACTION_BAR
                )
        );

        addPreference(category,
                helper.switchPreference(
                        Strings.PIKO_DEBUG,
                        Strings.PIKO_DEBUG_DESC,
                        Settings.PIKO_DEBUG
                )
        );

        if(Pref.pikoDebug()) {
            addPreference(category,
                    helper.buttonPreference(
                            Strings.PIKO_EXPORT_EXPERIMENT_LIST,
                            "",
                            Strings.PIKO_EXPORT_EXPERIMENT_LIST
                    )
            );

            addPreference(category,
                    helper.buttonPreference(
                            Strings.PIKO_EXPORT_EXPERIMENT_MAPPINGS,
                            "",
                            Strings.PIKO_EXPORT_EXPERIMENT_MAPPINGS
                    )
            );
        }
    }

    //end
}
