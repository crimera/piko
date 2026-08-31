/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.instants;

import android.content.Context;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.db.PikoInstantsDb;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Records each view-once Instant as the app builds it, so it can be re-viewed and downloaded later
 * from {@link InstantsVaultActivity}.
 */
@SuppressWarnings("unused")
public class InstantsDownloadHook {

    /** The item constructor runs again whenever the app rebuilds an item. */
    private static volatile String sLastCapturedId;

    /** Called from the per-instant item's constructor with that instant's Media. */
    public static void noteInstantMedia(Object media) {
        try {
            if (media == null) return;
            if (!SharedPref.getBooleanPref(Settings.INSTANTS_DOWNLOAD)) return;

            captureMedia(media);
        } catch (Throwable t) {
            Logger.printException(() -> "instants capture: noteInstantMedia failed", t);
        }
    }

    private static void captureMedia(Object media) {
        try {
            MediaData md = new MediaData(media);

            String id;
            try {
                id = md.getMediaPkId();
            } catch (Throwable t) {
                return; // without a stable id it can't be deduped or referenced later
            }
            if (id == null || id.isEmpty() || id.equals(sLastCapturedId)) return;
            sLastCapturedId = id;

            Context ctx = Utils.getContext();
            if (ctx == null) return;
            PikoInstantsDb db = PikoInstantsDb.getInstance(ctx);
            if (db.has(id)) return;

            boolean isVideo = safeBool(() -> md.isVideo());
            String imageUrl = safeStr(() -> md.getImageLink());
            String videoUrl = isVideo ? safeStr(() -> md.getVideoLink()) : null;
            String username = safeStr(() -> md.getUserData().getUsername());
            String userId = safeStr(() -> md.getUserData().getUserId());

            db.insertOrIgnore(id, username, userId, imageUrl, videoUrl, isVideo);
        } catch (Throwable t) {
            Logger.printException(() -> "instants capture: captureMedia failed", t);
        }
    }

    // ---- best-effort helpers ----

    private interface StrCall { String get() throws Exception; }
    private interface BoolCall { boolean get() throws Exception; }

    private static String safeStr(StrCall c) {
        try { return c.get(); } catch (Throwable t) { return null; }
    }

    private static boolean safeBool(BoolCall c) {
        try { return c.get(); } catch (Throwable t) { return false; }
    }
}
