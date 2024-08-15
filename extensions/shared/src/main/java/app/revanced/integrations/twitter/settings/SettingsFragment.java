package app.revanced.integrations.twitter.settings;

import app.revanced.integrations.shared.StringRef;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import androidx.annotation.Nullable;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.shared.settings.BooleanSetting;
import app.revanced.integrations.shared.settings.StringSetting;
import app.revanced.integrations.twitter.settings.featureflags.FeatureFlagsFragment;
import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;

import java.util.*;

@SuppressWarnings("deprecation")
public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    private Context context;

    @Override
    public void onResume() {
        super.onResume();
        ActivityHook.toolbar.setTitle(strRes("piko_title_settings"));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        preferenceManager.setSharedPreferencesName(Settings.SHARED_PREF_NAME);

        //premium section
        if (SettingsStatus.enablePremiumSection()) {
            LegacyTwitterPreferenceCategory premiumPrefs = preferenceCategory(strRes("piko_title_premium"), screen);
            if (SettingsStatus.enableReaderMode) {
                premiumPrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_reader_mode"),
                                strRes("piko_pref_reader_mode_desc"),
                                Settings.PREMIUM_READER_MODE
                        )
                );
            }
            if (SettingsStatus.enableUndoPosts) {
                premiumPrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_undo_posts"),
                                strRes("piko_pref_undo_posts_desc"),
                                Settings.PREMIUM_UNDO_POSTS
                        )
                );

                premiumPrefs.addPreference(
                        buttonPreference(
                                strRes("piko_pref_undo_posts_btn"),
                                "",
                                Settings.PREMIUM_UNDO_POSTS.key
                        )
                );
            }
            if (SettingsStatus.customAppIcon || SettingsStatus.navBarCustomisation) {
                premiumPrefs.addPreference(
                        buttonPreference(
                                strRes("piko_pref_icon_n_navbar_btn"),
                                "",
                                Settings.PREMIUM_ICONS.key
                        )
                );
            }
            if (SettingsStatus.enableForcePip) {
                premiumPrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_enable_force_pip"),
                                strRes("piko_pref_enable_force_pip_desc"),
                                Settings.PREMIUM_ENABLE_FORCE_PIP
                        )
                );
            }
        }

        //download section
        if (SettingsStatus.enableDownloadSection()) {
            LegacyTwitterPreferenceCategory downloadPrefs = preferenceCategory(strRes("piko_title_download"), screen);
            if (SettingsStatus.changeDownloadEnabled || SettingsStatus.nativeDownloader) {
                downloadPrefs.addPreference(listPreference(
                        strRes("piko_pref_download_path"),
                        strRes("piko_pref_download_path_desc"),
                        Settings.VID_PUBLIC_FOLDER
                ));
                downloadPrefs.addPreference(editTextPreference(
                        strRes("piko_pref_download_folder"),
                        strRes("piko_pref_download_folder_desc"),
                        Settings.VID_SUBFOLDER
                ));
            }
            if (SettingsStatus.mediaLinkHandle) {
                downloadPrefs.addPreference(
                        listPreference(
                                strRes("piko_pref_download_media_link_handle"),
                                "",
                                Settings.VID_MEDIA_HANDLE
                        )
                );
            }
        }

        //ads section
        if (SettingsStatus.enableAdsSection()) {
            LegacyTwitterPreferenceCategory adsPrefs = preferenceCategory(strRes("piko_title_ads"), screen);

            if (SettingsStatus.hideAds) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_promoted_posts"),
                                "",
                                Settings.ADS_HIDE_PROMOTED_POSTS
                        )
                );
            }

            if (SettingsStatus.hideGAds) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_g_ads"),
                                "",
                                Settings.ADS_HIDE_GOOGLE_ADS
                        )
                );
            }
            if (SettingsStatus.hideMainEvent) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_main_event"),
                                "",
                                Settings.ADS_HIDE_MAIN_EVENT
                        )
                );
            }
            if (SettingsStatus.hideSuperheroEvent) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_superhero_event"),
                                "",
                                Settings.ADS_HIDE_SUPERHERO_EVENT
                        )
                );
            }
            if (SettingsStatus.hideVideosForYou) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_videos_for_you"),
                                "",
                                Settings.ADS_HIDE_VIDEOS_FOR_YOU
                        )
                );
            }
            if (SettingsStatus.hideWTF) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_wtf_section"),
                                "",
                                Settings.ADS_HIDE_WHO_TO_FOLLOW
                        )
                );
            }
            if (SettingsStatus.hideCTS) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_cts_section"),
                                "",
                                Settings.ADS_HIDE_CREATORS_TO_SUB
                        )
                );
            }

            if (SettingsStatus.hideCTJ) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_ctj_section"),
                                "",
                                Settings.ADS_HIDE_COMM_TO_JOIN
                        )
                );
            }

            if (SettingsStatus.hideRBMK) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_ryb_section"),
                                "",
                                Settings.ADS_HIDE_REVISIT_BMK
                        )
                );
            }

            if (SettingsStatus.hideRPinnedPosts) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_pinned_posts_section"),
                                "",
                                Settings.ADS_HIDE_REVISIT_PINNED_POSTS
                        )
                );
            }

            if (SettingsStatus.hideDetailedPosts) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_detailed_posts"),
                                "",
                                Settings.ADS_HIDE_DETAILED_POSTS
                        )
                );
            }

            if (SettingsStatus.hidePromotedTrend) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_trends"),
                                "",
                                Settings.ADS_HIDE_PROMOTED_TRENDS
                        )
                );
            }

            if (SettingsStatus.hidePremiumPrompt) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_premium_prompt"),
                                "",
                                Settings.ADS_HIDE_PREMIUM_PROMPT
                        )
                );
            }

            if (SettingsStatus.removePremiumUpsell) {
                adsPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_premium_upsell"),
                                strRes("piko_pref_hide_premium_upsell_desc"),
                                Settings.ADS_REMOVE_PREMIUM_UPSELL
                        )
                );
            }

            if (SettingsStatus.deleteFromDb) {
                adsPrefs.addPreference(
                        buttonPreference(
                                strRes("piko_pref_del_from_db"),
                                "",
                                Settings.ADS_DEL_FROM_DB.key
                        )
                );
            }


        }

        //misc Section
        if (SettingsStatus.enableMiscSection()) {
            LegacyTwitterPreferenceCategory miscPrefs = preferenceCategory(strRes("piko_title_misc"), screen);
            if (SettingsStatus.enableFontMod) {
                miscPrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_chirp_font"),
                                "",
                                Settings.MISC_FONT
                        )
                );
            }
            if (SettingsStatus.hideFAB) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_fab"),
                                "",
                                Settings.MISC_HIDE_FAB
                        )
                );
            }
            if (SettingsStatus.hideFABBtns) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_fab_menu"),
                                "",
                                Settings.MISC_HIDE_FAB_BTN
                        )
                );
            }

            if (SettingsStatus.hideRecommendedUsers) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_rec_users"),
                                "",
                                Settings.MISC_HIDE_RECOMMENDED_USERS
                        )
                );
            }

            if (SettingsStatus.hideViewCount) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_view_count"),
                                "",
                                Settings.MISC_HIDE_VIEW_COUNT
                        )
                );
            }

            if (SettingsStatus.browserChooserEnabled) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_browser_chooser"),
                                strRes("piko_pref_browser_chooser_desc"),
                                Settings.MISC_BROWSER_CHOOSER
                        )
                );
            }

            if (SettingsStatus.customSharingDomainEnabled) {
                miscPrefs.addPreference(
                        editTextPreference(
                                strRes("piko_pref_custom_share_domain"),
                                strRes("piko_pref_custom_share_domain_desc"),
                                Settings.CUSTOM_SHARING_DOMAIN
                        )
                );
            }

            if (SettingsStatus.roundOffNumbers) {
                miscPrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_round_off_numbers"),
                                strRes("piko_pref_round_off_numbers_desc"),
                                Settings.MISC_ROUND_OFF_NUMBERS
                        )
                );
            }

            if (SettingsStatus.enableDebugMenu) {
                miscPrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_debug_menu"),
                                "",
                                Settings.MISC_DEBUG_MENU
                        )
                );
            }

            miscPrefs.addPreference(
              switchPreference(
                      strRes("piko_pref_quick_settings"),
                      strRes("piko_pref_quick_settings_summary"),
                      Settings.MISC_QUICK_SETTINGS_BUTTON
              )
            );

            if (SettingsStatus.featureFlagsEnabled) {
                miscPrefs.addPreference(
                        buttonPreference(
                                strRes("piko_pref_feature_flags"),
                                "",
                                Settings.MISC_FEATURE_FLAGS.key
                        )
                );
            }
        }

        //customise Section
        if (SettingsStatus.enableCustomisationSection()) {
            LegacyTwitterPreferenceCategory customisationPrefs = preferenceCategory(strRes("piko_title_customisation"), screen);
            if (SettingsStatus.profileTabCustomisation) {
                customisationPrefs.addPreference(
                        multiSelectListPreference(
                                strRes("piko_pref_customisation_profiletabs"),
                                strRes("piko_pref_app_restart_rec"),
                                Settings.CUSTOM_PROFILE_TABS
                        )
                );
            }
            if (SettingsStatus.timelineTabCustomisation) {
                customisationPrefs.addPreference(
                        listPreference(
                                strRes("piko_pref_customisation_timelinetabs"),
                                strRes("piko_pref_app_restart_rec"),
                                Settings.CUSTOM_TIMELINE_TABS
                        )
                );
            }
            if (SettingsStatus.sideBarCustomisation) {
                customisationPrefs.addPreference(
                        multiSelectListPreference(
                                strRes("piko_pref_customisation_sidebartabs"),
                                strRes("piko_pref_app_restart_rec"),
                                Settings.CUSTOM_SIDEBAR_TABS
                        )
                );
            }

            if (SettingsStatus.navBarCustomisation) {
                customisationPrefs.addPreference(
                        multiSelectListPreference(
                                strRes("piko_pref_customisation_navbartabs"),
                                strRes("piko_pref_app_restart_rec"),
                                Settings.CUSTOM_NAVBAR_TABS
                        )
                );
            }

            if (SettingsStatus.inlineBarCustomisation) {
                customisationPrefs.addPreference(
                        multiSelectListPreference(
                                strRes("piko_pref_customisation_inlinetabs"),
                                strRes("piko_pref_app_restart_rec"),
                                Settings.CUSTOM_INLINE_TABS
                        )
                );
            }

            if (SettingsStatus.defaultReplySortFilter) {
                customisationPrefs.addPreference(
                        listPreference(
                                strRes("piko_pref_customisation_reply_sorting"),
                                "",
                                Settings.CUSTOM_DEF_REPLY_SORTING
                        )
                );
            }
        }

        //Timeline Section
        if (SettingsStatus.enableTimelineSection()) {
            LegacyTwitterPreferenceCategory timelinePrefs = preferenceCategory(strRes("piko_title_timeline"), screen);
            if (SettingsStatus.disableAutoTimelineScroll) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_disable_auto_timeline_scroll"),
                                "",
                                Settings.TIMELINE_DISABLE_AUTO_SCROLL
                        )
                );
            }
            if (SettingsStatus.hideLiveThreads) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_live_threads"),
                                strRes("piko_pref_hide_live_threads_desc"),
                                Settings.TIMELINE_HIDE_LIVETHREADS
                        )
                );
            }
            if (SettingsStatus.hideBanner) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_banner"),
                                strRemoveRes("piko_pref_hide_banner_desc"),
                                Settings.TIMELINE_HIDE_BANNER
                        )
                );
            }
            if (SettingsStatus.hideInlineBmk) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_bmk_timeline"),
                                "",
                                Settings.TIMELINE_HIDE_BMK_ICON
                        )
                );
            }

            if (SettingsStatus.showPollResultsEnabled) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_show_poll_result"),
                                strRes("piko_pref_show_poll_result_desc"),
                                Settings.TIMELINE_SHOW_POLL_RESULTS
                        )
                );
            }

            if (SettingsStatus.unshortenlink) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_unshorten_link"),
                                strRes("piko_pref_unshorten_link_desc"),
                                Settings.TIMELINE_UNSHORT_URL
                        )
                );
            }

            if (SettingsStatus.hideCommunityNote) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("community_notes_title"),
                                "",
                                Settings.MISC_HIDE_COMM_NOTES
                        )
                );
            }

            if (SettingsStatus.forceTranslate) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRes("piko_pref_force_translate"),
                                strRes("piko_pref_force_translate_desc"),
                                Settings.TIMELINE_HIDE_FORCE_TRANSLATE
                        )
                );
            }

            if (SettingsStatus.hidePromoteButton) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_quick_promote"),
                                strRes("piko_pref_hide_quick_promote_desc"),
                                Settings.TIMELINE_HIDE_PROMOTE_BUTTON
                        )
                );
            }

            if (SettingsStatus.hideImmersivePlayer) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_immersive_player"),
                                strRes("piko_pref_hide_immersive_player_desc"),
                                Settings.TIMELINE_HIDE_IMMERSIVE_PLAYER
                        )
                );
            }
            if (SettingsStatus.enableVidAutoAdvance) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strEnableRes("piko_pref_enable_vid_auto_advance"),
                                strRes("piko_pref_enable_vid_auto_advance_desc"),
                                Settings.TIMELINE_ENABLE_VID_AUTO_ADVANCE
                        )
                );
            }
            if (SettingsStatus.hideHiddenReplies) {
                timelinePrefs.addPreference(
                        switchPreference(
                                strRemoveRes("piko_pref_hide_hidden_replies"),
                                "",
                                Settings.TIMELINE_HIDE_HIDDEN_REPLIES
                        )
                );
            }


        }


        //export section
        LegacyTwitterPreferenceCategory backupPref = preferenceCategory(strRes("piko_title_backup"), screen);
        backupPref.addPreference(
                buttonPreference(
                        StringRef.str("piko_pref_export",strRes("piko_name"))+" "+strRes("settings_notification_pref_item_title"),
                        "",
                        Settings.EXPORT_PREF.key
                )
        );
        backupPref.addPreference(
                buttonPreference(
                        StringRef.str("piko_pref_export",strRes("piko_title_feature_flags")),
                        "",
                        Settings.EXPORT_FLAGS.key
                )
        );

        backupPref.addPreference(
                buttonPreference(
                        StringRef.str("piko_pref_import",strRes("piko_name"))+" "+strRes("settings_notification_pref_item_title"),
                        strRes("piko_pref_app_restart_rec"),
                        Settings.IMPORT_PREF.key
                )
        );
        backupPref.addPreference(
                buttonPreference(
                        StringRef.str("piko_pref_import",strRes("piko_title_feature_flags")),
                        strRes("piko_pref_app_restart_rec"),
                        Settings.IMPORT_FLAGS.key
                )
        );

        backupPref.addPreference(
                buttonPreference(
                        strRes("delete")+": "+strRes("piko_name")+" "+strRes("settings_notification_pref_item_title"),
                        "",
                        Settings.RESET_PREF.key
                )
        );

        backupPref.addPreference(
                buttonPreference(
                        strRes("delete")+": "+strRes("piko_title_feature_flags"),
                        "",
                        Settings.RESET_FLAGS.key
                )
        );

        //about section
        LegacyTwitterPreferenceCategory aboutPref = preferenceCategory(strRes("piko_title_about"), screen);
        aboutPref.addPreference(
                buttonPreference(
                        strRes("piko_pref_patch_info"),
                        "",
                        Settings.PATCH_INFO.key
                )
        );


        setPreferenceScreen(screen);
    }

    private Preference editTextPreference(String title, String summary, StringSetting setting) {
        EditTextPreference preference = new EditTextPreference(context);
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        setOnPreferenceChangeListener(preference);
        return preference;
    }

    private Preference switchPreference(String title, String summary, BooleanSetting setting) {
        SwitchPreference preference = new SwitchPreference(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setKey(setting.key);
        preference.setDefaultValue(setting.defaultValue);
        setOnPreferenceChangeListener(preference);
        return preference;
    }

    private Preference buttonPreference(String title, String summary, String key) {
        Preference preference = new Preference(context);
        preference.setKey(key);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(this);
        return preference;
    }

    private Preference listPreference(String title, String summary, StringSetting setting) {
        ListPreference preference = new ListPreference(context);
        String key = setting.key;
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(key);
        preference.setDefaultValue(setting.defaultValue);
        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.VID_PUBLIC_FOLDER.key) {
            entries = new CharSequence[]{"Movies", "DCIM", "Pictures", "Download"};
            entriesValues = entries;
        }else if (key == Settings.CUSTOM_TIMELINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_timelinetabs");
            entriesValues = new CharSequence[]{"show_both","hide_forYou", "hide_following"};
        }else if (key == Settings.VID_MEDIA_HANDLE.key) {
            entries = Utils.getResourceStringArray("piko_array_download_media_handle");
            entriesValues = new CharSequence[]{"download_media","copy_media_link", "always_ask"};
        }else if (key == Settings.CUSTOM_INLINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_inlinetabs");
            entriesValues = new CharSequence[]{"Reply","Retweet", "Favorite","ViewCount","AddRemoveBookmarks", "TwitterShare"};
        }else if (key == Settings.CUSTOM_DEF_REPLY_SORTING.key) {
            entries = Utils.getResourceStringArray("piko_array_reply_sorting");
            entriesValues = new CharSequence[]{"Relevance","Recency", "Likes","LastPostion"};
        }
        preference.setEntries(entries);
        preference.setEntryValues(entriesValues);
        setOnPreferenceChangeListener(preference);
        return preference;
    }

    private Preference multiSelectListPreference(String title, String summary, StringSetting setting) {
        MultiSelectListPreference preference = new MultiSelectListPreference(context);
        String key = setting.key;
        preference.setTitle(title);
        preference.setDialogTitle(title);
        preference.setSummary(summary);
        preference.setKey(key);
        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.CUSTOM_PROFILE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_profiletabs");
            entriesValues = new CharSequence[]{"tweets", "tweets_replies", "affiliated", "subs", "highlights", "articles", "media", "likes"};
        }else if (key == Settings.CUSTOM_SIDEBAR_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_sidebar");
            entriesValues = new CharSequence[]{"Profile","TwitterBlueNonSubscriber", "Grok","DMs","Communities","Bookmarks","Lists","TopArticles","BirdwatchNotes","Spaces","PendingFollowers","Monetization","ProfessionalToolsGroup","MediaTransparency","Imprint"};
        }else if (key == Settings.CUSTOM_NAVBAR_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_navbar");
            entriesValues = new CharSequence[]{"HOME","GUIDE", "SPACES","COMMUNITIES","NOTIFICATIONS","CONNECT","COMMUNITY_NOTES","BOOKMARKS","DMS","GROK","MEDIA_TAB"};
        }else if (key == Settings.CUSTOM_INLINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_inlinetabs");
            entriesValues = new CharSequence[]{"Reply","Retweet", "Favorite","ViewCount","AddRemoveBookmarks", "TwitterShare"};
        }
        preference.setEntries(entries);
        preference.setEntryValues(entriesValues);
        setOnPreferenceChangeListener(preference);
        return preference;
    }

    private LegacyTwitterPreferenceCategory preferenceCategory(String title, PreferenceScreen screen) {
        LegacyTwitterPreferenceCategory preferenceCategory = new LegacyTwitterPreferenceCategory(context);
        preferenceCategory.setTitle(title);
        screen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key.equals(Settings.PREMIUM_UNDO_POSTS.key.toString())) {
            app.revanced.integrations.twitter.Utils.startUndoPostActivity();
        } else if (key.equals(Settings.PREMIUM_ICONS.key)) {
            app.revanced.integrations.twitter.Utils.startAppIconNNavIconActivity();
        } else if (key.equals(Settings.MISC_FEATURE_FLAGS.key)) {
            startFragment(new FeatureFlagsFragment());
        } else if (key.equals(Settings.EXPORT_FLAGS.key)) {
            startBackupFragment(new BackupPrefFragment(), true);
        } else if (key.equals(Settings.EXPORT_PREF.key)) {
            startBackupFragment(new BackupPrefFragment(), false);
        } else if (key.equals(Settings.IMPORT_PREF.key)) {
            startBackupFragment(new RestorePrefFragment(), false);
        } else if (key.equals(Settings.IMPORT_FLAGS.key)) {
            startBackupFragment(new RestorePrefFragment(), true);
        } else if (key.equals(Settings.PATCH_INFO.key)) {
            startFragment(new SettingsAboutFragment());
        } else if (key.equals(Settings.RESET_PREF.key)) {
            app.revanced.integrations.twitter.Utils.deleteSharedPrefAB(context,false);
        } else if (key.equals(Settings.RESET_FLAGS.key)) {
            app.revanced.integrations.twitter.Utils.deleteSharedPrefAB(context,true);
        }else if (key.equals(Settings.ADS_DEL_FROM_DB.key)) {
            app.revanced.integrations.twitter.patches.DatabasePatch.showDialog(context);
        }

        return true;
    }

    private void startFragment(Fragment fragment) {
        Bundle bundle = new Bundle();
        fragment.setArguments(bundle);
        getFragmentManager().beginTransaction().replace(Utils.getResourceIdentifier("fragment_container", "id"), fragment).addToBackStack(null).commit();
    }

    private void startBackupFragment(Fragment fragment, boolean featureFlag) {
        Bundle bundle = new Bundle();
        bundle.putBoolean("featureFlag", featureFlag);
        fragment.setArguments(bundle);
        getFragmentManager().beginTransaction().replace(Utils.getResourceIdentifier("fragment_container", "id"), fragment).addToBackStack(null).commit();
    }

    private void setOnPreferenceChangeListener(Preference preference) {
        String key = preference.getKey();
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                try {
                    if (newValue != null) {
                        String newValClass = newValue.getClass().getSimpleName();

                        if (newValClass.equals("Boolean")) {
                            setBooleanPerf(key, (Boolean) newValue);
                        } else if (newValClass.equals("String")) {
                            setStringPref(key, (String) newValue);
                        } else if (newValClass.equals("HashSet")) {
                            setSetPref(key, (Set) newValue);
                        }
                    }

                } catch (Exception ex) {
                    Utils.showToastShort(ex.toString());
                }
                return true;
            }
        });
    }

    private static String strRes(String tag) {
        return StringRef.str(tag);
    }

    private static String strRemoveRes(String tag) {
        return StringRef.str("piko_pref_remove",strRes(tag));
    }

    private static String strEnableRes(String tag) {
        return StringRef.str("piko_pref_enable",strRes(tag));
    }

    private static void setBooleanPerf(String key, Boolean val) {
        app.revanced.integrations.twitter.Utils.setBooleanPerf(key, val);
    }

    private static String getStringPref(StringSetting setting) {
        return app.revanced.integrations.twitter.Utils.getStringPref(setting);
    }
    private static void setStringPref(String key, String val) {
        app.revanced.integrations.twitter.Utils.setStringPref(key, val);
    }

    private static void setSetPref(String key, Set<String> val) {
        app.revanced.integrations.twitter.Utils.setSetPerf(key, val);
    }

    //end
}
