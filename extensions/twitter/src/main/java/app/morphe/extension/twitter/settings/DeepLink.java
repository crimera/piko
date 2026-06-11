/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.app.Activity;
import android.os.Bundle;
import java.util.List;
import android.net.Uri;
import  app.morphe.extension.twitter.Utils;

public class DeepLink {
    private static final String bundleFlagNameKey = "flagName";
    private static final String bundleFlagValueKey = "flagValue";

    public static boolean deeplink(Activity act) {
        try {
            Uri deeplink = act.getIntent().getData();
            if (deeplink == null) {
                return false;
            }
            List<String> deeplinkSegments = deeplink.getPathSegments();
            int segmentSize = deeplinkSegments.size();

            if (deeplinkSegments.size() < 2) {
                return false;
            }

            String firstSegment = deeplinkSegments.get(0);
            if (!("i".equals(firstSegment) || "r".equals(firstSegment))) {
                return false;
            }

            String secondSegment = deeplinkSegments.get(1);
            if (secondSegment == null || secondSegment.isEmpty()) {
                return false;
            }

            String mainPath = secondSegment.toLowerCase();
            String lastSegment = deeplink.getLastPathSegment();

            boolean isPiko = mainPath.equals("piko") || mainPath.equals("pikosettings");

            String key = null;
            if (segmentSize == 2 && "settings".equals(lastSegment)){
                Utils.startXSettings();
                return true;
            } else if (segmentSize == 2 && isPiko) {
                ActivityHook.startSettingsActivity();
                return true;
            } else if (mainPath.equals("addflags")) {
                key = Settings.FEATURE_FLAGS;
                String bundleFlagName = deeplink.getQueryParameter("f");
                if (bundleFlagName != null) {
                    boolean bundleFlagValue = deeplink.getBooleanQueryParameter("v",true);
                    Bundle bundle = new Bundle();
                    bundle.putString(bundleFlagNameKey, bundleFlagName);
                    bundle.putBoolean(bundleFlagValueKey, bundleFlagValue);
                    ActivityHook.startActivity(key,bundle);
                    return true;
                }
            } else if (mainPath.equals("status") && "r".equals(firstSegment)) {
                String tweetId = deeplinkSegments.get(2);
                ActivityHook.startReaderMode(tweetId);
                return true;
            } else if (isPiko) {
                if ("premium".equals(lastSegment)){
                    key = Settings.PREMIUM_SECTION;
                } else if ("download".equals(lastSegment)) {
                    key = Settings.DOWNLOAD_SECTION;
                } else if ("flags".equals(lastSegment)) {
                    key = Settings.FLAGS_SECTION;
                } else if ("ads".equals(lastSegment)) {
                    key = Settings.ADS_SECTION;
                } else if ("native".equals(lastSegment)) {
                    key = Settings.NATIVE_SECTION;
                } else if ("misc".equals(lastSegment)) {
                    key = Settings.MISC_SECTION;
                } else if ("customise".equals(lastSegment) || "customize".equals(lastSegment)) {
                    key = Settings.CUSTOMISE_SECTION;
                } else if ("font".equals(lastSegment)) {
                    key = Settings.FONT_SECTION;
                } else if ("timeline".equals(lastSegment)) {
                    key = Settings.TIMELINE_SECTION;
                } else if ("pref".equals(lastSegment)) {
                    key = Settings.BACKUP_SECTION;
                } else if ("info".equals(lastSegment)) {
                    key = Settings.PATCH_INFO;
                }
            }
            if (key != null) {
                ActivityHook.startActivity(key);
                return true;
            }
        } catch (Exception e) {
            app.morphe.extension.crimera.PikoUtils.logger(e);
        }

        return false;
    }


}