package app.morphe.extension.instagram.settings;

public class SettingsStatus {
    public static boolean enableDeveloperOptions = false;
    public static void enableDeveloperOptions() {
        enableDeveloperOptions = true;
    }
    public static boolean removeBuildExpirePopup = false;
    public static void removeBuildExpirePopup() {
        removeBuildExpirePopup = true;
    }
    public static boolean developerOptionsSection() {
        return (enableDeveloperOptions || removeBuildExpirePopup);
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

    public static boolean openLinksExternally = false;
    public static void openLinksExternally() {
        openLinksExternally = true;
    }
    public static boolean linksSection() {
        return openLinksExternally;
    }

    public static void load() {
    }
}