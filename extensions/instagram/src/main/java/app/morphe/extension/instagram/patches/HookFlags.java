/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
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
        BOOL_FLAGS.put("104772::6", false); //enable_reduced_options
    }

    private static void adsFlags() {
//        BOOL_FLAGS.put("58206::0", false); //is_acp_enabled
//        BOOL_FLAGS.put("72396::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::0", false); //is_mae_exclusion_feed_enabled
//        BOOL_FLAGS.put("78046::9", false); //enable_no_invalidation_reason_for_mae_exclusion
//        BOOL_FLAGS.put("79181::0", false); //ig_reels_ads_1x2_explore_halc_android::is_enabled
        BOOL_FLAGS.put("110800::0", false); //ig_android_controller_migration::use_v2_controller
    }

    private static void employeeOptionsFlags() {
        if(Pref.enableEmployeeOptions()){
            BOOL_FLAGS.put("28538::0", true); //ig_android_employee_options::is_enabled
        }else{
            BOOL_FLAGS.put("28538::0", false); //ig_android_employee_options::is_enabled
        }
    }

    private static void whitehatSettingsFlags() {
        if(Pref.enableWhitehatSettings()){
            BOOL_FLAGS.put("115529::0", true); //whitehat_settings_android_ig::show_settings
        }else{
            BOOL_FLAGS.put("115529::0", false); //whitehat_settings_android_ig::show_settings
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
