/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import app.morphe.extension.shared.Utils;

public class VersionCheckPatch {
    private static String majorVersionName;

    private static String getMajorVersionName() {
        if (majorVersionName == null) {
            String versionName = Utils.getAppVersionName();
            // 11.95.0-alpha.0 > 11.95.0
            majorVersionName = versionName.split("-")[0];
        }

        return majorVersionName;
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean isVersionOrGreater(String version) {
        return getMajorVersionName().compareTo(version) >= 0;
    }

    public static final boolean IS_11_95_OR_GREATER = isVersionOrGreater("11.95.0");
}
