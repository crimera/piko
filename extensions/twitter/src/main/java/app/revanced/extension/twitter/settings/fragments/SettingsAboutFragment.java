package app.revanced.extension.twitter.settings.fragments;

import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import androidx.annotation.Nullable;
import app.revanced.extension.shared.Utils;
import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;
import java.util.*;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.twitter.settings.ActivityHook;
import app.revanced.extension.twitter.settings.SettingsStatus;

@SuppressWarnings("deprecation")
public class SettingsAboutFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    private Context context;

    @Override
    public void onResume() {
        super.onResume();
        ActivityHook.toolbar.setTitle(strRes("piko_pref_patch_info"));
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        LegacyTwitterPreferenceCategory verPref = preferenceCategory(strRes("piko_pref_version_info"), screen);
        verPref.addPreference(
                buttonPreference(
                        strRes("piko_pref_app_version"),
                        Utils.getAppVersionName(),
                        strRes("piko_pref_app_version")
                )
        );
        verPref.addPreference(
                buttonPreference(
                        strRes("piko_title_piko_patches"),
                        Utils.getPatchesReleaseVersion(),
                        strRes("piko_title_piko_patches")
                )
        );
        verPref.addPreference(
                buttonPreference(
                        strRes("piko_settings_supported_links"),
                        "",
                        strRes("piko_settings_supported_links")
                )
        );

        TreeMap<String,Boolean> flags = new TreeMap();
        flags.put(strEnableRes("piko_pref_video_download"),SettingsStatus.enableVidDownload);
        flags.put(strEnableRes("piko_pref_undo_posts"),SettingsStatus.enableUndoPosts);
        flags.put(strRes("tab_customization_screen_title"),SettingsStatus.navBarCustomisation);
        flags.put(strRes("piko_pref_download"),SettingsStatus.changeDownloadEnabled);
        flags.put(strRes("piko_pref_download_media_link_handle"),SettingsStatus.mediaLinkHandle);
        flags.put(strRemoveRes("piko_pref_hide_promoted_posts"),SettingsStatus.hideAds);
        flags.put(strRemoveRes("piko_pref_wtf_section"),SettingsStatus.hideWTF);
        flags.put(strRemoveRes("piko_pref_cts_section"),SettingsStatus.hideCTS);
        flags.put(strRemoveRes("piko_pref_ctj_section"),SettingsStatus.hideCTJ);
        flags.put(strRemoveRes("piko_pref_ryb_section"),SettingsStatus.hideRBMK);
        flags.put(strRemoveRes("piko_pref_pinned_posts_section"),SettingsStatus.hideRPinnedPosts);
        flags.put(strRemoveRes("piko_pref_hide_detailed_posts"),SettingsStatus.hideDetailedPosts);
        flags.put(strEnableRes("piko_pref_chirp_font"),SettingsStatus.enableFontMod);
        flags.put(strRemoveRes("piko_pref_hide_fab"),SettingsStatus.hideFAB);
        flags.put(strRemoveRes("piko_pref_hide_fab_menu"),SettingsStatus.hideFABBtns);
        flags.put(strRes("piko_pref_show_sensitive_media"),SettingsStatus.showSensitiveMedia);
        flags.put(strRes("piko_pref_selectable_text"),SettingsStatus.selectableText);
        flags.put(strRemoveRes("piko_pref_rec_users"),SettingsStatus.hideRecommendedUsers);
        flags.put(strRes("piko_pref_custom_share_domain"),SettingsStatus.customSharingDomainEnabled);
        flags.put(strRes("piko_pref_feature_flags"),SettingsStatus.featureFlagsEnabled);
        flags.put(strRes("piko_pref_customisation_profiletabs"),SettingsStatus.profileTabCustomisation);
        flags.put(strRes("piko_pref_customisation_timelinetabs"),SettingsStatus.timelineTabCustomisation);
        flags.put(strRes("piko_pref_customisation_exploretabs"),SettingsStatus.exploreTabCustomisation);
        flags.put(strRes("piko_pref_customisation_navbartabs"),SettingsStatus.navBarCustomisation);
        flags.put(strRes("piko_pref_customisation_sidebartabs"),SettingsStatus.sideBarCustomisation);
        flags.put(strRes("piko_pref_disable_auto_timeline_scroll"),SettingsStatus.disableAutoTimelineScroll);
        flags.put(strRemoveRes("piko_pref_hide_live_threads"),SettingsStatus.hideLiveThreads);
        flags.put(strRemoveRes("piko_pref_hide_view_count"),SettingsStatus.hideViewCount);
        flags.put(strRemoveRes("piko_pref_hide_banner"),SettingsStatus.hideBanner);
        flags.put(strRemoveRes("piko_pref_hide_bmk_timeline"),SettingsStatus.hideInlineBmk);
        flags.put(strRes("piko_pref_show_poll_result"),SettingsStatus.showPollResultsEnabled);
        flags.put(strRemoveRes("community_notes_title"),SettingsStatus.hideCommunityNote);
        flags.put(strRemoveRes("piko_pref_hide_quick_promote"),SettingsStatus.hidePromoteButton);
        flags.put(strRemoveRes("piko_pref_hide_immersive_player"),SettingsStatus.hideImmersivePlayer);
        flags.put(strRes("piko_pref_clear_tracking_params"),SettingsStatus.cleartrackingparams);
        flags.put(strRes("piko_pref_unshorten_link"),SettingsStatus.unshortenlink);
        flags.put(strRes("piko_pref_force_translate"),SettingsStatus.forceTranslate);
        flags.put(strRes("piko_pref_round_off_numbers"),SettingsStatus.roundOffNumbers);
        flags.put(strRes("piko_pref_customisation_inlinetabs"),SettingsStatus.inlineBarCustomisation);
        flags.put(strEnableRes("piko_pref_debug_menu"),SettingsStatus.enableDebugMenu);
        flags.put(strRemoveRes("piko_pref_hide_premium_prompt"),SettingsStatus.hidePremiumPrompt);
        flags.put(strRemoveRes("piko_pref_hide_hidden_replies"),SettingsStatus.hideHiddenReplies);
        flags.put(strRes("piko_pref_del_from_db"),SettingsStatus.deleteFromDb);
        flags.put(strRes("piko_pref_video_download"),SettingsStatus.enableVidDownload);
        flags.put(strRes("piko_title_native_downloader"),SettingsStatus.nativeDownloader);
        flags.put(strRes("piko_title_native_reader_mode"),SettingsStatus.nativeReaderMode);
        flags.put(strEnableRes("piko_pref_enable_vid_auto_advance"),SettingsStatus.enableVidAutoAdvance);
        flags.put(strEnableRes("piko_pref_enable_force_pip"),SettingsStatus.enableForcePip);
        flags.put(strRemoveRes("piko_pref_hide_premium_upsell"),SettingsStatus.removePremiumUpsell);
        flags.put(strRes("piko_pref_customisation_reply_sorting"),SettingsStatus.defaultReplySortFilter);
        flags.put(strEnableRes("piko_pref_force_hd"),SettingsStatus.enableForceHD);
        flags.put(strRes("piko_pref_hide_nudge_button"),SettingsStatus.hideNudgeButton);
        flags.put(strRes("piko_pref_hide_social_proof"),SettingsStatus.hideSocialProof);
        flags.put(strRes("piko_pref_hide_community_badge"),SettingsStatus.hideCommBadge);
        flags.put(strRes("piko_title_native_translator"),SettingsStatus.nativeTranslator);
        flags.put(strRes("piko_pref_customisation_post_font_size"),SettingsStatus.customPostFontSize);
        flags.put(strRemoveRes("piko_pref_top_people_search"),SettingsStatus.hideTopPeopleSearch);
        flags.put(strRes("piko_pref_customisation_searchtabs"),SettingsStatus.searchTabCustomisation);
        flags.put(strRemoveRes("piko_pref_hide_todays_news"),SettingsStatus.hideTodaysNews);
        flags.put(strRemoveRes("piko_pref_server_response_logging"),SettingsStatus.serverResponseLogging);
        flags.put(strRes("piko_pref_show_post_source"),SettingsStatus.showSourceLabel);

        LegacyTwitterPreferenceCategory patPref = preferenceCategory(strRes("piko_pref_patches"), screen);

        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            String resName = entry.getKey();
            boolean sts = (boolean) entry.getValue();

            patPref.addPreference(
                    buttonPreference2(
                            resName,
                            sts,
                            strRes("piko_pref_patches")
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
        String summary = inc ? strRes("piko_pref_included"):strRes("piko_pref_excluded");
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
        if ( (key.equals(strRes("piko_pref_app_version"))) || (key.equals(strRes("piko_title_piko_patches"))) || (key.equals(strRes("piko_title_piko_integrations"))) ) {
            String summary = preference.getSummary().toString();
            Utils.setClipboard(summary);
            Utils.showToastShort(strRes("copied_to_clipboard")+": "+ summary);
        }else if (key.equals(strRes("piko_settings_supported_links"))){
            app.revanced.extension.twitter.Utils.openDefaultLinks();

        }

        return true;
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
}