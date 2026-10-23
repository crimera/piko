/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.patches.focusLock.FocusLock;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public final class ProfileHighlights {
    private static final Map<View, Integer> NATIVE_VISIBILITY = new WeakHashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable REFRESH = ProfileHighlights::refresh;
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            (preferences, key) -> {
                if (key == null || Settings.DISABLE_HIGHLIGHTS.key.equals(key)
                        || key.startsWith("focus_lock_")) {
                    Utils.runOnMainThread(REFRESH);
                }
            };
    private static boolean listenerRegistered;

    private ProfileHighlights() {
    }

    private static boolean isHighlightsTray(View view) {
        int id = ResourceUtils.getIdentifier(ResourceType.ID, "highlights_tray");
        return view != null && id != 0 && view.getId() == id;
    }

    public static void beforeBind(View view) {
        if (!isHighlightsTray(view)) return;
        Integer visibility = NATIVE_VISIBILITY.get(view);
        // Some native bind paths leave visibility unchanged. Restore their input state first
        // so our GONE value is never mistaken for an empty tray on the next bind.
        if (visibility != null) view.setVisibility(visibility);
    }

    public static void afterBind(View view) {
        if (!isHighlightsTray(view)) return;
        if (!NATIVE_VISIBILITY.containsKey(view)) observeWindowFocus(view);
        NATIVE_VISIBILITY.put(view, view.getVisibility());
        boolean hidden = shouldHide();
        if (!listenerRegistered) {
            listenerRegistered = SharedPref.registerOnSharedPreferenceChangeListener(LISTENER);
        }
        if (hidden) view.setVisibility(View.GONE);
        scheduleLockExpiry();
    }

    private static void observeWindowFocus(View view) {
        // Delayed handlers pause during deep sleep; recheck the wall-clock lock expiry on wake.
        ViewTreeObserver.OnWindowFocusChangeListener listener = focused -> {
            if (focused) refresh();
        };
        view.getViewTreeObserver().addOnWindowFocusChangeListener(listener);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View attached) {
                ViewTreeObserver observer = attached.getViewTreeObserver();
                observer.removeOnWindowFocusChangeListener(listener);
                observer.addOnWindowFocusChangeListener(listener);
            }

            @Override
            public void onViewDetachedFromWindow(View detached) {
                detached.getViewTreeObserver().removeOnWindowFocusChangeListener(listener);
            }
        });
    }

    private static boolean shouldHide() {
        return Pref.disableHighlights() || FocusLock.isForced(Settings.DISABLE_HIGHLIGHTS);
    }

    private static void refresh() {
        boolean hidden = shouldHide();
        for (Map.Entry<View, Integer> entry : NATIVE_VISIBILITY.entrySet()) {
            View view = entry.getKey();
            if (isHighlightsTray(view)) {
                view.setVisibility(hidden ? View.GONE : entry.getValue());
            }
        }
        scheduleLockExpiry();
    }

    private static void scheduleLockExpiry() {
        MAIN_HANDLER.removeCallbacks(REFRESH);
        if (!NATIVE_VISIBILITY.isEmpty() && FocusLock.isForced(Settings.DISABLE_HIGHLIGHTS)) {
            MAIN_HANDLER.postDelayed(
                    REFRESH,
                    Math.max(1L, FocusLock.lockedUntil() - System.currentTimeMillis())
            );
        }
    }
}
