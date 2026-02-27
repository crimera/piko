package app.morphe.extension.instagram.patches;

import android.net.Uri;
import android.content.Context;
import android.content.Intent;
import java.net.URI;
import java.io.IOException;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;

public class Links {
    private static boolean DISABLE_ANALYTICS,VIEW_STORIES_ANONYMOUSLY,VIEW_LIVE_ANONYMOUSLY,DISABLE_STORIES,DISABLE_FEED,DISABLE_REELS,DISABLE_EXPLORE,DISABLE_COMMENTS,DISABLE_DISCOVER_PEOPLE;
    static {
        DISABLE_ANALYTICS = Pref.disableAnalytics() && SettingsStatus.disableAnalytics;
        VIEW_STORIES_ANONYMOUSLY = Pref.viewStoriesAnonymously() && SettingsStatus.viewStoriesAnonymously;
        VIEW_LIVE_ANONYMOUSLY = Pref.viewLiveAnonymously() && SettingsStatus.viewLiveAnonymously;
        DISABLE_STORIES = Pref.disableStories() && SettingsStatus.disableStories;
        DISABLE_FEED = Pref.disableFeed() && SettingsStatus.disableFeed;
        DISABLE_REELS = Pref.disableReels() && SettingsStatus.disableReels;
        DISABLE_EXPLORE = Pref.disableExplore() && SettingsStatus.disableExplore;
        DISABLE_COMMENTS = Pref.disableComments() && SettingsStatus.disableComments;
        DISABLE_DISCOVER_PEOPLE = Pref.disableDiscoverPeople() && SettingsStatus.disableDiscoverPeople;
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
                    openLink(actualUrl);
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
                } else if (path.endsWith("/feed/timeline/")) {
                    shouldBlockUri = DISABLE_FEED;
                } else if (path.endsWith("/qp/batch_fetch/")
                        || path.contains("api/v1/clips")
                        || path.contains("clips")
                        || path.contains("mixed_media")
                        || path.contains("mixed_media/discover/stream/")) {
                    shouldBlockUri = DISABLE_REELS;
                } else if (path.contains("/discover/topical_explore")
                        || path.contains("/discover/topical_explore_stream")
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/recent_searches/"))
                        || (host.contains("i.instagram.com") && path.contains("/fbsearch/top_serp/"))) {
                    shouldBlockUri = DISABLE_EXPLORE;
                } else if (path.contains("/api/v1/media/") && path.contains("comments/")) {
                    shouldBlockUri = DISABLE_COMMENTS;
                } else if (path.contains("/discover/ayml/")) {
                    shouldBlockUri = DISABLE_DISCOVER_PEOPLE;
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

}