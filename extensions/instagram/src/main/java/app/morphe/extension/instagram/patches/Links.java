/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches;

import android.net.Uri;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Arrays;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.PostType;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.crimera.PikoUtils;

import app.morphe.extension.instagram.settings.ActivityHook;

@SuppressWarnings("unused")
public class Links {
    private static final boolean DISABLE_ANALYTICS;
    private static final boolean DISABLE_STORIES;
    private static final boolean DISABLE_EXPLORE;
    private static final boolean DISABLE_COMMENTS;
    private static final boolean DISABLE_DISCOVER_PEOPLE;
    private static final boolean DISABLE_ADS;
    private static final boolean DISABLE_HIGHLIGHTS;
    private static final List<String> META_PACKAGES;

    static {
        DISABLE_ANALYTICS = Pref.disableAnalytics() && SettingsStatus.disableAnalytics;
        DISABLE_STORIES = Pref.disableStories() && SettingsStatus.disableStories;
        DISABLE_HIGHLIGHTS = Pref.disableHighlights() && SettingsStatus.disableHighlights;
        DISABLE_EXPLORE = Pref.disableExplore() && SettingsStatus.disableExplore;
        DISABLE_COMMENTS = Pref.disableComments() && SettingsStatus.disableComments;
        DISABLE_DISCOVER_PEOPLE = Pref.disableDiscoverPeople() && SettingsStatus.disableDiscoverPeople;
        DISABLE_ADS = Pref.disableAds() && SettingsStatus.disableAds;

        META_PACKAGES = Arrays.asList(
                "com.instagram.android",      // Instagram
                "com.instagram.lite",         // Instagram Lite
                "com.instagram.barcelona",    // Threads
                "com.instagram.basel",        // Edits
                "com.instagram.moonshot",     // Instants
                "com.whatsapp",               // WhatsApp
                "com.whatsapp.w4b",           // WhatsApp Business
                "com.facebook.katana",        // Facebook
                "com.facebook.lite",          // Facebook Lite
                "com.facebook.orca",          // Messenger
                "com.facebook.mlite",         // Messenger Lite
                "com.facebook.pages.app",     // Meta Business Suite
                "com.facebook.adsmanager",    // Meta Ads Manager
                "com.facebook.messengerkids"  // Messenger Kids
        );
    }

    public static boolean setStorySeen(boolean seenStatus){
        return Pref.viewStoriesAnonymously() ? true:seenStatus;
    }

    public static boolean openExternally(String url) {
        try {
            if(Pref.openLinksExternally()) {
                // https://l.instagram.com/?u=<actual url>&e=<tracking id>
                String actualUrl = Uri.parse(url).getQueryParameter("u");
                if (actualUrl != null) {
                    String sanitizedUrl = sanitizeUrl(actualUrl);
                    ActivityHook.openLink(sanitizedUrl);
                    return true;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "openExternally failure", ex);
        }
        return false;
    }

    // Thanks to InstaEclipse and InstaMoon.
    public static void interceptUri(URI uri) throws IOException{
       boolean shouldBlockUri = false;
        try {
            if (uri != null && uri.getPath() != null) {
                String host = uri.getHost();
                String path = uri.getPath();

                if (host.contains("graph.instagram.com")
                        || host.contains("graph.facebook.com")
                        || path.contains("/logging_client_events")) {
                    shouldBlockUri = DISABLE_ANALYTICS;
                } else if (path.contains("/api/v2/media/seen/")) {
                    shouldBlockUri = Pref.viewStoriesAnonymously();
                } else if (path.contains("/heartbeat_and_get_viewer_count/")) {
                    shouldBlockUri = Pref.viewLiveAnonymously();
                } else if (path.contains("/feed/reels_tray/")
                        || path.contains("feed/get_latest_reel_media/")
                        || path.contains("direct_v2/pending_inbox/?visual_message")
                        || path.contains("stories/hallpass/")
                        || path.contains("/api/v1/feed/reels_media_stream/")) {
                    shouldBlockUri = DISABLE_STORIES;
                } else if (path.contains("/discover/topical_explore")
                        || path.contains("/discover/topical_explore_stream")
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/recent_searches/"))
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/top_serp/"))) {
                    shouldBlockUri = DISABLE_EXPLORE;
                } else if (path.contains("/api/v1/media/") && path.contains("comments/")) {
                    shouldBlockUri = DISABLE_COMMENTS;
                } else if (path.contains("/discover/ayml") || path.contains("/discover/chaining")) { // Thanks to  @brosssh
                    shouldBlockUri = DISABLE_DISCOVER_PEOPLE;
                } else if (path.contains("profile_ads/get_profile_ads/")
                        || path.contains("/async_ads/")
                        || path.contains("/feed/injected_reels_media/")
                        || path.contains("/api/v1/ads/graphql/")) {
                    shouldBlockUri = DISABLE_ADS;
                } else if (path.contains("/highlights_tray")) {
                    shouldBlockUri = DISABLE_HIGHLIGHTS;
                }

            }

        } catch (Exception ex) {
            Logger.printException(() -> "intercept URI failed: ", ex);
        }
        // Exception is hanndled at call.
        if(shouldBlockUri) {
            throw new IOException("Block uri");
        }
    }

    public static String sanitizeUrl(String url){
        try{
            return url.replaceAll("([&?])igsh=[^&]*", "")
                    .replaceAll("([&?])utm_source=[^&]*", "")
                    .replaceAll("([&?])utm_medium=[^&]*", "")
                    .replaceAll("([&?])utm_content=[^&]*", "")
                    .replaceAll("([&?])fbclid=[^&]*", "")
                    .replaceAll("([&?])si=[^&]*", "");
        } catch (Exception e) {
            Logger.printException(() -> "sanitizeUrl failed: ", e);
        }
        return url;
    }

    public static boolean signatureCheck(Object appIdentityObject){
        try{
            Entity entity = new Entity(appIdentityObject);
            List<String> packageNames = (List) entity.getField("A02");
            if(packageNames!=null && packageNames.size() == 1){
                // Just in case the packagename is changed by the user.
                String currentAppPackageName = Utils.getContext().getPackageName();

                // The idea behind here is when the package name lists of app identity object
                // contains only one package name and that is similar to the application's package name (or any of meta app's),
                // we need to return true else false
                String appIdentifierPackageName = packageNames.get(0);
                if(appIdentifierPackageName.equals(currentAppPackageName) || META_PACKAGES.contains(appIdentifierPackageName)){
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "Handle signature failed: ", e);
        }
        return false;
    }

    public static String generatePostLink(Object mediaObject, int position) throws Exception {
        MediaData mediaData = new MediaData(mediaObject);

        String postShortCode = mediaData.getShortcode();
        PostType postType = mediaData.getPostType();

        String shortTag = "p";
        if(postType.equals(PostType.REEL)){
            shortTag = "reel";
        } else if(postType.equals(PostType.STORY)){
            shortTag = "stories";
            postShortCode = mediaData.getUserData().getUsername();
        }

        String link = String.format(Constants.INSTAGRAM_SHARE_LINK, shortTag, postShortCode);

        if(postType.equals(PostType.STORY)){
            String postID = mediaData.getPostID();
            link+=postID;
        } else if(postType.equals(PostType.CAROUSEL)){
            link+="?img_index="+String.valueOf(position+1);
        }
        return link;
    }

}
