/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import static app.morphe.extension.twitter.patches.VersionCheckPatch.IS_11_95_OR_GREATER;

import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.settings.Settings;

import java.util.HashMap;

public class FeatureSwitchPatch {
    public static String FLAGS_SEARCH = "";

    private static final HashMap<String, Object> FLAGS = new HashMap<>();

    private static void addFlag(String flag, Object val) {
        FLAGS.put(flag, val);
    }

    private static void fabMenu() {
        addFlag("android_compose_fab_menu_enabled", Pref.hideFABBtn());
    }

    private static void chirpFont() {
        addFlag("af_ui_chirp_enabled", Pref.isChirpFontEnabled());
    }

    private static void hideGoogleAds() { addFlag("ssp_ads_dsp_client_context_enabled", !Pref.hideAds()); }
    private static void viewCount() {
        addFlag("view_counts_public_visibility_enabled", Pref.hideViewCount());
    }

    private static void bookmarkInTimeline() {
        addFlag("bookmarks_in_timelines_enabled", Pref.hideInlineBookmark());
    }

    private static void navbarFix() {
        addFlag("subscriptions_feature_1008", true);

        if (IS_11_95_OR_GREATER) {
            // Added in 11.95.0-aplha.0. This flag should be disabled for the patch to work properly.
            addFlag("subscriptions_feature_1008_sunset", false);
        }
    }

    private static void immersivePlayer() {
        addFlag("explore_relaunch_enable_immersive_player_across_twitter", Pref.hideImmersivePlayer());
    }

    public static void getFeatureFlagSearchItems() {
        FLAGS_SEARCH = Utils.getStringPref(Settings.MISC_FEATURE_FLAGS_SEARCH);
    }

    public static void addFeatureFlagSearchItem(String flag) {
        if (FLAGS_SEARCH.contains(flag)) {
            return;
        }

        FLAGS_SEARCH = FLAGS_SEARCH.concat(flag + ",");
        Utils.setStringPref(Settings.MISC_FEATURE_FLAGS_SEARCH.key, FLAGS_SEARCH);
    }

    private static void removePremiumUpsell() {
        boolean isDisabled = Pref.removePremiumUpsell(); // The return value is inverted
        if (isDisabled) {
            return;
        }
        addFlag("subscriptions_enabled", false);
        addFlag("subscriptions_upsells_get_verified_profile", false);
        addFlag("subscriptions_upsells_get_verified_drawer_discount_enabled", false);
        addFlag("subscriptions_upsells_get_verified_profile_fatigue_enabled", false);
        addFlag("subscriptions_upsells_api_enabled", false);
        addFlag("subscriptions_upsells_user_profile_name_migration_enabled", false);
        addFlag("subscriptions_upsells_profile_card_enable", false);
        addFlag("subscriptions_upsells_get_verified_drawer_card_enabled", false);
        addFlag("subscriptions_upsells_get_verified_profile_discount_visitor_enabled", false);
        addFlag("subscriptions_upsells_analytics_profile_enabled", false);
        addFlag("subscriptions_upsells_articles_post_composer_promo_variant_enabled", false);
        addFlag("subscriptions_upsells_bookmark_folders_enabled", false);
        addFlag("subscriptions_upsells_verified_profile_visitor_upsell_enabled", false);
        addFlag("subscriptions_upsells_verified_profile_visitor_upsell_redesign_enabled", false);
        addFlag("subscriptions_upsells_get_verified_profile_card", false);
        addFlag("subscriptions_upsells_get_verified_profile_discount_own_enabled", false);
        addFlag("subscriptions_upsells_get_verified_profile_rotation_enabled", false);
        addFlag("subscriptions_upsells_home_nav_migration_enabled", false);
        addFlag("subscriptions_upsells_profile_card_enabled", false);
        addFlag("subscriptions_upsells_quick_display_settings", false);
        addFlag("subscriptions_upsells_track_interactions_enabled", false);
        addFlag("subscriptions_upsells_user_profile_header_migration_enabled", false);

    }

    public static Object flagInfo(String flag, Object def) {
        try {
            if (def instanceof Boolean) {
                addFeatureFlagSearchItem(flag);
            }
            if (FLAGS.containsKey(flag)) {
                return FLAGS.get(flag);
            }
        } catch (Exception ex) {

        }
        return def;
    }

    public static void load() {
        String flags = Utils.getStringPref(Settings.MISC_FEATURE_FLAGS);
        if (!flags.isEmpty()) {
            for (String flag : flags.split(",")) {
                String[] item = flag.split(":");
                addFlag(item[0], Boolean.valueOf(item[1]));
            }
        }
    }
}
