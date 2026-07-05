/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.instagram.settings.preference.fragments;


import android.content.Context;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.constants.Constants;

@SuppressWarnings("deprecation")
public class FragmentHook {

    public static void startFragment(String key){
        String actionBarTitleKey = null;

        if(key.equals(Constants.PIKO_FRAGMENT_ADS)){
            actionBarTitleKey = "piko_category_ads";
        }else if(key.equals(Constants.PIKO_FRAGMENT_GHOST)){
            actionBarTitleKey = "piko_category_ghost";
        }else if(key.equals(Constants.PIKO_FRAGMENT_LINKS)){
            actionBarTitleKey = "piko_category_links";
        }else if(key.equals(Constants.PIKO_FRAGMENT_DISTRACTION_FREE)){
            actionBarTitleKey = "piko_category_distraction_free";
        }else if(key.equals(Constants.PIKO_FRAGMENT_MISC)){
            actionBarTitleKey = "piko_category_misc";
        }else if(key.equals(Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA)){
            actionBarTitleKey = "piko_category_download_media";
        }else if(key.equals(Constants.PIKO_FRAGMENT_NAV_BTNS)){
            actionBarTitleKey = "piko_category_hide_navigation_buttons";
        }else if(key.equals(Constants.PIKO_FRAGMENT_DEV_OPTIONS)){
            actionBarTitleKey = "piko_category_dev_options";
        }else if(key.equals(Constants.PIKO_FRAGMENT_ABOUT)){
            actionBarTitleKey = "piko_category_about";
        }else if(key.equals(Constants.PIKO_FRAGMENT_ACTION_BAR)){
            actionBarTitleKey = "piko_category_action_bar";
        }

        if(actionBarTitleKey!=null){
            ActivityHook.startPikoActivity(key,actionBarTitleKey);
        }
    }

    public static void startSettings(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_SETTINGS,"piko_title_settings");
    }

}
