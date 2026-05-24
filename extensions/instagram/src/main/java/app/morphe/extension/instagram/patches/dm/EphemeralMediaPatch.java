/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class EphemeralMediaPatch {
    private static final boolean MAKE_EPHEMERAL_MEDIA_PERMANENT;
    private static final String PERMA_KEY = "permanent";

    static {
        MAKE_EPHEMERAL_MEDIA_PERMANENT = Pref.makeEphemeralMediaPermanent();
    }

    public static String makeEphemeralMediaPermanent(Long expireAt, String viewMode) {
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
