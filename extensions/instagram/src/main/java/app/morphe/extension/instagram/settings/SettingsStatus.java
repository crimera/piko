/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


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
    public static boolean disableExplore = false;
    public static void disableExplore() {
        disableExplore = true;
    }
    public static boolean disableComments = false;
    public static void disableComments() {
        disableComments = true;
    }
    public static boolean hideStoriesTray = false;
    public static void hideStoriesTray() {
        hideStoriesTray = true;
    }
    public static boolean limitFollowingFeed = false;
    public static void limitFollowingFeed() {
        limitFollowingFeed = true;
    }
    public static boolean hideGroupCreationOnSharesheet = false;
    public static void hideGroupCreationOnSharesheet() {
        hideGroupCreationOnSharesheet = true;
    }
    public static boolean distractionFreeSection() {
        return (disableStories || disableExplore || disableComments || hideStoriesTray || limitFollowingFeed || hideGroupCreationOnSharesheet);
    }

    //Misc section.
    public static boolean disableAnalytics = false;
    public static void disableAnalytics() { disableAnalytics = true; }
    public static boolean disableDiscoverPeople = false;
    public static void disableDiscoverPeople() {
        disableDiscoverPeople = true;
    }
    public static boolean followBackIndicator = false;
    public static void followBackIndicator() { followBackIndicator = true; }
    public static boolean viewStoryMentions = false;
    public static void viewStoryMentions() {
        viewStoryMentions = true;
    }
    public static boolean disableStoryFlipping = false;
    public static void disableStoryFlipping() {
        disableStoryFlipping = true;
    }
    public static boolean customiseStoryTimestamp = false;
    public static void customiseStoryTimestamp() {
        customiseStoryTimestamp = true;
    }
    public static boolean unlimitedReplaysOnEphemeralMedia = false;
    public static void unlimitedReplaysOnEphemeralMedia() {
        unlimitedReplaysOnEphemeralMedia = true;
    }
    public static boolean improveImageViewing = false;
    public static void improveImageViewing() {
        improveImageViewing = true;
    }
    public static boolean hideReshareButton = false;
    public static void hideReshareButton() {
        hideReshareButton = true;
    }
    public static boolean miscSection() {return (improveImageViewing || unlimitedReplaysOnEphemeralMedia || customiseStoryTimestamp || disableAnalytics || disableDiscoverPeople || followBackIndicator || viewStoryMentions || disableStoryFlipping || hideReshareButton);}

    //Download section.
    public static boolean downloadMedia = false;
    public static void downloadMedia() {downloadMedia = true;}

    public static boolean hideNavigationButtons = false;
    public static void hideNavigationButtons() { hideNavigationButtons = true; }

    public static void load() {
    }
}
