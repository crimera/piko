/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.utils;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.SettingsStatus;

@SuppressWarnings("unused")
public class Pref {
    public static boolean disableAds(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_ADS);
    }

    public static boolean hideSuggestedContent(){return SharedPref.getBooleanPerf(Settings.HIDE_SUGGESTED_CONTENT);}

    public static boolean openLinksExternally(){return SharedPref.getBooleanPerf(Settings.OPEN_LINKS_EXTERNALLY);}
    public static boolean sanitizeShareLinks(){return SharedPref.getBooleanPerf(Settings.SANITIZE_SHARE_LINKS);}
    public static boolean viewStoriesAnonymously(){return SharedPref.getBooleanPerf(Settings.VIEW_STORIES_ANONYMOUSLY);}
    public static boolean viewLiveAnonymously(){
        return SharedPref.getBooleanPerf(Settings.VIEW_LIVE_ANONYMOUSLY);
    }

    public static boolean disableStories(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_STORIES);
    }
    public static boolean disableExplore(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_EXPLORE);
    }
    public static boolean disableComments(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_COMMENTS);
    }
    public static boolean hideStoriesTray(){
        return SharedPref.getBooleanPerf(Settings.HIDE_STORIES_TRAY) && SettingsStatus.hideStoriesTray;
    }

    public static boolean enableDevOptions(){
        return SharedPref.getBooleanPerf(Settings.DEVELOPER_OPTIONS);
    }

    public static int buildAge(int appAge){
        return SharedPref.getBooleanPerf(Settings.REMOVE_BUILD_EXPIRE_POPUP) ? 1:appAge;}

    public static boolean disableAnalytics(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_ANALYTICS);
    }
    public static boolean disableDiscoverPeople(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_DISCOVER_PEOPLE);
    }
    public static boolean followBackIndicator(){
        return SharedPref.getBooleanPerf(Settings.FOLLOW_BACK_INDICATOR);
    }
    public static boolean disableStoryFlipping(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_STORY_FLIPPING);
    }
    public static boolean viewStoryMentions(){
        return SharedPref.getBooleanPerf(Settings.VIEW_STORY_MENTIONS);
    }
    public static String customiseStoryTimestamp(){
        return SharedPref.getStringPref(Settings.CUSTOMISE_STORY_TIMESTAMP);
    }


    public static boolean enableDownload(){
        return SharedPref.getBooleanPerf(Settings.ENABLE_DOWNLOAD);
    }
    public static boolean enableDirectDownload(){
        return SharedPref.getBooleanPerf(Settings.ENABLE_DIRECT_DOWNLOAD);
    }
    public static boolean downloadUsernameFolder(){
        return SharedPref.getBooleanPerf(Settings.DOWNLOAD_USERNAME_FOLDER);
    }

    //end
}