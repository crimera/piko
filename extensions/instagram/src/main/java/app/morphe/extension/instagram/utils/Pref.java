package app.morphe.extension.instagram.utils;

import app.morphe.extension.instagram.settings.Settings;

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
    public static boolean disableReels(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_REELS);
    }
    public static boolean disableFeed(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_FEED);
    }
    public static boolean disableExplore(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_EXPLORE);
    }
    public static boolean disableComments(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_COMMENTS);
    }

    public static boolean enableDevOptions(){
        return SharedPref.getBooleanPerf(Settings.DEVELOPER_OPTIONS);
    }

    public static long buildAge(){
        return SharedPref.getBooleanPerf(Settings.REMOVE_BUILD_EXPIRE_POPUP) ? 0:86400000 ;}

    public static boolean disableAnalytics(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_ANALYTICS);
    }
    public static boolean disableDiscoverPeople(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_DISCOVER_PEOPLE);
    }
    public static boolean followBackIndicator(){
        return SharedPref.getBooleanPerf(Settings.FOLLOW_BACK_INDICATOR);
    }
    public static boolean viewStoryMentions(){
        return SharedPref.getBooleanPerf(Settings.VIEW_STORY_MENTIONS);
    }
    //end
}