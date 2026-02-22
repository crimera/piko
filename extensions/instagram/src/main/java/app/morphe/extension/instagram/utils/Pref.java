package app.morphe.extension.instagram.utils;


import app.morphe.extension.instagram.settings.Settings;


@SuppressWarnings("unused")
public class Pref {

    public static boolean disableAds(){
        return SharedPref.getBooleanPerf(Settings.DISABLE_ADS);
    }
    public static boolean enableDevOptions(){
        return SharedPref.getBooleanPerf(Settings.DEVELOPER_OPTIONS);
    }
    //end
}