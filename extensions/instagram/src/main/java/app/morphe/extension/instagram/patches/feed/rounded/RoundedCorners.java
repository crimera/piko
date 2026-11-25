/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed.rounded;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;

public final class RoundedCorners {
    static final int COVER_DEPTH = 8;
    static final float RADIUS_DP = 16f;

    private static final RoundedFrames kept = new RoundedFrames();
    private static final Map<View, Boolean> listened = Collections.synchronizedMap(new WeakHashMap<>());
    private static final View.OnLayoutChangeListener LAID_OUT =
            (frame, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> laidOut(frame);

    private RoundedCorners() {
    }

    static boolean wanted() {
        return Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_POST_CORNERS));
    }

    public static void made(Object holder) {
        try {
            StoryCorners.register();
            CarouselPeek.register();
            RoundedViewWatcher.ensureRegistered();
            Object frame = RoundedTargets.frame(holder);
            if (!(frame instanceof View)) return;
            View view = (View) frame;
            Activity owner = RoundedViewWatcher.ownerOf(view);
            if (owner != null) RoundedViewWatcher.joinAll(owner);
            if (!wanted()) {
                if (!kept.isEmpty()) kept.putBack();
                return;
            }
            if (listened.put(view, Boolean.TRUE) == null) view.addOnLayoutChangeListener(LAID_OUT);
            kept.round(view, RADIUS_DP * view.getResources().getDisplayMetrics().density);
            laidOut(view);
        } catch (Throwable ignored) {
        }
    }

    static void laidOut(View frame) {
        try {
            if (!wanted()) return;
            View cover = cover(frame);
            if (cover != null) {
                kept.round(cover, RADIUS_DP * cover.getResources().getDisplayMetrics().density);
            }
        } catch (Throwable ignored) {
        }
    }

    static View cover(View frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        if (width <= 0 || height <= 0) return null;
        View found = null;
        View at = frame;
        for (int depth = 0; depth < COVER_DEPTH; depth++) {
            ViewParent parent = at.getParent();
            if (!(parent instanceof ViewGroup) || parent instanceof AbsListView) break;
            ViewGroup group = (ViewGroup) parent;
            if (group.getClass().getName().endsWith("RecyclerView")) break;
            if (at.getLeft() != 0 || at.getTop() != 0 || group.getWidth() != width || group.getHeight() != height) break;
            if (group.getChildCount() > 1) found = group;
            at = group;
        }
        return found;
    }
}
