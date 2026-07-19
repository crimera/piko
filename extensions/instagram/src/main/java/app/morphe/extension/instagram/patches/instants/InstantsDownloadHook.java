/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.instants;

import android.content.Context;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.instagram.db.PikoInstantsDb;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Runtime capture for the "Save Instants" feature. View-once instants (quicksnap) change on tap and
 * vanish after viewing, so rather than a per-instant button this passively records each viewed
 * instant's CDN links into {@link PikoInstantsDb} as the viewer surfaces it. The "Saved Instants"
 * screen ({@link InstantsVaultActivity}) lists them for viewing and download.
 *
 * <p>Capture reads the live viewer ViewModel: {@code vm.<A0Q>.getValue().<A03>() -> item.<A01>}
 * (a {@code Media}), then reuses piko's {@link MediaData} entity to pull the image/video url,
 * username and type. All obfuscated names are resolved at patch time (§11).
 *
 * <p>The obfuscated names are seeded as sentinel placeholders in {@link #names()}; instantsDownloadPatch
 * locates each by value and rewrites it from the target dex. They live in a returned array (not
 * static fields) because R8 minifies this extension and would constant-fold single-assigned string
 * fields away before the patch runs — the array-return form keeps them as real {@code const-string}s
 * for the patch to find. {@link Names} maps that array into named fields in one place, so nothing
 * downstream depends on array position. No last-known-good fallback — obfuscated names rotate on every
 * R8 run; if resolution didn't run the marker stays unset and capture no-ops.
 */
@SuppressWarnings("unused")
public class InstantsDownloadHook {

    /** Sentinel placeholders, rewritten at patch time by matching each value (not by position). */
    private static String[] names() {
        return new String[] {
                "piko.instants.dl.marker",          // commit flag — patched to "1" last
                "piko.instants.dl.vmStateField",    // viewer VM state field (getValue() -> viewer state)
                "piko.instants.dl.stateItemMethod", // state method returning the current item
                "piko.instants.dl.itemMediaField",  // current item's Media field
        };
    }

    /** Names as fields, so the reflection path never indexes the array by hand. The only place the
     *  array order is assumed is right here, next to {@link #names()}. */
    private static final class Names {
        final String marker, vmStateField, stateItemMethod, itemMediaField;

        Names(String[] a) {
            marker = a[0];
            vmStateField = a[1];
            stateItemMethod = a[2];
            itemMediaField = a[3];
        }
    }

    private static final Names NAMES = new Names(names());

    /** Value of {@link Names#marker} once patch-time resolution has committed. */
    private static final String MARKER_RESOLVED = "1";

    /** The last media id captured — avoids re-hitting the DB on every VM method call while the same
     *  instant is on screen (noteViewerVm fires very frequently). */
    private static volatile String sLastCapturedId;

    /**
     * Injected at the entry of the viewer ViewModel's instance methods (p0 = VM). Pulls the instant
     * currently on screen and records it. Fully guarded — any failure just skips this capture.
     */
    public static void noteViewerVm(Object vm) {
        try {
            if (vm == null) return;
            if (!MARKER_RESOLVED.equals(NAMES.marker)) return;
            if (!SharedPref.getBooleanPref(Settings.INSTANTS_DOWNLOAD)) return;

            Object media = currentInstantMedia(vm);
            if (media != null) captureMedia(media);
        } catch (Throwable t) {
            Logger.printException(() -> "instants capture: noteViewerVm failed", t);
        }
    }

    /** Reads the Media backing the instant currently shown, via the live VM, using the repo's
     *  {@link Entity} helper (getField for field steps, getMethod for the calls) rather than raw
     *  reflection. Null on any mismatch. */
    private static Object currentInstantMedia(Object vm) {
        try {
            Object stateHolder = new Entity(vm).getField(NAMES.vmStateField);          // LX/EuU
            if (stateHolder == null) return null;
            Object state = new Entity(stateHolder).getMethod("getValue");              // LX/5VB
            if (state == null) return null;
            Object item = new Entity(state).getMethod(NAMES.stateItemMethod);          // LX/5Xq
            if (item == null) return null;
            return new Entity(item).getField(NAMES.itemMediaField);                    // Media
        } catch (Throwable t) {
            // Off-cycle calls (VM alive but no current item) land here — expected, keep quiet.
            return null;
        }
    }

    /** Extracts the instant's links via piko's MediaData entity and stores them (deduped by id). */
    private static void captureMedia(Object media) {
        try {
            MediaData md = new MediaData(media, null);

            String id;
            try {
                id = md.getMediaPkId();
            } catch (Throwable t) {
                return; // no stable id -> can't dedupe or reference later
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

    // ---- reflection + best-effort helpers ----

    private interface StrCall { String get() throws Exception; }
    private interface BoolCall { boolean get() throws Exception; }

    private static String safeStr(StrCall c) {
        try { return c.get(); } catch (Throwable t) { return null; }
    }

    private static boolean safeBool(BoolCall c) {
        try { return c.get(); } catch (Throwable t) { return false; }
    }
}
