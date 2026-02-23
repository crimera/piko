package app.morphe.extension.instagram.settings;

public class SettingsStatus {
    public static boolean enableDeveloperOptions = false;
    public static void enableDeveloperOptions() {
        enableDeveloperOptions = true;
    }
    public static boolean developerOptionsSection() {
        return enableDeveloperOptions;
    }

    //Ads Section
    public static boolean disableAds = false;
    public static void disableAds() {
        disableAds = true;
    }

    public static boolean hideSuggestedContent = false;
    public static void hideSuggestedContent() {
        hideSuggestedContent = true;
    }
    public static boolean adsSection() {
        return (disableAds || hideSuggestedContent);
    }

    public static void load() {
    }
}