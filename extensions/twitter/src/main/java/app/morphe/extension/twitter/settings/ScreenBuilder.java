/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import android.text.TextUtils;

import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;

import java.util.List;

import app.morphe.extension.twitter.settings.SettingsSearchMatcher.SearchMatch;
import app.morphe.extension.twitter.settings.SettingsSearchMatcher.SearchResult;
import app.morphe.extension.twitter.settings.widgets.*;
import androidx.annotation.Nullable;
import app.morphe.extension.twitter.Pref;


public class ScreenBuilder {
    private static final SettingsSection[] SETTINGS_SECTIONS = new SettingsSection[]{
            new SettingsSection(
                    "piko_title_premium",
                    null,
                    Settings.PREMIUM_SECTION,
                    "ic_vector_twitter",
                    SettingsStatus::enablePremiumSection,
                    ScreenBuilder::buildPremiumSection
            ),
            new SettingsSection(
                    "piko_title_download",
                    null,
                    Settings.DOWNLOAD_SECTION,
                    "ic_vector_incoming",
                    SettingsStatus::enableDownloadSection,
                    ScreenBuilder::buildDownloadSection
            ),
            new SettingsSection(
                    "piko_title_feature_flags",
                    null,
                    Settings.FLAGS_SECTION,
                    "ic_vector_flag",
                    () -> SettingsStatus.featureFlagsEnabled,
                    ScreenBuilder::buildFeatureFlagsSection
            ),
            new SettingsSection(
                    "piko_title_ads",
                    null,
                    Settings.ADS_SECTION,
                    "ic_vector_accessibility_alt",
                    SettingsStatus::enableAdsSection,
                    ScreenBuilder::buildAdsSection
            ),
            new SettingsSection(
                    "piko_title_native",
                    null,
                    Settings.NATIVE_SECTION,
                    "ic_vector_flask_stroke",
                    SettingsStatus::enableNativeSection,
                    ScreenBuilder::buildNativeSection
            ),
            new SettingsSection(
                    "piko_title_misc",
                    null,
                    Settings.MISC_SECTION,
                    "ic_vector_heartline",
                    SettingsStatus::enableMiscSection,
                    ScreenBuilder::buildMiscSection
            ),
            new SettingsSection(
                    "piko_title_customisation",
                    null,
                    Settings.CUSTOMISE_SECTION,
                    "ic_vector_paintbrush_stroke",
                    SettingsStatus::enableCustomisationSection,
                    ScreenBuilder::buildCustomiseSection
            ),
            new SettingsSection(
                    "piko_title_font",
                    null,
                    Settings.FONT_SECTION,
                    "ic_vector_at",
                    SettingsStatus::fontSection,
                    ScreenBuilder::buildFontSection
            ),
            new SettingsSection(
                    "piko_title_timeline",
                    null,
                    Settings.TIMELINE_SECTION,
                    "ic_vector_timeline_stroke",
                    SettingsStatus::enableTimelineSection,
                    ScreenBuilder::buildTimelineSection
            ),
            new SettingsSection(
                    "piko_title_logging",
                    null,
                    Settings.LOGGING_SECTION,
                    "ic_vector_bug_stroke",
                    SettingsStatus::loggingSection,
                    ScreenBuilder::buildLoggingSection
            ),
            new SettingsSection(
                    "piko_title_backup",
                    null,
                    Settings.BACKUP_SECTION,
                    "ic_vector_layers_stroke",
                    () -> true,
                    ScreenBuilder::buildExportSection
            ),
            new SettingsSection(
                    "piko_title_about",
                    "piko_pref_patch_info",
                    Settings.PATCH_INFO,
                    "ic_vector_accessibility_circle",
                    () -> true,
                    ScreenBuilder::buildPikoSection
            )
    };
    private final Context context;
    private final Helper helper;
    private final SettingsSearchIndex searchIndex;
    private PreferenceBuildTarget preferenceTarget;

    public ScreenBuilder(Context context,PreferenceScreen screen,Helper helper){
        this.context = context;
        this.helper = helper;
        this.searchIndex = new SettingsSearchIndex();
        this.preferenceTarget = new PreferenceScreenBuildTarget(screen);
    }

    private void addPreference(Preference pref){
        preferenceTarget.addPreference(null, pref);
    }
    private void addPreference(@Nullable LegacyTwitterPreferenceCategory category,Preference pref){
        preferenceTarget.addPreference(category, pref);
    }

    public void buildPremiumSection(boolean buildCategory){

        if (!(SettingsStatus.enablePremiumSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_premium"));

        if (SettingsStatus.enableUndoPosts) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_undo_posts"),
                            str("piko_pref_undo_posts_desc"),
                            Settings.PREMIUM_UNDO_POSTS
                    )
            );

            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_undo_posts_btn"),
                            "",
                            Settings.PREMIUM_UNDO_POSTS.key
                    )
            );
        }

        if (SettingsStatus.enableForcePip) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_enable_force_pip"),
                            str("piko_pref_enable_force_pip_desc"),
                            Settings.PREMIUM_ENABLE_FORCE_PIP
                    )
            );
        }

        if (SettingsStatus.navBarCustomisation) {
            addPreference(category,
                    helper.buttonPreference(
                            str("tab_customization_screen_title"),
                            "",
                            Settings.PREMIUM_NAVBAR.key
                    )
            );
        }
        
    }

    public void buildDownloadSection(boolean buildCategory){
        if (!(SettingsStatus.enableDownloadSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_download"));
        if (SettingsStatus.changeDownloadEnabled) {
            addPreference(category,helper.listPreference(
                    str("piko_pref_download_path"),
                    str("piko_pref_download_path_desc"),
                    Settings.VID_PUBLIC_FOLDER
            ));
            addPreference(category,helper.editTextPreference(
                    str("piko_pref_download_folder"),
                    str("piko_pref_download_folder_desc"),
                    Settings.VID_SUBFOLDER
            ));
        }
        if (SettingsStatus.mediaLinkHandle) {
            addPreference(category,
                    helper.listPreference(
                            str("piko_pref_download_media_link_handle"),
                            "",
                            Settings.VID_MEDIA_HANDLE
                    )
            );
        }
        if (SettingsStatus.externalDownloader) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_external_downloader_toggle"),
                            "",
                            Settings.EXTERNAL_DOWNLOADER
                    )
            );

            addPreference(category,helper.editTextPreference(
                    str("piko_pref_external_downloader_package_name"),
                    "",
                    Settings.EXTERNAL_DOWNLOADER_PACKAGE_NAME
            ));
        }
    }

    public void buildAdsSection(boolean buildCategory){
        if (!(SettingsStatus.enableAdsSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_ads"));
        if (SettingsStatus.hideAds) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_promoted_posts"),
                            "",
                            Settings.ADS_HIDE_PROMOTED_POSTS
                    )
            );
        }

        if (SettingsStatus.hideWTF) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_wtf_section"),
                            "",
                            Settings.ADS_HIDE_WHO_TO_FOLLOW
                    )
            );
        }
        if (SettingsStatus.hideCTS) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_cts_section"),
                            "",
                            Settings.ADS_HIDE_CREATORS_TO_SUB
                    )
            );
        }

        if (SettingsStatus.hideCTJ) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_ctj_section"),
                            "",
                            Settings.ADS_HIDE_COMM_TO_JOIN
                    )
            );
        }

        if (SettingsStatus.hideRBMK) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_ryb_section"),
                            "",
                            Settings.ADS_HIDE_REVISIT_BMK
                    )
            );
        }

        if (SettingsStatus.hideRPinnedPosts) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_pinned_posts_section"),
                            "",
                            Settings.ADS_HIDE_REVISIT_PINNED_POSTS
                    )
            );
        }

        if (SettingsStatus.hideDetailedPosts) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_unrelated_replies"),
                            "",
                            Settings.ADS_HIDE_DETAILED_POSTS
                    )
            );
        }

        if (SettingsStatus.hidePremiumPrompt) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_premium_prompt"),
                            "",
                            Settings.ADS_HIDE_PREMIUM_PROMPT
                    )
            );
        }

        if (SettingsStatus.removePremiumUpsell) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_premium_upsell"),
                            str("piko_pref_hide_premium_upsell_desc"),
                            Settings.ADS_REMOVE_PREMIUM_UPSELL
                    )
            );
        }

        if (SettingsStatus.hideTopPeopleSearch) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_top_people_search"),
                            str("piko_pref_top_people_search_desc"),
                            Settings.ADS_HIDE_TOP_PEOPLE_SEARCH
                    )
            );
        }
        if (SettingsStatus.hideTodaysNews) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_todays_news"),
                            "",
                            Settings.ADS_REMOVE_TODAYS_NEW
                    )
            );
        }

        if (SettingsStatus.deleteFromDb) {
            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_del_from_db"),
                            "",
                            Settings.ADS_DEL_FROM_DB.key,
                            null,
                            "#DE0025"
                    )
            );
        }
    }

    public void buildNativeSection(boolean buildCategory){
        if (!(SettingsStatus.enableNativeSection())) return;

        if (!buildCategory) {
            Preference nativePageDescription = new Preference(context);
            nativePageDescription.setSummary(str("piko_pref_native_page_desc"));
            addPreference(nativePageDescription);
        }

        LegacyTwitterPreferenceCategory category = null;
        if (SettingsStatus.nativeDownloader) {
            category = preferenceCategory(str("piko_title_native_downloader"));
            addPreference(category,
                    helper.switchPreference(
                            str("piko_title_native_downloader_toggle"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_native_downloader_autodownload_highest_video_res"),
                            str("piko_pref_native_downloader_autodownload_highest_video_res_desc"),
                            Settings.VID_NATIVE_DOWNLOADER_AUTODOWNLOAD_HIGHEST_VIDEO_RES
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_native_downloader_show_download_icon"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER_SHOW_DOWNLOAD_ICON
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_native_downloader_show_copy_icon"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER_SHOW_COPY_ICON
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_native_downloader_show_variants_icon"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER_SHOW_VARIANTS_ICON
                    )
            );

            if (SettingsStatus.inlineDownloadButton) {
                addPreference(category,
                        helper.switchPreference(
                                str("piko_pref_native_downloader_inline_button"),
                                str("piko_pref_native_downloader_inline_button_desc"),
                                Settings.VID_INLINE_DOWNLOAD_BUTTON
                        )
                );
            }

            addPreference(category,helper.listPreference(
                    str("piko_pref_download_path"),
                    str("piko_pref_download_path_desc"),
                    Settings.VID_PUBLIC_FOLDER
            ));
            addPreference(category,helper.editTextPreference(
                    str("piko_pref_download_folder"),
                    str("piko_pref_download_folder_desc"),
                    Settings.VID_SUBFOLDER
            ));
            addPreference(category,
                    helper.listPreference(
                            str("piko_pref_native_downloader_filename_title"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER_FILENAME
                    )
            );
        }


        category = preferenceCategory(str("piko_title_native_translator"));

        if (SettingsStatus.nativeTranslator) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_native_translator_toggle"),
                            "",
                            Settings.NATIVE_TRANSLATOR
                    )
            );
            addPreference(category,
                    helper.listPreference(
                            str("piko_native_translator_provider"),
                            "",
                            Settings.NATIVE_TRANSLATOR_PROVIDERS
                    )
            );
            addPreference(category,
                    helper.listPreference(
                            str("piko_native_translator_to_lang"),
                            Pref.translatorLanguage(),
                            Settings.NATIVE_TRANSLATOR_LANG
                    )
            );

        }

        if (SettingsStatus.nativeReaderMode) {
            category = preferenceCategory(str("piko_title_native_reader_mode"));

            addPreference(category,
                    helper.switchPreference(
                            str("piko_native_reader_mode_toggle"),
                            "",
                            Settings.NATIVE_READER_MODE
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_native_reader_mode_pref_text_only_mode"),
                            "",
                            Settings.NATIVE_READER_MODE_TEXT_ONLY_MODE
                    )
            );
            addPreference(category,
                    helper.switchPreference(
                            str("piko_native_reader_mode_pref_hide_quoted_posts"),
                            "",
                            Settings.NATIVE_READER_MODE_HIDE_QUOTED_POST
                    )
            );
            addPreference(category,
                    helper.switchPreference (
                            str("piko_native_reader_mode_pref_no_grok"),
                            "",
                            Settings.NATIVE_READER_MODE_NO_GROK
                    )
            );
            addPreference(category,
                    helper.listPreference(
                            str("community_theme_settings_title"),
                            "",
                            Settings.NATIVE_READER_MODE_THEME
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            str("piko_native_reader_mode_cache_delete"),
                            "",
                            Settings.RESET_READER_MODE_CACHE
                    )
            );

        }

        if (SettingsStatus.shareImage) {
            category = preferenceCategory(str("piko_share_image_title"));

            addPreference(category,
                    helper.switchPreference(
                            str("piko_share_image_toggle"),
                            "",
                            Settings.SHARE_IMAGE_ENABLED
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_share_image_autocleanup"),
                            str("piko_share_image_autocleanup_desc"),
                            Settings.SHARE_IMAGE_AUTOCLEANUP
                    )
            );
        }

        if (SettingsStatus.nativeTranslator) {
            category = preferenceCategory(str("piko_title_native_share_menu"));

            addPreference(category,
                    helper.switchPreference(
                            str("piko_native_share_menu_toggle"),
                            "",
                            Settings.NATIVE_SHARE_MENU
                    )
            );
        }
    }

    public void buildMiscSection(boolean buildCategory){

        if (!(SettingsStatus.enableMiscSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_misc"));
        if (SettingsStatus.enableFontMod) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_chirp_font"),
                            "",
                            Settings.MISC_FONT
                    )
            );
        }
        if (SettingsStatus.hideFAB) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_fab"),
                            "",
                            Settings.MISC_HIDE_FAB
                    )
            );
        }
        if (SettingsStatus.hideFABBtns) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_fab_menu"),
                            "",
                            Settings.MISC_HIDE_FAB_BTN
                    )
            );
        }

        if (SettingsStatus.hideRecommendedUsers) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_rec_users"),
                            "",
                            Settings.MISC_HIDE_RECOMMENDED_USERS
                    )
            );
        }

        if (SettingsStatus.hideViewCount) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_view_count"),
                            "",
                            Settings.MISC_HIDE_VIEW_COUNT
                    )
            );
        }

        if (SettingsStatus.roundOffNumbers) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_round_off_numbers"),
                            str("piko_pref_round_off_numbers_desc"),
                            Settings.MISC_ROUND_OFF_NUMBERS
                    )
            );
        }

        if (SettingsStatus.enableDebugMenu) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_debug_menu"),
                            "",
                            Settings.MISC_DEBUG_MENU
                    )
            );
        }

        if (SettingsStatus.customSharingDomainEnabled) {
            addPreference(category,
                    helper.editTextPreference(
                            str("piko_pref_custom_share_domain"),
                            str("piko_pref_custom_share_domain_desc"),
                            Settings.CUSTOM_SHARING_DOMAIN
                    )
            );
        }

        if (SettingsStatus.hideSocialProof) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_social_proof"),
                            str("piko_pref_hide_social_proof_desc"),
                            Settings.MISC_HIDE_SOCIAL_PROOF
                    )
            );
        }

        if (SettingsStatus.removeSearchSuggestions) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_search_suggestion"),
                            str("piko_pref_search_suggestion_desc"),
                            Settings.MISC_HIDE_SEARCH_SUGGESTIONS
                    )
            );
        }

        if (SettingsStatus.pauseSearchSuggestions) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_pause_search_suggestion"),
                            str("piko_pref_pause_search_suggestion_desc"),
                            Settings.MISC_PAUSE_SEARCH_SUGGESTIONS
                    )
            );
        }


        if (SettingsStatus.disUnifyXChatSystem) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_disunify_xchat_system"),
                            str("piko_disunify_xchat_system_desc"),
                            Settings.MISC_DISUNIFY_XCHAT_SYSTEM
                    )
            );
        }

    }

    public void buildFeatureFlagsSection(boolean buildCategory){
        if (!(SettingsStatus.featureFlagsEnabled)) return;

        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_feature_flags"));

        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_feature_flags"),
                        "",
                        Settings.FEATURE_FLAGS,
                        "ic_vector_exiting",
                        null
                )
        );


        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_import_flags"),
                        str("piko_pref_app_restart_rec"),
                        Settings.IMPORT_FLAGS,
                        "ic_vector_incoming",
                        null
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_export_flags"),
                        "",
                        Settings.EXPORT_FLAGS,
                        "ic_vector_outgoing",
                        null
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_reset_flags"),
                        "",
                        Settings.RESET_FLAGS,
                        "ic_vector_trashcan_stroke",
                        "#DE0025"
                )
        );


    }

    public void buildCustomiseSection(boolean buildCategory){

        if (!(SettingsStatus.enableCustomisationSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_customisation"));
        if (SettingsStatus.profileTabCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_profiletabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_PROFILE_TABS
                    )
            );
        }
        if (SettingsStatus.timelineTabCustomisation) {
           addPreference(category,
                    helper.listPreference(
                            str("piko_pref_customisation_timelinetabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_TIMELINE_TABS
                    )
            );
        }
        if (SettingsStatus.exploreTabCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_exploretabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_EXPLORE_TABS
                    )
            );
        }
        if (SettingsStatus.sideBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_sidebartabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_SIDEBAR_TABS
                    )
            );
        }

        if (SettingsStatus.navBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_navbartabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_NAVBAR_TABS
                    )
            );
        }

        if (SettingsStatus.inlineBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_inlinetabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_INLINE_TABS
                    )
            );
        }

        if (SettingsStatus.searchTabCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_searchtabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_SEARCH_TABS
                    )
            );
        }

        if (SettingsStatus.notificationTabCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_notificationtabs"),
                            str("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_NOTIFICATION_TABS
                    )
            );
        }

        if (SettingsStatus.defaultReplySortFilter) {
           addPreference(category,
                    helper.listPreference(
                            str("piko_pref_customisation_reply_sorting"),
                            "",
                            Settings.CUSTOM_DEF_REPLY_SORTING
                    )
            );
        }

        if (SettingsStatus.typeaheadCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            str("piko_pref_customisation_search_type_ahead"),
                            "",
                            Settings.CUSTOM_SEARCH_TYPE_AHEAD
                    )
            );
        }

        if(SettingsStatus.customPostFontSize) {
            addPreference(category,
                    helper.editTextNumPreference(
                            str("piko_pref_customisation_post_font_size"),
                            String.valueOf(Pref.setPostFontSize()),
                            Settings.CUSTOM_POST_FONT_SIZE
                    ));
        }

        if (SettingsStatus.appIconCustomisation) {
            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_customisation_change_app_icon"),
                            "",
                            Settings.CHANGE_APP_ICON
                    )
            );
        }
        if (SettingsStatus.moreInfoOnProfile) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_quick_settings"),
                            str("piko_pref_quick_settings_summary"),
                            Settings.MISC_QUICK_SETTINGS_BUTTON
                    )
            );
        }

        addPreference(category,
                helper.switchPreference(
                        str("piko_pref_customisation_more_info_on_profile"),
                        str("piko_pref_customisation_more_info_on_profile_desc"),
                        Settings.MORE_INFO_ON_PROFILE
                )
        );
    }

    public void buildFontSection(boolean buildCategory){
        if (!(SettingsStatus.fontSection())) return;

        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_font"));

        if(SettingsStatus.customFont) {
            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_add_font"),
                            str("piko_pref_add_font_desc"),
                            Settings.ADD_FONT,
                            "ic_vector_pencil_stroke",
                            null
                    )
            );


            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_delete_font"),
                            "",
                            Settings.DELETE_FONT,
                            "ic_vector_trashcan_stroke",
                            "#DE0025"
                    )
            );
        }
        if(SettingsStatus.customEmojiFont) {
            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_add_emoji_font"),
                            str("piko_pref_add_font_desc"),
                            Settings.ADD_EMOJI_FONT,
                            "ic_vector_live_heart_stroke",
                            null
                    )
            );


            addPreference(category,
                    helper.buttonPreference(
                            str("piko_pref_delete_emoji_font"),
                            "",
                            Settings.DELETE_EMOJI_FONT,
                            "ic_vector_trashcan_stroke",
                            "#DE0025"
                    )
            );
        }



    }

    public void buildTimelineSection(boolean buildCategory){

        if (!(SettingsStatus.enableTimelineSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_timeline"));
        if (SettingsStatus.disableAutoTimelineScroll) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_disable_auto_timeline_scroll"),
                            "",
                            Settings.TIMELINE_DISABLE_AUTO_SCROLL
                    )
            );
        }
        if (SettingsStatus.showSourceLabel) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_show_post_source"),
                            str("piko_pref_show_post_source_desc"),
                            Settings.TIMELINE_SHOW_SOURCE_LABEL
                    )
            );
        }
        if (SettingsStatus.hideLiveThreads) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_live_threads"),
                            str("piko_pref_hide_live_threads_desc"),
                            Settings.TIMELINE_HIDE_LIVETHREADS
                    )
            );
        }
        if (SettingsStatus.hideBanner) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_banner"),
                            str("piko_pref_hide_banner_desc"),
                            Settings.TIMELINE_HIDE_BANNER
                    )
            );
        }
        if (SettingsStatus.hideInlineBmk) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_bmk_timeline"),
                            "",
                            Settings.TIMELINE_HIDE_BMK_ICON
                    )
            );
        }

        if (SettingsStatus.showPollResultsEnabled) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_show_poll_result"),
                            str("piko_pref_show_poll_result_desc"),
                            Settings.TIMELINE_SHOW_POLL_RESULTS
                    )
            );
        }

        if (SettingsStatus.unshortenlink) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_unshorten_link"),
                            str("piko_pref_unshorten_link_desc"),
                            Settings.TIMELINE_UNSHORT_URL
                    )
            );
        }

        if (SettingsStatus.hideCommunityNote) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_community_notes"),
                            "",
                            Settings.MISC_HIDE_COMM_NOTES
                    )
            );
        }

        if (SettingsStatus.forceTranslate) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_force_translate"),
                            str("piko_pref_force_translate_desc"),
                            Settings.TIMELINE_HIDE_FORCE_TRANSLATE
                    )
            );
        }

        if (SettingsStatus.hidePromoteButton) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_quick_promote"),
                            str("piko_pref_hide_quick_promote_desc"),
                            Settings.TIMELINE_HIDE_PROMOTE_BUTTON
                    )
            );
        }

        if (SettingsStatus.hideImmersivePlayer) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_immersive_player"),
                            str("piko_pref_hide_immersive_player_desc"),
                            Settings.TIMELINE_HIDE_IMMERSIVE_PLAYER
                    )
            );
        }
        if (SettingsStatus.enableVidAutoAdvance) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_enable_vid_auto_advance"),
                            str("piko_pref_enable_vid_auto_advance_desc"),
                            Settings.TIMELINE_ENABLE_VID_AUTO_ADVANCE
                    )
            );
        }
        if (SettingsStatus.hideHiddenReplies) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_hidden_replies"),
                            "",
                            Settings.TIMELINE_HIDE_HIDDEN_REPLIES
                    )
            );
        }
        if (SettingsStatus.enableForceHD) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_force_hd"),
                            str("piko_pref_force_hd_desc"),
                            Settings.TIMELINE_ENABLE_VID_FORCE_HD
                    )
            );
        }
        if (SettingsStatus.hideNudgeButton) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_nudge_button"),
                            str("piko_pref_hide_nudge_button_desc"),
                            Settings.TIMELINE_HIDE_NUDGE_BUTTON
                    )
            );
        }
        if (SettingsStatus.showSensitiveMedia) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_show_sensitive_media"),
                            "",
                            Settings.TIMELINE_SHOW_SENSITIVE_MEDIA
                    )
            );
        }
        if (SettingsStatus.hideCommBadge) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_community_badge"),
                            "",
                            Settings.TIMELINE_HIDE_COMM_BADGE
                    )
            );
        }
        if (SettingsStatus.hideNavbarBadge) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_badge_nav_bar"),
                            str("piko_pref_hide_badge_nav_bar_desc"),
                            Settings.TIMELINE_HIDE_NAVBAR_BADGE
                    )
            );
        }

        if (SettingsStatus.hidePostMetrics) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_post_inline_metrics"),
                            str("piko_pref_hide_post_inline_metrics_desc"),
                            Settings.TIMELINE_HIDE_POST_INLINE_METRICS
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_hide_post_detailed_metrics"),
                            str("piko_pref_hide_post_detailed_metrics_desc"),
                            Settings.TIMELINE_HIDE_POST_DETAILED_METRICS
                    )
            );
        }

    }

    public void buildLoggingSection(boolean buildCategory) {
        LegacyTwitterPreferenceCategory category = null;
        if (buildCategory)
            category = preferenceCategory(str("piko_title_logging"));

        if (SettingsStatus.serverResponseLogging) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_server_response_logging"),
                            str("piko_pref_server_response_logging_desc"),
                            Settings.LOG_RES
                    )
            );
        }

        if (SettingsStatus.serverResponseLoggingOverwriteFile) {
            addPreference(category,
                    helper.switchPreference(
                            str("piko_pref_server_response_logging_file_overwrite"),
                            str("piko_pref_server_response_logging_file_overwrite_desc"),
                            Settings.LOG_RES_OVRD
                    )
            );
        }

    }

    public void buildExportSection(boolean buildCategory){
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_backup"));


        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_import_settings"),
                        str("piko_pref_app_restart_rec"),
                        Settings.IMPORT_PREF,
                        "ic_vector_incoming",
                        null

                )
        );
       addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_export_settings"),
                        "",
                        Settings.EXPORT_PREF,
                        "ic_vector_outgoing",
                        null
                )
        );

       addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_reset_settings"),
                        "",
                        Settings.RESET_PREF,
                        "ic_vector_trashcan_stroke",
                        "#DE0025"
                )
        );

       if (SettingsStatus.exportLoginToken) {
           addPreference(category,
                   helper.buttonPreference(
                           str("piko_pref_import_login_token"),
                           "",
                           Settings.IMPORT_LOGIN_TOKEN,
                           "ic_vector_passkey",
                           null
                   )
           );
           addPreference(category,
                   helper.buttonPreference(
                           str("piko_pref_export_login_token"),
                           "",
                           Settings.EXPORT_LOGIN_TOKEN,
                           "ic_vector_passkey",
                           null
                   )
           );
       }
    }

    public void buildPikoSection(boolean buildCategory){
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(str("piko_title_about"));
        addPreference(category,
                helper.buttonPreference(
                        str("piko_pref_patch_info"),
                        "",
                        Settings.PATCH_INFO
                )
        );
    }

    public void buildSettingsCategories(){
        for (SettingsSection section : SETTINGS_SECTIONS) {
            if (!section.isEnabled()) {
                continue;
            }

            addPreference(
                    helper.buttonPreference(
                            section.rowTitle(),
                            "",
                            section.destinationKey,
                            section.iconName,
                            null
                    )
            );
        }
    }

    public int buildSettingsSearchResults(String query) {
        if (!SettingsSearchMatcher.isSearchQueryReady(query)) {
            return 0;
        }

        int addedResults = 0;
        for (SearchMatch matchedResult : SettingsSearchMatcher.matchResults(settingsSearchResults(), query)) {
            SearchResult result = matchedResult.result;

            String displaySummary = result.summaryForDisplay(
                    query,
                    SettingsSearchUIController.isLayoutRtl(context)
            );
            Preference searchResultPreference = helper.buttonPreference(
                    result.title,
                    displaySummary,
                    result.destinationKey,
                    result.iconName,
                    null
            );
            searchResultPreference.setTitle(SettingsSearchMatcher.highlightMatches(result.title, query));
            searchResultPreference.setSummary(SettingsSearchMatcher.highlightMatches(displaySummary, query));
            searchResultPreference.setOnPreferenceClickListener(preference -> {
                SettingsSearchNavigator.openResult(context, result);
                return true;
            });
            addPreference(searchResultPreference);
            addedResults++;
        }

        if (addedResults == 0) {
            return 0;
        }
        return addedResults;
    }

    public void invalidateSettingsSearchIndex() {
        searchIndex.invalidate();
    }

    private List<SearchResult> settingsSearchResults() {
        List<SearchResult> cachedResults = searchIndex.cachedResults();
        if (cachedResults != null) {
            return cachedResults;
        }

        PreferenceBuildTarget previousTarget = preferenceTarget;
        preferenceTarget = searchIndex;
        searchIndex.beginCollection();
        try {
            for (SettingsSection section : SETTINGS_SECTIONS) {
                if (!section.isEnabled()) {
                    continue;
                }

                PreferenceBuildTarget.SectionContext sectionContext = new PreferenceBuildTarget.SectionContext(
                        section.rowTitle(),
                        section.title(),
                        section.destinationKey(),
                        section.iconName()
                );
                preferenceTarget.beginSection(sectionContext);
                try {
                    if (preferenceTarget.acceptsSectionContents(sectionContext)) {
                        section.build(this, true);
                    }
                } finally {
                    preferenceTarget.endSection();
                }
            }
            return searchIndex.finishCollection();
        } catch (RuntimeException | Error throwable) {
            searchIndex.abortCollection();
            throw throwable;
        } finally {
            preferenceTarget = previousTarget;
        }
    }

    public boolean buildSection(String sectionKey, boolean buildCategory) {
        for (SettingsSection section : SETTINGS_SECTIONS) {
            if (TextUtils.equals(section.destinationKey(), sectionKey) && section.isEnabled()) {
                section.build(this, buildCategory);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static String getSectionTitleResourceName(String sectionKey) {
        if (Settings.PATCH_INFO.equals(sectionKey)) {
            return null;
        }
        for (SettingsSection section : SETTINGS_SECTIONS) {
            if (TextUtils.equals(section.destinationKey(), sectionKey)) {
                return section.titleResourceName();
            }
        }
        return null;
    }

    private interface SectionAvailability {
        boolean isEnabled();
    }

    private interface SectionBuilder {
        void build(ScreenBuilder screenBuilder, boolean buildCategory);
    }

    static class SettingsSection {
        private final String titleResourceName;
        private final String rowTitleResourceName;
        private final String destinationKey;
        private final String iconName;
        private final SectionAvailability availability;
        private final SectionBuilder builder;

        SettingsSection(
                String titleResourceName,
                @Nullable String rowTitleResourceName,
                String destinationKey,
                String iconName,
                SectionAvailability availability,
                SectionBuilder builder
        ) {
            this.titleResourceName = titleResourceName;
            this.rowTitleResourceName = rowTitleResourceName;
            this.destinationKey = destinationKey;
            this.iconName = iconName;
            this.availability = availability;
            this.builder = builder;
        }

        String title() {
            return str(titleResourceName);
        }

        String rowTitle() {
            return TextUtils.isEmpty(rowTitleResourceName) ? title() : str(rowTitleResourceName);
        }

        String destinationKey() {
            return destinationKey;
        }

        String iconName() {
            return iconName;
        }

        String titleResourceName() {
            return titleResourceName;
        }

        boolean isEnabled() {
            return availability.isEnabled();
        }

        void build(ScreenBuilder screenBuilder, boolean buildCategory) {
            builder.build(screenBuilder, buildCategory);
        }
    }

    public LegacyTwitterPreferenceCategory preferenceCategory(String title) {
        LegacyTwitterPreferenceCategory preferenceCategory = new LegacyTwitterPreferenceCategory(context);
        preferenceCategory.setTitle(title);
        preferenceTarget.addCategory(preferenceCategory);
        return preferenceCategory;
    }

    private static final class PreferenceScreenBuildTarget implements PreferenceBuildTarget {
        private final PreferenceScreen screen;

        PreferenceScreenBuildTarget(PreferenceScreen screen) {
            this.screen = screen;
        }

        @Override
        public void addCategory(LegacyTwitterPreferenceCategory category) {
            screen.addPreference(category);
        }

        @Override
        public void addPreference(@Nullable LegacyTwitterPreferenceCategory category, Preference preference) {
            if (category != null) {
                category.addPreference(preference);
            } else {
                screen.addPreference(preference);
            }
        }
    }




    //end
}
