/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.instants;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.instagram.db.PikoInstantsDb;
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
 * username and type. All obfuscated names are resolved at patch time (§11, see {@link #names()}).
 */
@SuppressWarnings("unused")
public class InstantsDownloadHook {

    // Indices into names(). The patch resolves entries by placeholder text, not position.
    private static final int N_MARKER = 0;
    private static final int N_VM_STATE_FIELD = 1;    // 5Ur -> A0Q (StateFlow-like, getValue() -> state)
    private static final int N_STATE_ITEM_METHOD = 2; // 5VB -> A03 () -> current item (5Xq)
    private static final int N_ITEM_MEDIA_FIELD = 3;  // 5Xq -> A01 (Lcom/instagram/feed/media/Media;)

    /** Value of N_MARKER once patch-time resolution has committed. */
    private static final String MARKER_RESOLVED = "1";

    /**
     * Obfuscated field/method names the capture path reflects on, resolved at patch time (§11) by
     * instantsDownloadPatch from the target dex. Each entry is a sentinel the resolver locates by
     * value and rewrites; there is no last-known-good fallback, since obfuscated names rotate on
     * every R8 run. If resolution didn't run the marker stays unset and capture no-ops.
     */
    private static String[] names() {
        return new String[] {
                "piko.instants.dl.marker",          // patched to "1" — commit flag, written last
                "piko.instants.dl.vmStateField",    // viewer VM state field (getValue() -> viewer state)
                "piko.instants.dl.stateItemMethod", // state method returning the current item
                "piko.instants.dl.itemMediaField",  // current item's Media field
        };
    }

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
            if (!MARKER_RESOLVED.equals(names()[N_MARKER])) return;
            if (!SharedPref.getBooleanPref(Settings.INSTANTS_DOWNLOAD)) return;

            Object media = currentInstantMedia(vm);
            if (media != null) captureMedia(media);
        } catch (Throwable t) {
            Logger.printException(() -> "instants capture: noteViewerVm failed", t);
        }
    }

    /** Reads the Media backing the instant currently shown, via the live VM. Null on any mismatch. */
    private static Object currentInstantMedia(Object vm) {
        try {
            String[] N = names();
            Object stateHolder = declaredField(vm, N[N_VM_STATE_FIELD]);              // LX/EuU
            if (stateHolder == null) return null;
            Object state = publicMethod(stateHolder, "getValue").invoke(stateHolder);  // LX/5VB
            if (state == null) return null;
            Object item = publicMethod(state, N[N_STATE_ITEM_METHOD]).invoke(state);   // LX/5Xq
            if (item == null) return null;
            return declaredField(item, N[N_ITEM_MEDIA_FIELD]);                         // Media
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
            Logger.printException(() -> "[piko] instants capture: saved "
                    + (isVideo ? "video" : "photo") + " from " + username);
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

    private static Object declaredField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static Method publicMethod(Object obj, String name) throws Exception {
        Method m = obj.getClass().getMethod(name);
        m.setAccessible(true);
        return m;
    }
}
