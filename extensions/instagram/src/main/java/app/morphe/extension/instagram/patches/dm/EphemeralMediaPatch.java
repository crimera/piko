/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.SharedPreferences;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import com.instagram.common.session.UserSession;

@SuppressWarnings("unused")
public class EphemeralMediaPatch {
    private static final boolean MAKE_EPHEMERAL_MEDIA_PERMANENT;
    private static final String PERMA_KEY = "permanent";

    static {
        MAKE_EPHEMERAL_MEDIA_PERMANENT = Pref.makeEphemeralMediaPermanent();
    }

    public static boolean shouldSuppressEphemeralMediaReceipt(UserSession session, Object item) {
        if (!MAKE_EPHEMERAL_MEDIA_PERMANENT) return false;
        try {
            String key = localReadKey(session.getUserId(), item);
            SharedPreferences preferences = localReadPreferences();
            if (key != null && preferences != null) preferences.edit().putBoolean(key, true).apply();
        } catch (Exception ignored) { }
        return true;
    }

    public static boolean wasEphemeralMediaViewed(Object item, Object viewer) {
        if (!MAKE_EPHEMERAL_MEDIA_PERMANENT) return false;
        try {
            String key = localReadKey(new UserData(viewer).getUserId(), item);
            SharedPreferences preferences = localReadPreferences();
            return key != null && preferences != null && preferences.getBoolean(key, false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String localReadKey(String viewerId, Object item) {
        if (viewerId == null || viewerId.isEmpty() || item == null) return null;
        String itemId = new DirectItem(item).getItemId();
        return itemId == null || itemId.isEmpty() ? null : viewerId + ":" + itemId;
    }

    private static SharedPreferences localReadPreferences() {
        Context context = PikoUtils.getContext();
        // Suppressing the receipt preserves the image, but refresh resets the server's seen count.
        return context == null ? null : context.getSharedPreferences("piko_ephemeral_media_viewed", Context.MODE_PRIVATE);
    }

    public static String makeEphemeralMediaPermanent(Long expireAt, String viewMode) {
        EphemeralMediaCaption.record(expireAt, viewMode);
        try {
            if (expireAt == null || !MAKE_EPHEMERAL_MEDIA_PERMANENT) return viewMode;

            long currentTime = System.currentTimeMillis();
            long expireAtConv = Long.valueOf(expireAt) * 1000;

            // If current time is less than expire at time and the view mode isn't permanent
            // change the view mode to permanent.
            viewMode = currentTime <= expireAtConv && !viewMode.equals(PERMA_KEY) ? PERMA_KEY : viewMode;

        } catch (Exception e) {
            Logger.printException(() -> "error makeEphemeralMediaPermanent: " + e);
        }
        return viewMode;
    }

}
