package app.morphe.extension.instagram.settings;

public class SettingsStatus {
    // Developer section.
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

    //Ads section.
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

    //Links section.
    public static boolean openLinksExternally = false;
    public static void openLinksExternally() {
        openLinksExternally = true;
    }
    public static boolean linksSection() {
        return openLinksExternally;
    }


    public static boolean viewStoriesAnonymously = false;
    public static void viewStoriesAnonymously() {
        viewStoriesAnonymously = true;
    }
    public static boolean ghostSection() {
        return viewStoriesAnonymously;
    }

    //Misc section.
    public static boolean disableAnalytics = false;
    public static void disableAnalytics() {
        disableAnalytics = true;
    }
    public static boolean miscSection() {
        return disableAnalytics;
    }
    public static void load() {
    }
}