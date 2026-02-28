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
    public static boolean sanitizeShareLinks = false;
    public static void sanitizeShareLinks() {sanitizeShareLinks = true;}
    public static boolean linksSection() {
        return (openLinksExternally || sanitizeShareLinks);
    }


    public static boolean viewStoriesAnonymously = false;
    public static void viewStoriesAnonymously() {
        viewStoriesAnonymously = true;
    }
    public static boolean viewLiveAnonymously = false;
    public static void viewLiveAnonymously() {
        viewLiveAnonymously = true;
    }
    public static boolean ghostSection() {
        return (viewStoriesAnonymously || viewLiveAnonymously);
    }


    public static boolean disableStories = false;
    public static void disableStories() {
        disableStories = true;
    }
    public static boolean disableFeed = false;
    public static void disableFeed() {
        disableFeed = true;
    }
    public static boolean disableReels = false;
    public static void disableReels() {
        disableReels = true;
    }
    public static boolean disableExplore = false;
    public static void disableExplore() {
        disableExplore = true;
    }
    public static boolean disableComments = false;
    public static void disableComments() {
        disableComments = true;
    }
    public static boolean distractionFreeSection() {
        return (disableStories || disableFeed || disableReels || disableExplore || disableComments);
    }

    //Misc section.
    public static boolean disableAnalytics = false;
    public static void disableAnalytics() {disableAnalytics = true;}
    public static boolean disableDiscoverPeople = false;
    public static void disableDiscoverPeople() {
        disableDiscoverPeople = true;
    }
    public static boolean followBackIndicator = false;
    public static void followBackIndicator() {
        followBackIndicator = true;
    }
    public static boolean miscSection() {return (disableAnalytics || disableDiscoverPeople || followBackIndicator);}
    public static void load() {
    }
}