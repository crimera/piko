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

import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.widgets.*;

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

    public void backupAndRestoreSection() {

        PreferenceCategory category = category = addCategory(Strings.BACKUP_AND_RESTORE_TITLE);

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
