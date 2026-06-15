/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches;

import java.util.Map;
import java.util.HashMap;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.DeveloperOptions;
import app.morphe.extension.instagram.entity.DeveloperOptionsItem;
import app.morphe.extension.instagram.utils.Pref;

public class HookFlags {
    private static Map<String, Boolean> BOOL_FLAGS = new HashMap<>();
    private static DeveloperOptions developerOptions = new DeveloperOptions();

    private static void contactPermissionConsentFlags() {
        BOOL_FLAGS.put("56295::14", false); //igios_enable_native_new_reconsider_alert_content
        BOOL_FLAGS.put("56295::15", false); //ig4a_enable_native_new_reconsider_alert_content
        BOOL_FLAGS.put("56295::29", false); //igios_reconsideration_dialog_for_location_suggestion_hscroll_view
        BOOL_FLAGS.put("56295::30", false); //igios_reconsideration_dialog_for_prmote_audience_ui_helper
    }

    private static void simpleOverflowMenuFlags() {
        BOOL_FLAGS.put("104772::0", false); //enable_simple_overflow_menu
        BOOL_FLAGS.put("104772::1", false); //enable_feed_menu
        BOOL_FLAGS.put("104772::2", false); //enable_reels_menu
        BOOL_FLAGS.put("104772::3", false); //enable_tombstone_redesign
        BOOL_FLAGS.put("104772::4", false); //enable_non_destructive_report
        BOOL_FLAGS.put("104772::5", false); //enable_non_destructive_styling
        BOOL_FLAGS.put("104772::6", false); //enable_reduced_options
        BOOL_FLAGS.put("104772::7", false); //ig_ini_nice
        BOOL_FLAGS.put("104772::10", false); //enable_bottom_profrile_intergrity_options
        BOOL_FLAGS.put("104772::11", false); //enable_dynamic_chips_for_nice
        BOOL_FLAGS.put("104772::12", false); //enable_cc_not_interested
        BOOL_FLAGS.put("104772::13", false); //enable_nice_variant_b
        BOOL_FLAGS.put("104772::14", false); //enable_nice_variant_full_screen
        BOOL_FLAGS.put("104772::15", false); //enable_ini_refresh
        BOOL_FLAGS.put("104772::16", false); //enable_reduced_options_reels
        BOOL_FLAGS.put("104772::17", false); //enable_reduced_options_feed
        BOOL_FLAGS.put("104772::18", false); //enable_follow_options_fix
    }

    private static void adsFlags() {
//        BOOL_FLAGS.put("58206::0", false); //is_acp_enabled
//        BOOL_FLAGS.put("72396::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::9", false); //enable_no_invalidation_reason_for_mae_exclusion
//        BOOL_FLAGS.put("79181::0", false); //ig_reels_ads_1x2_explore_halc_android::is_enabled
        BOOL_FLAGS.put("110800::0", false); //ig_android_controller_migration::use_v2_controller
    }

    // Thanks to @brosssh
    private static void suggestedContentFlags() {
        BOOL_FLAGS.put("111509::3", false); //ig_search_ta_nullstate_suggestions::is_android_enabled
        BOOL_FLAGS.put("82771::0", false); //igx_foundation_litho_stories_tray::is_litho_stories_tray_enabled
    }

    private static void profileActionBarFlags() {
        BOOL_FLAGS.put("81826::0", true); //igx_action_bar_service_replacement::is_profile_replaced
    }

    private static void mainFeedActionBarFlags() {
        BOOL_FLAGS.put("81826::1", true); //igx_action_bar_service_replacement::is_main_feed_replaced
        BOOL_FLAGS.put("81826::4", true); //igx_action_bar_service_replacement::is_main_feed_large_screen_replaced
    }

    private static void employeeOptionsFlags() {
        if(Pref.enableEmployeeOptions()){
            BOOL_FLAGS.put("28538::0", true); //ig_android_employee_options::is_enabled
        }else{
            BOOL_FLAGS.put("28538::0", false); //ig_android_employee_options::is_enabled
        }
    }

    public static void load() {
    }

    public static Boolean handleBoolFlags(long mobileConfigSpecifier) {
        try {
            DeveloperOptionsItem developerOptionsItem = new DeveloperOptionsItem(mobileConfigSpecifier);
            String configId = developerOptionsItem.getConfigId();
            return BOOL_FLAGS.getOrDefault(configId, null);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return null;
    }

}
