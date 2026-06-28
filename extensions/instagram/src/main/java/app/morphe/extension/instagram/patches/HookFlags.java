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
        BOOL_FLAGS.put("56295", false); //ig_device_permission_consent
    }

    private static void simpleOverflowMenuFlags() {
        BOOL_FLAGS.put("104772", false); //ig_ini
        BOOL_FLAGS.put("117613::0", true); //ig_overflow_menu_icon::use_more_lines_icon
        BOOL_FLAGS.put("100002", true); //ig_igds_android_prism_overflow_sheet
    }
    private static void adsFlags() {
//        BOOL_FLAGS.put("58206::0", false); //is_acp_enabled
//        BOOL_FLAGS.put("72396::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::9", false); //enable_no_invalidation_reason_for_mae_exclusion
//        BOOL_FLAGS.put("79181::0", false); //ig_reels_ads_1x2_explore_halc_android::is_enabled
        BOOL_FLAGS.put("110800::0", false); //ig_android_controller_migration::use_v2_controller Removed in version 435.0.0.0.2
        BOOL_FLAGS.put("114983::0", false); //ig_stories_restyle_midcard::is_enable
        BOOL_FLAGS.put("95150::1", false); //ig_stories_music_midcard::is_enable
        BOOL_FLAGS.put("84366::12", false); //ig_stories_ayt_midcard::enable_add_yours
        BOOL_FLAGS.put("120110", false); //ig_android_scroll_break
        BOOL_FLAGS.put("105778::0", false); //ig_android_restyle_post_cap_promo_dialog::is_enable
    }

    // Thanks to @brosssh
    private static void suggestedContentFlags() {
        BOOL_FLAGS.put("111509::3", false); //ig_search_ta_nullstate_suggestions::is_android_enabled
        BOOL_FLAGS.put("82771::0", false); //igx_foundation_litho_stories_tray::is_litho_stories_tray_enabled
    }

    private static void profileActionBarFlags() {
        if(Pref.enableMoreOptionsOnProfileQuickToggle()) {
            BOOL_FLAGS.put("81826::0", true); //igx_action_bar_service_replacement::is_profile_replaced
            BOOL_FLAGS.put("89230::0", true); //ig_android_profile_overflow_menu_redesign_launcher:enabled
        }
    }

    private static void mainFeedActionBarFlags() {
        if(Pref.pikoSettingsOnActionBar()) {
            BOOL_FLAGS.put("81826::1", true); //igx_action_bar_service_replacement::is_main_feed_replaced
            BOOL_FLAGS.put("81826::4", true); //igx_action_bar_service_replacement::is_main_feed_large_screen_replaced
        }
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
            // Sometimes I want to block all the subflags inside a universal ID.
            // In which case I would only add the universal ID in the BOOL_MAP map.
            // If a boolean value is found then it will return else it will check for for the usual config ID
            String universalId = developerOptionsItem.getUniversalId();
            Boolean universalFlag = BOOL_FLAGS.getOrDefault(universalId, null);
            if(universalFlag!=null) return universalFlag;

            String configId = developerOptionsItem.getConfigId();
            return BOOL_FLAGS.getOrDefault(configId, null);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return null;
    }

}
