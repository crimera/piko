package app.morphe.extension.instagram.patches;

import android.net.Uri;
import android.content.Context;
import android.content.Intent;
import java.net.URI;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.settings.SettingsStatus;

public class Links {
    private static boolean DISABLE_ANALYTICS,VIEW_STORIES_ANONYMOUSLY,VIEW_LIVE_ANONYMOUSLY;
    static {
        DISABLE_ANALYTICS = Pref.disableAnalytics() && SettingsStatus.disableAnalytics;
        VIEW_STORIES_ANONYMOUSLY = Pref.viewStoriesAnonymously() && SettingsStatus.viewStoriesAnonymously;
        VIEW_LIVE_ANONYMOUSLY = Pref.viewLiveAnonymously() && SettingsStatus.viewLiveAnonymously;
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

    // Thanks to InstaEclipse and other mods.
    public static URI interceptUri(URI uri){
        boolean shouldBlockUri = false;
        try {
            if (uri != null && uri.getPath() != null) {

                String host = uri.getHost();
                String path = uri.getPath();

                if (host.contains("graph.instagram.com") || host.contains("graph.facebook.com") || path.contains("/logging_client_events")){
                    shouldBlockUri = DISABLE_ANALYTICS;
                }else if(path.contains("/api/v2/media/seen/")){
                    shouldBlockUri = VIEW_STORIES_ANONYMOUSLY;
                }else if(path.contains("/heartbeat_and_get_viewer_count/")){
                    shouldBlockUri = VIEW_LIVE_ANONYMOUSLY;
                }
            }


        } catch (Exception ex) {
            Logger.printException(() -> "intercept URI failed: ", ex);
        }
        // returns dummy 404 uri if the uri needs to be blocked.
        return shouldBlockUri ? URI.create("https://127.0.0.1/piko") : uri;
    }

}