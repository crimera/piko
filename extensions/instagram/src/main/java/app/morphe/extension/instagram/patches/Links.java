/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches;

import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class Links {
    private static final boolean DISABLE_ANALYTICS;
    private static final boolean VIEW_STORIES_ANONYMOUSLY;
    private static final boolean VIEW_LIVE_ANONYMOUSLY;
    private static final boolean DISABLE_STORIES;
    private static final boolean DISABLE_EXPLORE;
    private static final boolean DISABLE_COMMENTS;
    private static final boolean DISABLE_DISCOVER_PEOPLE;
    private static final boolean DISABLE_ADS;
    private static final boolean DM_GHOST_MODE;

    static {
        DISABLE_ANALYTICS = Pref.disableAnalytics() && SettingsStatus.disableAnalytics;
        VIEW_STORIES_ANONYMOUSLY = Pref.viewStoriesAnonymously() && SettingsStatus.viewStoriesAnonymously;
        VIEW_LIVE_ANONYMOUSLY = Pref.viewLiveAnonymously() && SettingsStatus.viewLiveAnonymously;
        DISABLE_STORIES = Pref.disableStories() && SettingsStatus.disableStories;
        DISABLE_EXPLORE = Pref.disableExplore() && SettingsStatus.disableExplore;
        DISABLE_COMMENTS = Pref.disableComments() && SettingsStatus.disableComments;
        DISABLE_DISCOVER_PEOPLE = Pref.disableDiscoverPeople() && SettingsStatus.disableDiscoverPeople;
        DISABLE_ADS = Pref.disableAds() && SettingsStatus.disableAds;
        DM_GHOST_MODE = Pref.dmGhostMode() && SettingsStatus.dmGhostMode;

        FlagSecureBypass.init();
    }


    private static void openLink(String url) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Utils.getContext().startActivity(intent);
        } catch (Exception ex) {
            Logger.printException(() -> "openLink failure", ex);
        }
    }

    public static boolean openExternally(String url) {
        try {
            if(Pref.openLinksExternally()) {
                // https://l.instagram.com/?u=<actual url>&e=<tracking id>
                String actualUrl = Uri.parse(url).getQueryParameter("u");
                if (actualUrl != null) {
                    String sanitizedUrl = sanitizeUrl(actualUrl);
                    openLink(sanitizedUrl);
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
                    shouldBlockUri = VIEW_STORIES_ANONYMOUSLY;
                } else if (path.contains("/heartbeat_and_get_viewer_count/")) {
                    shouldBlockUri = VIEW_LIVE_ANONYMOUSLY;
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
                } else if (path.contains("/discover/ayml/")) {
                    shouldBlockUri = DISABLE_DISCOVER_PEOPLE;
                } else if (path.contains("profile_ads/get_profile_ads/")
                        || path.contains("/async_ads/")
                        || path.contains("/feed/injected_reels_media/")
                        || path.contains("/api/v1/ads/graphql/")) {
                    shouldBlockUri = DISABLE_ADS;
                } else if (path.matches(".*/direct_v2/threads/.*/items/.*/seen.*")) {
                    shouldBlockUri = DM_GHOST_MODE;
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
                // contains only one package name and that is similar to the application's package name,
                // we need to return true else false
                if(packageNames.get(0).equals(currentAppPackageName)){
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.printException(() -> "Handle signature failed: ", e);
        }
        return false;
    }

}
