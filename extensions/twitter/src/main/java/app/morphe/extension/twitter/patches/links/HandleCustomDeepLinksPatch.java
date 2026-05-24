/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.links;

import android.annotation.SuppressLint;
import android.app.Activity;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
@SuppressLint("DiscouragedApi")
public class HandleCustomDeepLinksPatch {
    private static String[] customLinkHosts = null;

    public static void rewriteCustomDeepLinks(Activity activity) {
        var intent = activity.getIntent();

        var uri = intent.getData();
        if (uri == null) return;

        if (customLinkHosts == null)
            customLinkHosts = ResourceUtils.getStringArray("piko_custom_deeplink_hosts");

        String host = uri.getHost();

        for (String customHost : customLinkHosts) {
            if (host.endsWith(customHost)) {
                // Rewrite host
                var newUri = uri.buildUpon()
                        .authority("x.com")
                        .build();

                intent.setData(newUri);
                return;
            }
        }
    }
}
