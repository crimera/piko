package app.revanced.extension.twitter.patches;

import app.revanced.extension.twitter.Pref;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.twitter.settings.Settings;

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

    private static void hideGoogleAds() { addFlag("ssp_ads_dsp_client_context_enabled", !Pref.hideGoogleAds()); }

    private static void viewCount() {
        addFlag("view_counts_public_visibility_enabled", Pref.hideViewCount());
    }

    private static void bookmarkInTimeline() {
        addFlag("bookmarks_in_timelines_enabled", Pref.hideInlineBookmark());
    }

    private static void navbarFix() {
        addFlag("subscriptions_feature_1008", true);
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
