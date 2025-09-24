package app.revanced.extension.twitter.patches.links;

import android.annotation.SuppressLint;
import android.app.Activity;

import java.util.List;

@SuppressWarnings("unused")
@SuppressLint("DiscouragedApi")
public class HandleCustomDeepLinksPatch {
    private static List<String> customLinkHosts = null;

    public static void rewriteCustomDeepLinks(Activity activity) {
        var intent = activity.getIntent();

        var uri = intent.getData();
        if (uri == null) return;

        if (customLinkHosts == null) {
            int hostsResourceId = activity.getResources().getIdentifier(
                    "piko_custom_deeplink_hosts",
                    "array",
                    activity.getPackageName());
            String[] hosts = activity.getResources().getStringArray(hostsResourceId);

            customLinkHosts = List.of(hosts);
        }

        if (!customLinkHosts.contains(uri.getHost())) return;

        var newUri = uri.buildUpon()
                .authority("x.com")
                .build();

        intent.setData(newUri);
    }
}
