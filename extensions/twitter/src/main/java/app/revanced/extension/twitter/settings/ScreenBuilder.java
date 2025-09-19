package app.revanced.extension.twitter.settings;

import app.revanced.extension.shared.StringRef;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.preference.Preference;
import androidx.annotation.Nullable;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.StringSetting;
import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;
import android.view.View;
import app.revanced.extension.twitter.settings.widgets.*;
import androidx.annotation.Nullable;
import app.revanced.extension.twitter.Pref;
public class ScreenBuilder {
    private Context context;
    private PreferenceScreen screen;
    private Helper helper;

    public ScreenBuilder(Context context,PreferenceScreen screen,Helper helper){
        this.context = context;
        this.screen = screen;
        this.helper = helper;
    }

    private void addPreference(Preference pref){
        screen.addPreference(pref);
    }
    private void addPreference(@Nullable LegacyTwitterPreferenceCategory category,Preference pref){
        if(category!=null){
            category.addPreference(pref);
        }else {
            addPreference(pref);
        }
    }

    public void buildPremiumSection(boolean buildCategory){

        if (!(SettingsStatus.enablePremiumSection())) return;
            
            LegacyTwitterPreferenceCategory category = null;
            if(buildCategory)
                category = preferenceCategory(strRes("piko_title_premium"));

            if (SettingsStatus.enableUndoPosts) {
                addPreference(category,
                        helper.switchPreference(
                                strEnableRes("piko_pref_undo_posts"),
                                strRes("piko_pref_undo_posts_desc"),
                                Settings.PREMIUM_UNDO_POSTS
                        )
                );

                if (SettingsStatus.enableForcePip) {
                    addPreference(category,
                            helper.switchPreference(
                                    strEnableRes("piko_pref_enable_force_pip"),
                                    strRes("piko_pref_enable_force_pip_desc"),
                                    Settings.PREMIUM_ENABLE_FORCE_PIP
                            )
                    );
                }

                addPreference(category,
                        helper.buttonPreference(
                                strRes("piko_pref_undo_posts_btn"),
                                "",
                                Settings.PREMIUM_UNDO_POSTS.key
                        )
                );
            }

        if (SettingsStatus.navBarCustomisation) {
            addPreference(category,
                    helper.buttonPreference(
                            strRes("tab_customization_screen_title"),
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
                    category = preferenceCategory(strRes("piko_title_download"));
            if (SettingsStatus.changeDownloadEnabled) {
                addPreference(category,helper.listPreference(
                        strRes("piko_pref_download_path"),
                        strRes("piko_pref_download_path_desc"),
                        Settings.VID_PUBLIC_FOLDER
                ));
                addPreference(category,helper.editTextPreference(
                        strRes("piko_pref_download_folder"),
                        strRes("piko_pref_download_folder_desc"),
                        Settings.VID_SUBFOLDER
                ));
            }
            if (SettingsStatus.mediaLinkHandle) {
                addPreference(category,
                        helper.listPreference(
                                strRes("piko_pref_download_media_link_handle"),
                                "",
                                Settings.VID_MEDIA_HANDLE
                        )
                );
            }
        
    }

    public void buildAdsSection(boolean buildCategory){
        if (!(SettingsStatus.enableAdsSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_ads"));
        if (SettingsStatus.hideAds) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_promoted_posts"),
                            "",
                            Settings.ADS_HIDE_PROMOTED_POSTS
                    )
            );
        }

        if (SettingsStatus.hideGAds) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_g_ads"),
                            "",
                            Settings.ADS_HIDE_GOOGLE_ADS
                    )
            );
        }
        if (SettingsStatus.hideMainEvent) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_main_event"),
                            "",
                            Settings.ADS_HIDE_MAIN_EVENT
                    )
            );
        }
        if (SettingsStatus.hideSuperheroEvent) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_superhero_event"),
                            "",
                            Settings.ADS_HIDE_SUPERHERO_EVENT
                    )
            );
        }
        if (SettingsStatus.hideVideosForYou) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_videos_for_you"),
                            "",
                            Settings.ADS_HIDE_VIDEOS_FOR_YOU
                    )
            );
        }
        if (SettingsStatus.hideWTF) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_wtf_section"),
                            "",
                            Settings.ADS_HIDE_WHO_TO_FOLLOW
                    )
            );
        }
        if (SettingsStatus.hideCTS) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_cts_section"),
                            "",
                            Settings.ADS_HIDE_CREATORS_TO_SUB
                    )
            );
        }

        if (SettingsStatus.hideCTJ) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_ctj_section"),
                            "",
                            Settings.ADS_HIDE_COMM_TO_JOIN
                    )
            );
        }

        if (SettingsStatus.hideRBMK) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_ryb_section"),
                            "",
                            Settings.ADS_HIDE_REVISIT_BMK
                    )
            );
        }

        if (SettingsStatus.hideRPinnedPosts) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_pinned_posts_section"),
                            "",
                            Settings.ADS_HIDE_REVISIT_PINNED_POSTS
                    )
            );
        }

        if (SettingsStatus.hideDetailedPosts) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_detailed_posts"),
                            "",
                            Settings.ADS_HIDE_DETAILED_POSTS
                    )
            );
        }

        if (SettingsStatus.hidePromotedTrend) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_trends"),
                            "",
                            Settings.ADS_HIDE_PROMOTED_TRENDS
                    )
            );
        }

        if (SettingsStatus.hidePremiumPrompt) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_premium_prompt"),
                            "",
                            Settings.ADS_HIDE_PREMIUM_PROMPT
                    )
            );
        }

        if (SettingsStatus.removePremiumUpsell) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_premium_upsell"),
                            strRes("piko_pref_hide_premium_upsell_desc"),
                            Settings.ADS_REMOVE_PREMIUM_UPSELL
                    )
            );
        }

        if (SettingsStatus.hideTopPeopleSearch) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_top_people_search"),
                            strRes("piko_pref_top_people_search_desc"),
                            Settings.ADS_HIDE_TOP_PEOPLE_SEARCH
                    )
            );
        }
        if (SettingsStatus.hideTodaysNews) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_todays_news"),
                            "",
                            Settings.ADS_REMOVE_TODAYS_NEW
                    )
            );
        }

        if (SettingsStatus.deleteFromDb) {
            addPreference(category,
                    helper.buttonPreference(
                            strRes("piko_pref_del_from_db"),
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
            nativePageDescription.setSummary(strRes("piko_pref_native_page_desc"));
            addPreference(nativePageDescription);
        }

        LegacyTwitterPreferenceCategory category = preferenceCategory(strRes("piko_title_native_downloader"));
        if (SettingsStatus.nativeDownloader) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_title_native_downloader_toggle"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER
                    )
            );

            addPreference(category,helper.listPreference(
                    strRes("piko_pref_download_path"),
                    strRes("piko_pref_download_path_desc"),
                    Settings.VID_PUBLIC_FOLDER
            ));
            addPreference(category,helper.editTextPreference(
                    strRes("piko_pref_download_folder"),
                    strRes("piko_pref_download_folder_desc"),
                    Settings.VID_SUBFOLDER
            ));
            addPreference(category,
                    helper.listPreference(
                            strRes("piko_pref_native_downloader_filename_title"),
                            "",
                            Settings.VID_NATIVE_DOWNLOADER_FILENAME
                    )
            );
        }


        category = preferenceCategory(strRes("piko_title_native_translator"));

        if (SettingsStatus.nativeTranslator) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_native_translator_toggle"),
                            "",
                            Settings.NATIVE_TRANSLATOR
                    )
            );
            addPreference(category,
                    helper.listPreference(
                            strRes("piko_native_translator_provider"),
                            "",
                            Settings.NATIVE_TRANSLATOR_PROVIDERS
                    )
            );
            addPreference(category,
                    helper.listPreference(
                            strRes("piko_native_translator_to_lang"),
                            Pref.translatorLanguage(),
                            Settings.NATIVE_TRANSLATOR_LANG
                    )
            );

        }

        category = preferenceCategory(strRes("piko_title_native_reader_mode"));

        if (SettingsStatus.nativeReaderMode) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_title_native_reader_mode"),
                            "",
                            Settings.NATIVE_READER_MODE
                    )
            );

            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_native_reader_mode_pref_text_only_mode"),
                            "",
                            Settings.NATIVE_READER_MODE_TEXT_ONLY_MODE
                    )
            );
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_native_reader_mode_pref_hide_quoted_posts"),
                            "",
                            Settings.NATIVE_READER_MODE_HIDE_QUOTED_POST
                    )
            );
            addPreference(category,
                    helper.switchPreference (
                            strRes("piko_native_reader_mode_pref_no_grok"),
                            "",
                            Settings.NATIVE_READER_MODE_NO_GROK
                    )
            );
            addPreference(category,
                    helper.buttonPreference(
                            strRes("piko_native_reader_mode_cache_delete"),
                            "",
                            Settings.RESET_READER_MODE_CACHE
                    )
            );

        }
    }

    public void buildMiscSection(boolean buildCategory){

        if (!(SettingsStatus.enableMiscSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_misc"));
        if (SettingsStatus.enableFontMod) {
            addPreference(category,
                    helper.switchPreference(
                            strEnableRes("piko_pref_chirp_font"),
                            "",
                            Settings.MISC_FONT
                    )
            );
        }
        if (SettingsStatus.hideFAB) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_fab"),
                            "",
                            Settings.MISC_HIDE_FAB
                    )
            );
        }
        if (SettingsStatus.hideFABBtns) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_fab_menu"),
                            "",
                            Settings.MISC_HIDE_FAB_BTN
                    )
            );
        }

        if (SettingsStatus.hideRecommendedUsers) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_rec_users"),
                            "",
                            Settings.MISC_HIDE_RECOMMENDED_USERS
                    )
            );
        }

        if (SettingsStatus.hideViewCount) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_view_count"),
                            "",
                            Settings.MISC_HIDE_VIEW_COUNT
                    )
            );
        }

        if (SettingsStatus.roundOffNumbers) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_round_off_numbers"),
                            strRes("piko_pref_round_off_numbers_desc"),
                            Settings.MISC_ROUND_OFF_NUMBERS
                    )
            );
        }

        if (SettingsStatus.enableDebugMenu) {
            addPreference(category,
                    helper.switchPreference(
                            strEnableRes("piko_pref_debug_menu"),
                            "",
                            Settings.MISC_DEBUG_MENU
                    )
            );
        }

        if (SettingsStatus.customSharingDomainEnabled) {
            addPreference(category,
                    helper.editTextPreference(
                            strRes("piko_pref_custom_share_domain"),
                            strRes("piko_pref_custom_share_domain_desc"),
                            Settings.CUSTOM_SHARING_DOMAIN
                    )
            );
        }

        if (SettingsStatus.hideSocialProof) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_hide_social_proof"),
                            strRes("piko_pref_hide_social_proof_desc"),
                            Settings.MISC_HIDE_SOCIAL_PROOF
                    )
            );
        }

    }

    public void buildFeatureFlagsSection(boolean buildCategory){
        if (!(SettingsStatus.featureFlagsEnabled)) return;

        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_feature_flags"));

        addPreference(category,
                helper.buttonPreference(
                        strRes("piko_pref_feature_flags"),
                        "",
                        Settings.FEATURE_FLAGS,
                        "ic_vector_exiting",
                        null
                )
        );


        addPreference(category,
                helper.buttonPreference(
                        StringRef.str("piko_pref_import",strRes("piko_title_feature_flags")),
                        strRes("piko_pref_app_restart_rec"),
                        Settings.IMPORT_FLAGS,
                        "ic_vector_incoming",
                        null
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        StringRef.str("piko_pref_export",strRes("piko_title_feature_flags")),
                        "",
                        Settings.EXPORT_FLAGS,
                        "ic_vector_outgoing",
                        null
                )
        );

        addPreference(category,
                helper.buttonPreference(
                        strRes("delete")+": "+strRes("piko_title_feature_flags"),
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
            category = preferenceCategory(strRes("piko_title_customisation"));
        if (SettingsStatus.profileTabCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_profiletabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_PROFILE_TABS
                    )
            );
        }
        if (SettingsStatus.timelineTabCustomisation) {
           addPreference(category,
                    helper.listPreference(
                            strRes("piko_pref_customisation_timelinetabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_TIMELINE_TABS
                    )
            );
        }
        if (SettingsStatus.exploreTabCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_exploretabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_EXPLORE_TABS
                    )
            );
        }
        if (SettingsStatus.sideBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_sidebartabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_SIDEBAR_TABS
                    )
            );
        }

        if (SettingsStatus.navBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_navbartabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_NAVBAR_TABS
                    )
            );
        }

        if (SettingsStatus.inlineBarCustomisation) {
           addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_inlinetabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_INLINE_TABS
                    )
            );
        }

        if (SettingsStatus.searchTabCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_searchtabs"),
                            strRes("piko_pref_app_restart_rec"),
                            Settings.CUSTOM_SEARCH_TABS
                    )
            );
        }

        if (SettingsStatus.defaultReplySortFilter) {
           addPreference(category,
                    helper.listPreference(
                            strRes("piko_pref_customisation_reply_sorting"),
                            "",
                            Settings.CUSTOM_DEF_REPLY_SORTING
                    )
            );
        }

        if (SettingsStatus.typeaheadCustomisation) {
            addPreference(category,
                    helper.multiSelectListPref(
                            strRes("piko_pref_customisation_search_type_ahead"),
                            "",
                            Settings.CUSTOM_SEARCH_TYPE_AHEAD
                    )
            );
        }

        if(SettingsStatus.customPostFontSize) {
            addPreference(category,
                    helper.editTextNumPreference(
                            strRes("piko_pref_customisation_post_font_size"),
                            String.valueOf(Pref.setPostFontSize()),
                            Settings.CUSTOM_POST_FONT_SIZE
                    ));
        }

        addPreference(category,
                helper.switchPreference(
                        strRes("piko_pref_quick_settings"),
                        strRes("piko_pref_quick_settings_summary"),
                        Settings.MISC_QUICK_SETTINGS_BUTTON
                )
        );

        addPreference(category,
                helper.switchPreference(
                        strRes("piko_single_page_settings"),
                        strRes("piko_single_page_settings_desc")+"\n"+strRes("piko_pref_app_restart_rec"),
                        Settings.SINGLE_PAGE_SETTINGS
                )
        );
    }

    public void buildTimelineSection(boolean buildCategory){

        if (!(SettingsStatus.enableTimelineSection())) return;
            
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_timeline"));
        if (SettingsStatus.disableAutoTimelineScroll) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_disable_auto_timeline_scroll"),
                            "",
                            Settings.TIMELINE_DISABLE_AUTO_SCROLL
                    )
            );
        }
        if (SettingsStatus.showSourceLabel) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_show_post_source"),
                            strRes("piko_pref_show_post_source_desc"),
                            Settings.TIMELINE_SHOW_SOURCE_LABEL
                    )
            );
        }
        if (SettingsStatus.hideLiveThreads) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_live_threads"),
                            strRes("piko_pref_hide_live_threads_desc"),
                            Settings.TIMELINE_HIDE_LIVETHREADS
                    )
            );
        }
        if (SettingsStatus.hideBanner) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_banner"),
                            strRemoveRes("piko_pref_hide_banner_desc"),
                            Settings.TIMELINE_HIDE_BANNER
                    )
            );
        }
        if (SettingsStatus.hideInlineBmk) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_bmk_timeline"),
                            "",
                            Settings.TIMELINE_HIDE_BMK_ICON
                    )
            );
        }

        if (SettingsStatus.showPollResultsEnabled) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_show_poll_result"),
                            strRes("piko_pref_show_poll_result_desc"),
                            Settings.TIMELINE_SHOW_POLL_RESULTS
                    )
            );
        }

        if (SettingsStatus.unshortenlink) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_unshorten_link"),
                            strRes("piko_pref_unshorten_link_desc"),
                            Settings.TIMELINE_UNSHORT_URL
                    )
            );
        }

        if (SettingsStatus.hideCommunityNote) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("community_notes_title"),
                            "",
                            Settings.MISC_HIDE_COMM_NOTES
                    )
            );
        }

        if (SettingsStatus.forceTranslate) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_force_translate"),
                            strRes("piko_pref_force_translate_desc"),
                            Settings.TIMELINE_HIDE_FORCE_TRANSLATE
                    )
            );
        }

        if (SettingsStatus.hidePromoteButton) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_quick_promote"),
                            strRes("piko_pref_hide_quick_promote_desc"),
                            Settings.TIMELINE_HIDE_PROMOTE_BUTTON
                    )
            );
        }

        if (SettingsStatus.hideImmersivePlayer) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_immersive_player"),
                            strRes("piko_pref_hide_immersive_player_desc"),
                            Settings.TIMELINE_HIDE_IMMERSIVE_PLAYER
                    )
            );
        }
        if (SettingsStatus.enableVidAutoAdvance) {
            addPreference(category,
                    helper.switchPreference(
                            strEnableRes("piko_pref_enable_vid_auto_advance"),
                            strRes("piko_pref_enable_vid_auto_advance_desc"),
                            Settings.TIMELINE_ENABLE_VID_AUTO_ADVANCE
                    )
            );
        }
        if (SettingsStatus.hideHiddenReplies) {
            addPreference(category,
                    helper.switchPreference(
                            strRemoveRes("piko_pref_hide_hidden_replies"),
                            "",
                            Settings.TIMELINE_HIDE_HIDDEN_REPLIES
                    )
            );
        }
        if (SettingsStatus.enableForceHD) {
            addPreference(category,
                    helper.switchPreference(
                            strEnableRes("piko_pref_force_hd"),
                            strRes("piko_pref_force_hd_desc"),
                            Settings.TIMELINE_ENABLE_VID_FORCE_HD
                    )
            );
        }
        if (SettingsStatus.hideNudgeButton) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_hide_nudge_button"),
                            strRes("piko_pref_hide_nudge_button_desc"),
                            Settings.TIMELINE_HIDE_NUDGE_BUTTON
                    )
            );
        }
        if (SettingsStatus.showSensitiveMedia) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_show_sensitive_media"),
                            "",
                            Settings.TIMELINE_SHOW_SENSITIVE_MEDIA
                    )
            );
        }
        if (SettingsStatus.hideCommBadge) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_hide_community_badge"),
                            "",
                            Settings.TIMELINE_HIDE_COMM_BADGE
                    )
            );
        }
    }

    public void buildLoggingSection(boolean buildCategory) {
        LegacyTwitterPreferenceCategory category = null;
        if (buildCategory)
            category = preferenceCategory(strRes("piko_title_logging"));

        if (SettingsStatus.serverResponseLogging) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_server_response_logging"),
                            strRes("piko_pref_server_response_logging_desc"),
                            Settings.LOG_RES
                    )
            );
        }

        if (SettingsStatus.serverResponseLoggingOverwriteFile) {
            addPreference(category,
                    helper.switchPreference(
                            strRes("piko_pref_server_response_logging_file_overwrite"),
                            strRes("piko_pref_server_response_logging_file_overwrite_desc"),
                            Settings.LOG_RES_OVRD
                    )
            );
        }

    }
    public void buildExportSection(boolean buildCategory){
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_backup"));


        addPreference(category,
                helper.buttonPreference(
                        StringRef.str("piko_pref_import",strRes("piko_name"))+" "+strRes("settings_notification_pref_item_title"),
                        strRes("piko_pref_app_restart_rec"),
                        Settings.IMPORT_PREF,
                        "ic_vector_incoming",
                        null

                )
        );
       addPreference(category,
                helper.buttonPreference(
                        StringRef.str("piko_pref_export",strRes("piko_name"))+" "+strRes("settings_notification_pref_item_title"),
                        "",
                        Settings.EXPORT_PREF,
                        "ic_vector_outgoing",
                        null
                )
        );

       addPreference(category,
                helper.buttonPreference(
                        strRes("delete")+": "+strRes("piko_name")+" "+strRes("settings_notification_pref_item_title"),
                        "",
                        Settings.RESET_PREF,
                        "ic_vector_trashcan_stroke",
                        "#DE0025"
                )
        );

    }

    public void buildPikoSection(boolean buildCategory){
        LegacyTwitterPreferenceCategory category = null;
        if(buildCategory)
            category = preferenceCategory(strRes("piko_title_about"));
        addPreference(category,
                helper.buttonPreference(
                        strRes("piko_pref_patch_info"),
                        "",
                        Settings.PATCH_INFO
                )
        );
    }

    public void buildSinglePageSettings(){
        if (SettingsStatus.enablePremiumSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_premium"),
                            "",
                            Settings.PREMIUM_SECTION,
                            "ic_vector_twitter",null
                    )
            ); 
        }
        if (SettingsStatus.enableDownloadSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_download"),
                            "",
                            Settings.DOWNLOAD_SECTION,
                            "ic_vector_incoming",null
                    )
            );
        }
        if (SettingsStatus.featureFlagsEnabled) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_feature_flags"),
                            "",
                            Settings.FLAGS_SECTION,
                            "ic_vector_flag",null
                    )
            );
        }
        if (SettingsStatus.enableAdsSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_ads"),
                            "",
                            Settings.ADS_SECTION,
                            "ic_vector_accessibility_alt",null
                    )
            );
        }
        if (SettingsStatus.enableNativeSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_native"),
                            "",
                            Settings.NATIVE_SECTION,
                            "ic_vector_flask_stroke",null
                    )
            );
        }
        if (SettingsStatus.enableMiscSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_misc"),
                            "",
                            Settings.MISC_SECTION,
                            "ic_vector_heartline",null
                    )
            );
        }
        if (SettingsStatus.enableCustomisationSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_customisation"),
                            "",
                            Settings.CUSTOMISE_SECTION,
                            "ic_vector_paintbrush_stroke",null
                    )
            );
        }
        if (SettingsStatus.enableTimelineSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_timeline"),
                            "",
                            Settings.TIMELINE_SECTION,
                            "ic_vector_timeline_stroke",null
                    )
            );
        }

        if (SettingsStatus.loggingSection()) {
            addPreference(
                    helper.buttonPreference(
                            strRes("piko_title_logging"),
                            "",
                            Settings.LOGGING_SECTION,
                            "ic_vector_bug_stroke",null
                    )
            );
        }
   
        addPreference(
                helper.buttonPreference(
                        strRes("piko_title_backup"),
                        "",
                        Settings.BACKUP_SECTION,
                        "ic_vector_layers_stroke",null
                )
        );
        
        addPreference(
                helper.buttonPreference(
                        strRes("piko_pref_patch_info"),
                        "",
                        Settings.PATCH_INFO,
                        "ic_vector_accessibility_circle",null
                )
        );
        
    }

    public LegacyTwitterPreferenceCategory preferenceCategory(String title) {
        LegacyTwitterPreferenceCategory preferenceCategory = new LegacyTwitterPreferenceCategory(context);
        preferenceCategory.setTitle(title);
        screen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    public String strRes(String tag) {
        return StringRef.str(tag);
    }

    public String strRemoveRes(String tag) {
        return StringRef.str("piko_pref_remove",strRes(tag));
    }

    public String strEnableRes(String tag) {
        return StringRef.str("piko_pref_enable",strRes(tag));
    }


    //end
}
