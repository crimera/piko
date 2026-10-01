/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;

/** Where each account's profile cover was moved and zoomed. */
public class ProfileCoverPref extends BaseSharedPref {

    private static final ProfileCoverPref INSTANCE = new ProfileCoverPref();
    private static final String KEY_ZOOM = "zoom";
    private static final String KEY_OFFSET_X = "offset_x";
    private static final String KEY_OFFSET_Y = "offset_y";

    private ProfileCoverPref() {
        super("piko_profile_cover");
    }

    // BaseSharedPref has no float accessors, so the values are stored as strings.
    private static float getFloat(String accountId, String key, float defaultValue) {
        try {
            return Float.parseFloat(INSTANCE.getString(accountId + ":" + key, ""));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static float zoom(String accountId) {
        return getFloat(accountId, KEY_ZOOM, 1f);
    }

    public static float offsetX(String accountId) {
        return getFloat(accountId, KEY_OFFSET_X, 0f);
    }

    public static float offsetY(String accountId) {
        return getFloat(accountId, KEY_OFFSET_Y, 0f);
    }

    public static void save(String accountId, float zoom, float offsetX, float offsetY) {
        INSTANCE.setString(accountId + ":" + KEY_ZOOM, String.valueOf(zoom));
        INSTANCE.setString(accountId + ":" + KEY_OFFSET_X, String.valueOf(offsetX));
        INSTANCE.setString(accountId + ":" + KEY_OFFSET_Y, String.valueOf(offsetY));
    }

    public static void reset(String accountId) {
        save(accountId, 1f, 0f, 0f);
    }
}
