/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.settings.preference;


import android.content.Context;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.*;
import app.morphe.extension.shared.Utils;

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
        PreferenceCategory preferenceCategory = new PreferenceCategory(context);
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
        if (SettingsStatus.enableDeveloperOptions) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.ENABLE_DEV_OPTIONS,
                            "",
                            Settings.DEVELOPER_OPTIONS
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
        }
    }

    public void ghostSection() {
        if (!(SettingsStatus.ghostSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_GHOST);

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
        if (SettingsStatus.hideGroupCreationOnSharesheet) {
            addPreference(category,
                    helper.switchPreference(
                            Strings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET,
                            "",
                            Settings.HIDE_GROUP_CREATION_BUTTON_ON_SHARESHEET
                    )
            );
        }
    }

    public void buildMiscSection() {
        if (!(SettingsStatus.miscSection())) return;

        PreferenceCategory category = category = addCategory(Strings.CATEGORY_MISC);
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
    }

    public void buildDownloadSection() {
        if (!(SettingsStatus.downloadMedia)) return;

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

        addPreference(category,
                helper.switchPreference(
                        Strings.HIDE_NAVIGATION_PROFILE,
                        "",
                        Settings.HIDE_NAVIGATION_PROFILE
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
    }

    //end
}
