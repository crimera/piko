/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.fragments;

import static app.morphe.extension.shared.StringRef.str;

import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;

import app.morphe.extension.shared.Utils;
import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;
import java.util.*;
import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.twitter.patches.Changelogs;

@SuppressWarnings("deprecation")
public class SettingsAboutFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    private Context context;

    @Override
    public void onResume() {
        super.onResume();
        ActivityHook.toolbar.setTitle(str("piko_pref_patch_info"));
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        LegacyTwitterPreferenceCategory verPref = preferenceCategory(str("piko_pref_version_info"), screen);
        verPref.addPreference(
                buttonPreference(
                        str("piko_pref_app_version"),
                        Utils.getAppVersionName(),
                        str("piko_pref_app_version")
                )
        );
        verPref.addPreference(
                buttonPreference(
                        str("piko_title_piko_patches"),
                        Utils.getPatchesReleaseVersion(),
                        str("piko_title_piko_patches")
                )
        );
        verPref.addPreference(
                buttonPreference(
                        str("piko_changelogs_title"),
                        "",
                        str("piko_changelogs_title")
                )
        );
        verPref.addPreference(
                buttonPreference(
                        str("piko_settings_supported_links"),
                        "",
                        str("piko_settings_supported_links")
                )
        );

        TreeMap<String,Boolean> flags = new TreeMap();
        flags.put(str("piko_pref_video_download"),SettingsStatus.enableVidDownload);
        flags.put(str("piko_pref_undo_posts"),SettingsStatus.enableUndoPosts);
        flags.put(str("tab_customization_screen_title"),SettingsStatus.navBarCustomisation);
        flags.put(str("piko_pref_download"),SettingsStatus.changeDownloadEnabled);
        flags.put(str("piko_pref_download_media_link_handle"),SettingsStatus.mediaLinkHandle);
        flags.put(str("piko_pref_hide_promoted_posts"),SettingsStatus.hideAds);
        flags.put(str("piko_pref_wtf_section"),SettingsStatus.hideWTF);
        flags.put(str("piko_pref_cts_section"),SettingsStatus.hideCTS);
        flags.put(str("piko_pref_ctj_section"),SettingsStatus.hideCTJ);
        flags.put(str("piko_pref_ryb_section"),SettingsStatus.hideRBMK);
        flags.put(str("piko_pref_pinned_posts_section"),SettingsStatus.hideRPinnedPosts);
        flags.put(str("piko_pref_hide_related_posts"),SettingsStatus.hideDetailedPosts);
        flags.put(str("piko_pref_chirp_font"),SettingsStatus.enableFontMod);
        flags.put(str("piko_pref_hide_fab"),SettingsStatus.hideFAB);
        flags.put(str("piko_pref_hide_fab_menu"),SettingsStatus.hideFABBtns);
        flags.put(str("piko_pref_show_sensitive_media"),SettingsStatus.showSensitiveMedia);
        flags.put(str("piko_pref_selectable_text"),SettingsStatus.selectableText);
        flags.put(str("piko_pref_rec_users"),SettingsStatus.hideRecommendedUsers);
        flags.put(str("piko_pref_custom_share_domain"),SettingsStatus.customSharingDomainEnabled);
        flags.put(str("piko_pref_feature_flags"),SettingsStatus.featureFlagsEnabled);
        flags.put(str("piko_pref_customisation_profiletabs"),SettingsStatus.profileTabCustomisation);
        flags.put(str("piko_pref_customisation_timelinetabs"),SettingsStatus.timelineTabCustomisation);
        flags.put(str("piko_pref_customisation_exploretabs"),SettingsStatus.exploreTabCustomisation);
        flags.put(str("piko_pref_customisation_navbartabs"),SettingsStatus.navBarCustomisation);
        flags.put(str("piko_pref_customisation_sidebartabs"),SettingsStatus.sideBarCustomisation);
        flags.put(str("piko_pref_disable_auto_timeline_scroll"),SettingsStatus.disableAutoTimelineScroll);
        flags.put(str("piko_pref_hide_live_threads"),SettingsStatus.hideLiveThreads);
        flags.put(str("piko_pref_hide_view_count"),SettingsStatus.hideViewCount);
        flags.put(str("piko_pref_hide_banner"),SettingsStatus.hideBanner);
        flags.put(str("piko_pref_hide_bmk_timeline"),SettingsStatus.hideInlineBmk);
        flags.put(str("piko_pref_show_poll_result"),SettingsStatus.showPollResultsEnabled);
        flags.put(str("community_notes_title"),SettingsStatus.hideCommunityNote);
        flags.put(str("piko_pref_hide_quick_promote"),SettingsStatus.hidePromoteButton);
        flags.put(str("piko_pref_hide_immersive_player"),SettingsStatus.hideImmersivePlayer);
        flags.put(str("piko_pref_clear_tracking_params"),SettingsStatus.cleartrackingparams);
        flags.put(str("piko_pref_unshorten_link"),SettingsStatus.unshortenlink);
        flags.put(str("piko_pref_force_translate"),SettingsStatus.forceTranslate);
        flags.put(str("piko_pref_round_off_numbers"),SettingsStatus.roundOffNumbers);
        flags.put(str("piko_pref_customisation_inlinetabs"),SettingsStatus.inlineBarCustomisation);
        flags.put(str("piko_pref_debug_menu"),SettingsStatus.enableDebugMenu);
        flags.put(str("piko_pref_hide_premium_prompt"),SettingsStatus.hidePremiumPrompt);
        flags.put(str("piko_pref_hide_hidden_replies"),SettingsStatus.hideHiddenReplies);
        flags.put(str("piko_pref_del_from_db"),SettingsStatus.deleteFromDb);
        flags.put(str("piko_title_native_downloader"),SettingsStatus.nativeDownloader);
        flags.put(str("piko_pref_native_downloader_inline_button"),SettingsStatus.inlineDownloadButton);
        flags.put(str("piko_share_image_title"),SettingsStatus.shareImage);
        flags.put(str("piko_browse_object_title"),SettingsStatus.browseObject);
        flags.put(str("piko_title_native_reader_mode"),SettingsStatus.nativeReaderMode);
        flags.put(str("piko_pref_enable_vid_auto_advance"),SettingsStatus.enableVidAutoAdvance);
        flags.put(str("piko_pref_enable_force_pip"),SettingsStatus.enableForcePip);
        flags.put(str("piko_pref_hide_premium_upsell"),SettingsStatus.removePremiumUpsell);
        flags.put(str("piko_pref_customisation_reply_sorting"),SettingsStatus.defaultReplySortFilter);
        flags.put(str("piko_pref_force_hd"),SettingsStatus.enableForceHD);
        flags.put(str("piko_pref_hide_nudge_button"),SettingsStatus.hideNudgeButton);
        flags.put(str("piko_pref_hide_social_proof"),SettingsStatus.hideSocialProof);
        flags.put(str("piko_pref_hide_community_badge"),SettingsStatus.hideCommBadge);
        flags.put(str("piko_title_native_translator"),SettingsStatus.nativeTranslator);
        flags.put(str("piko_pref_customisation_post_font_size"),SettingsStatus.customPostFontSize);
        flags.put(str("piko_pref_top_people_search"),SettingsStatus.hideTopPeopleSearch);
        flags.put(str("piko_pref_customisation_searchtabs"),SettingsStatus.searchTabCustomisation);
        flags.put(str("piko_pref_hide_todays_news"),SettingsStatus.hideTodaysNews);
        flags.put(str("piko_pref_server_response_logging"),SettingsStatus.serverResponseLogging);
        flags.put(str("piko_pref_show_post_source"),SettingsStatus.showSourceLabel);
        flags.put(str("piko_changelogs_title"),SettingsStatus.showChangelogsPatchEnabled);
        flags.put(str("piko_pref_pause_search_suggestion"),SettingsStatus.pauseSearchSuggestions);
        flags.put(str("piko_pref_search_suggestion"),SettingsStatus.removeSearchSuggestions);
        flags.put(str("piko_pref_customisation_change_app_icon"),SettingsStatus.appIconCustomisation);
        flags.put(str("piko_pref_hide_badge_nav_bar"),SettingsStatus.hideNavbarBadge);
        flags.put(str("piko_pref_hide_post_inline_metrics"),SettingsStatus.hidePostMetrics);
        flags.put(str("piko_disunify_xchat_system"),SettingsStatus.disUnifyXChatSystem);
        flags.put(str("piko_legacy_share_link"),SettingsStatus.legacyShareLink);
        flags.put(str("piko_pref_export_login_token"),SettingsStatus.exportLoginToken);
        flags.put(str("piko_block_redirecting_to_x_lite"),SettingsStatus.blockRedirectingToXLite);

        LegacyTwitterPreferenceCategory patPref = preferenceCategory(str("piko_pref_patches"), screen);

        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            String resName = entry.getKey();
            boolean sts = (boolean) entry.getValue();

            patPref.addPreference(
                    buttonPreference2(
                            resName,
                            sts,
                            str("piko_pref_patches")
                    )
            );
        }

        setPreferenceScreen(screen);

    }

    private LegacyTwitterPreferenceCategory preferenceCategory(String title, PreferenceScreen screen) {
        LegacyTwitterPreferenceCategory preferenceCategory = new LegacyTwitterPreferenceCategory(context);
        preferenceCategory.setTitle(title);
        screen.addPreference(preferenceCategory);
        return preferenceCategory;
    }

    private Preference buttonPreference(String title, String summary, String key) {
        Preference preference = new Preference(context);
        preference.setKey(key);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(this);
        return preference;
    }

    private Preference buttonPreference2(String title, Boolean inc, String key) {
        String summary = inc ? str("piko_pref_included"):str("piko_pref_excluded");
        String colorHex = inc ? "#008FC4":"#DE0025";
        Preference preference = new Preference(context);
        preference.setKey(key);
        preference.setTitle(title);
        Spannable summarySpan = new SpannableString(summary);
        summarySpan.setSpan(new ForegroundColorSpan(Color.parseColor(colorHex)), 0, summary.length(), 0);
        preference.setSummary(summarySpan);
        preference.setOnPreferenceClickListener(this);
        return preference;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if ( (key.equals(str("piko_pref_app_version"))) || (key.equals(str("piko_title_piko_patches"))) ) {
            String summary = preference.getSummary().toString();
            Utils.setClipboard(summary);
            Utils.showToastShort(str("copied_to_clipboard")+": "+ summary);
        }else if (key.equals(str("piko_settings_supported_links"))){
            app.morphe.extension.crimera.PikoUtils.openDefaultLinks();
        }else if (key.equals(str("piko_changelogs_title"))){
            Changelogs.showChangelogDialog(context);
        }

        return true;
    }

}