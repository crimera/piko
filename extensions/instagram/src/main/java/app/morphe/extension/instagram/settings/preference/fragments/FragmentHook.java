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
        if(key.equals(Constants.PIKO_FRAGMENT_ADS)){
            startAds();
        }else if(key.equals(Constants.PIKO_FRAGMENT_GHOST)){
            startGhost();
        }else if(key.equals(Constants.PIKO_FRAGMENT_LINKS)){
            startLinks();
        }else if(key.equals(Constants.PIKO_FRAGMENT_DISTRACTION_FREE)){
            startDistractionFree();
        }else if(key.equals(Constants.PIKO_FRAGMENT_MISC)){
            startMisc();
        }else if(key.equals(Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA)){
            startDownloadMedia();
        }else if(key.equals(Constants.PIKO_FRAGMENT_NAV_BTNS)){
            startHideNavBtn();
        }else if(key.equals(Constants.PIKO_FRAGMENT_DEV_OPTIONS)){
            startDevOptions();
        }else if(key.equals(Constants.PIKO_FRAGMENT_ABOUT)){
            startAbout();
        }
    }

    public static void startSettings(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_SETTINGS,"piko_title_settings");
    }

    private static void startAds(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_ADS,"piko_category_ads");
    }

    private static void startGhost(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_GHOST,"piko_category_ghost");
    }

    private static void startLinks(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_LINKS,"piko_category_links");
    }

    private static void startDistractionFree(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_DISTRACTION_FREE,"piko_category_distraction_free");
    }

    private static void startMisc(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_MISC,"piko_category_misc");
    }
    private static void startDownloadMedia(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_DOWNLOAD_MEDIA,"piko_category_download_media");
    }

    private static void startHideNavBtn(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_NAV_BTNS,"piko_category_hide_navigation_buttons");
    }

    private static void startDevOptions(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_DEV_OPTIONS,"piko_category_dev_options");
    }

    private static void startAbout(){
        ActivityHook.startPikoActivity(Constants.PIKO_FRAGMENT_ABOUT,"piko_category_about");
    }
}
