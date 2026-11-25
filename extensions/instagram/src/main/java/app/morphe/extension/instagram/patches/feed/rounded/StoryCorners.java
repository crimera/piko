/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed.rounded;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;

public final class StoryCorners {
    static final String FRAME = "reel_viewer_media_container";
    static final int MAX_VIEWS = 12000;
    static final float RADIUS_DP = 16f;

    private static final RoundedFrames rounded = new RoundedFrames();
    private static volatile int cachedId;

    private StoryCorners() {
    }

    static boolean wanted() {
        return Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ROUNDED_STORY_CORNERS));
    }

    static void register() {
        RoundedViewWatcher.register(MEMBER);
    }

    static void watch(Activity activity) {
        try {
            if (!RoundedViewWatcher.joined(activity, MEMBER)) {
                RoundedViewWatcher.join(activity, MEMBER);
            }
        } catch (Throwable ignored) {
        }
    }

    static void unwatch(Activity activity) {
        try {
            RoundedViewWatcher.leave(activity, MEMBER);
        } catch (Throwable ignored) {
        }
    }

    private static final RoundedViewWatcher.Member MEMBER = new RoundedViewWatcher.Member() {
        @Override
        public boolean preDraw(Activity activity, boolean laidOut) {
            try {
                if (!wanted()) {
                    if (!rounded.isEmpty()) rounded.putBack();
                    return false;
                }
                if (!laidOut) return false;
                int id = resolveId(activity);
                if (id == 0) return false;
                walk(activity.getWindow().getDecorView(), id, new int[]{MAX_VIEWS});
            } catch (Throwable ignored) {
            }
            return false;
        }

        private int resolveId(Activity activity) {
            if (cachedId != 0) return cachedId;
            int id = activity.getResources().getIdentifier(FRAME, "id", activity.getPackageName());
            if (id != 0) cachedId = id;
            return id;
        }

        private void walk(View view, int id, int[] budget) {
            if (budget[0]-- <= 0) return;
            if (view.getId() == id) {
                rounded.round(view, RADIUS_DP * view.getResources().getDisplayMetrics().density);
                return;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), id, budget);
            }
        }
    };
}
