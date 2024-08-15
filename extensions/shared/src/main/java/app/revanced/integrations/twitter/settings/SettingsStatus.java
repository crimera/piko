package app.revanced.integrations.twitter.settings;

import app.revanced.integrations.twitter.patches.FeatureSwitchPatch;

public class SettingsStatus {
    public static boolean changeDownloadEnabled = false;
    public static boolean customSharingDomainEnabled = false;
    public static boolean enableFontMod = false;
    public static boolean hideRecommendedUsers = false;
    public static boolean hidePromotedTrend = false;
    public static boolean hideCommunityNote = false;
    public static boolean hideFAB = false;
    public static boolean hideFABBtns = false;
    public static boolean hideViewCount = false;
    public static boolean hideInlineBmk = false;
    public static boolean disableAutoTimelineScroll = false;
    public static boolean hideLiveThreads = false;
    public static boolean hideBanner = false;
    public static boolean showPollResultsEnabled = false;
    public static boolean browserChooserEnabled = false;
    public static boolean featureFlagsEnabled = false;
    public static boolean forceTranslate = false;
    public static boolean hidePromoteButton = false;
    public static boolean roundOffNumbers = false;
    public static boolean enableDebugMenu = false;

    public static boolean hideAds = false;
    public static boolean hideGAds = false;
    public static boolean hideMainEvent = false;
    public static boolean hideSuperheroEvent = false;
    public static boolean hideVideosForYou = false;
    public static boolean hideWTF = false;
    public static boolean hideCTS = false;
    public static boolean hideCTJ = false;
    public static boolean hideDetailedPosts = false;
    public static boolean hideRBMK = false;
    public static boolean hideRPinnedPosts = false;
    public static boolean hidePremiumPrompt = false;
    public static boolean hideHiddenReplies = false;
    public static boolean removePremiumUpsell = false;

    public static boolean enableReaderMode = false;
    public static boolean enableUndoPosts = false;
    public static boolean customAppIcon = false;
    public static boolean enableForcePip = false;

    public static boolean hideImmersivePlayer = false;
    public static boolean enableVidAutoAdvance = false;

    public static boolean profileTabCustomisation = false;
    public static boolean timelineTabCustomisation = false;
    public static boolean sideBarCustomisation = false;
    public static boolean navBarCustomisation = false;
    public static boolean inlineBarCustomisation = false;
    public static boolean mediaLinkHandle = false;
    public static boolean defaultReplySortFilter = false;

    public static boolean selectableText = false;
    public static boolean showSensitiveMedia = false;
    public static boolean enableVidDownload = false;
    public static boolean cleartrackingparams = false;
    public static boolean unshortenlink = false;
    public static boolean deleteFromDb = false;
    public static boolean nativeDownloader = false;

    public static void removePremiumUpsell() {
        removePremiumUpsell = true;
    }

    public static void enableVidAutoAdvance() {
        enableVidAutoAdvance = true;
    }

    public static void nativeDownloader() {
        nativeDownloader = true;
    }

    public static void deleteFromDb() {
        deleteFromDb = true;
    }

    public static void cleartrackingparams() {
        cleartrackingparams = true;
    }

    public static void unshortenlink() {
        unshortenlink = true;
    }

    public static void enableVidDownload() {
        enableVidDownload = true;
    }

    public static void showSensitiveMedia() {
        showSensitiveMedia = true;
    }

    public static void selectableText() {
        selectableText = true;
    }

    public static void enableDownloadFolder() {
        changeDownloadEnabled = true;
    }

    public static void mediaLinkHandle() {
        mediaLinkHandle = true;
    }

    public static void enableCustomSharingDomain() {
        customSharingDomainEnabled = true;
    }

    public static void enableFont() {
        enableFontMod = true;
    }

    public static void roundOffNumbers() {
        roundOffNumbers = true;
    }

    public static void enableFeatureFlags() {
        featureFlagsEnabled = true;
        FeatureSwitchPatch.getFeatureFlagSearchItems();
    }

    public static void enableBrowserChooser() {
        browserChooserEnabled = true;
    }

    public static void hideRecommendedUsers() {
        hideRecommendedUsers = true;
    }

    public static void hideCommunityNotes() {
        hideCommunityNote = true;
    }

    public static void hideFAB() {
        hideFAB = true;
    }

    public static void hideFABBtns() {
        hideFABBtns = true;
    }

    public static void hideViewCount() {
        hideViewCount = true;
    }

    public static void hideInlineBmk() {
        hideInlineBmk = true;
    }

    public static void disableAutoTimelineScroll() {
        disableAutoTimelineScroll = true;
    }

    public static void hideLiveThreads() {
        hideLiveThreads = true;
    }

    public static void hideBanner() {
        hideBanner = true;
    }

    public static void hidePremiumPrompt() {
        hidePremiumPrompt = true;
    }

    public static void enableShowPollResults() {
        showPollResultsEnabled = true;
    }

    public static void forceTranslate() {
        forceTranslate = true;
    }

    public static void hideHiddenReplies() {
        hideHiddenReplies = true;
    }

    public static void hidePromoteButton() {
        hidePromoteButton = true;
    }

    public static void enableDebugMenu() {
        enableDebugMenu = true;
    }


    public static void hideAds() {
        hideAds = true;
    }

    public static void hideGAds() {
        hideGAds = true;
    }

    public static void hideMainEvent() {
        hideMainEvent = true;
    }

    public static void hideVideosForYou() {
        hideVideosForYou = true;
    }

    public static void hideSuperheroEvent() {
        hideSuperheroEvent = true;
    }

    public static void hideWhoToFollow() {
        hideWTF = true;
    }

    public static void hideCreatorsToSub() {
        hideCTS = true;
    }

    public static void hideCommToJoin() {
        hideCTJ = true;
    }

    public static void hideDetailedPost() {
        hideDetailedPosts = true;
    }

    public static void hideRevistBookmark() {
        hideRBMK = true;
    }

    public static void hideRevistPinnedPost() {
        hideRPinnedPosts = true;
    }

    public static void hidePromotedTrends() {
        hidePromotedTrend = true;
    }

    public static void enableReaderMode() {
        enableReaderMode = true;
    }

    public static void enableUndoPosts() {
        enableUndoPosts = true;
    }

    public static void customAppIcon() {
        customAppIcon = true;
    }

    public static void enableForcePip() {
        enableForcePip = true;
    }

    public static void hideImmersivePlayer() {
        hideImmersivePlayer = true;
    }

    public static void profileTabCustomisation() {
        profileTabCustomisation = true;
    }

    public static void timelineTabCustomisation() {
        timelineTabCustomisation = true;
    }

    public static void sideBarCustomisation() {
        sideBarCustomisation = true;
    }

    public static void navBarCustomisation() {
        navBarCustomisation = true;
    }

    public static void defaultReplySortFilter() {
        defaultReplySortFilter = true;
    }

    public static void inlineBarCustomisation() {
        inlineBarCustomisation = true;
    }

    public static boolean enableTimelineSection() {
        return ( disableAutoTimelineScroll || forceTranslate || hidePromoteButton || hideCommunityNote || hideLiveThreads || hideBanner || hideInlineBmk || showPollResultsEnabled || hideImmersivePlayer || enableVidAutoAdvance);
    }

    public static boolean enableMiscSection() {
        return (roundOffNumbers || enableFontMod || hideRecommendedUsers || hideFAB || hideViewCount || customSharingDomainEnabled || hideFABBtns);
    }

    public static boolean enableAdsSection() {
        return (hideAds || hideGAds || hideWTF || hideCTS || hideCTJ || hideDetailedPosts || hideRBMK || hidePromotedTrend || removePremiumUpsell || hideMainEvent || hideSuperheroEvent || hideVideosForYou);
    }

    public static boolean enableDownloadSection() {
        return (nativeDownloader || changeDownloadEnabled || mediaLinkHandle);
    }

    public static boolean enablePremiumSection() {
        return (enableReaderMode || enableUndoPosts || customAppIcon || enableForcePip);
    }

    public static boolean enableCustomisationSection() {
        return (inlineBarCustomisation || navBarCustomisation || sideBarCustomisation || profileTabCustomisation || timelineTabCustomisation || defaultReplySortFilter);
    }

    public static void load() {
    }
}
