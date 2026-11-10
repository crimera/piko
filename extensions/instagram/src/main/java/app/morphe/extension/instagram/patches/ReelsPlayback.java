/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.utils.Pref;

public final class ReelsPlayback {
    // Reels can keep adjacent players prepared. Only correct the player that left with this item.
    private static final WeakHashMap<Object, SavedStop> pending = new WeakHashMap<>();
    // Reel items compare equal by post ID even across different viewers/accounts.
    private static final WeakHashMap<Object, WeakHashMap<Object, SavedStop>> viewers = new WeakHashMap<>();
    private static Object settingsSession = new Object();
    private static final int START_DRIFT_MS = 200;
    private static final int REATTACH_SCROLL = 1;
    private static final int REATTACH_SAVED_POSITION = 2;

    private static final class SavedStop extends WeakReference<Integer> {
        final Object session = settingsSession;
        int reattachMode;
        boolean awaitingSelection = true;

        SavedStop(Integer position, boolean manuallyPaused) {
            super(position);
            reattachMode = manuallyPaused ? REATTACH_SAVED_POSITION : REATTACH_SCROLL;
        }
    }

    private ReelsPlayback() {}

    public static synchronized void onSettingChanged() {
        settingsSession = new Object();
        pending.clear();
    }

    private static SavedStop savedStop(Object viewer, Object item) {
        WeakHashMap<Object, SavedStop> written = viewers.get(viewer);
        return written == null ? null : written.get(item);
    }

    public static synchronized void onReelSelected(Object viewer, Object item) {
        SavedStop stop = savedStop(viewer, item);
        if (stop != null) stop.awaitingSelection = false;
    }

    public static synchronized void onPlayerReattached(Object viewer, Object player, Object item) {
        SavedStop stop = pending.get(player);
        if (stop != null && stop.awaitingSelection && stop == savedStop(viewer, item)) {
            stop.reattachMode = REATTACH_SAVED_POSITION;
        }
    }

    public static synchronized Integer onScrollOut(Object viewer, Object player, Object item,
                                                   Integer stoppedMs, Integer pausedMs, Integer storedMs,
                                                   Double durationSeconds) {
        WeakHashMap<Object, SavedStop> written = viewers.computeIfAbsent(viewer, key -> new WeakHashMap<>());
        Integer position = stoppedMs != null ? stoppedMs : pausedMs;
        SavedStop previous = written.get(item);
        if (position == null) {
            // Instagram also visits already stopped adjacent players on subsequent swipes.
            return previous != null && previous.session == settingsSession ? previous.get() : null;
        }
        // Instagram can stop the same player again after it has played offscreen.
        if (pausedMs == null && previous != null && previous.awaitingSelection
                && pending.get(player) == previous && storedMs != null && previous.get() == storedMs) {
            previous.reattachMode = REATTACH_SAVED_POSITION;
            return storedMs;
        }
        if (position <= 0 || durationSeconds == null || !(position < durationSeconds * 1000)) {
            written.remove(item);
            pending.remove(player);
            return null;
        }
        // The native setter keeps the old Integer object when the millisecond value is equal.
        Integer nativeStop = position.equals(storedMs) ? storedMs : position;
        SavedStop stop = new SavedStop(nativeStop, stoppedMs == null);
        pending.put(player, stop);
        written.put(item, stop);
        return nativeStop;
    }

    // Negative keeps Instagram's own value; zero discards a disabled stop; positive checks explicit starts.
    public static synchronized int savedStopState(Object viewer, Object item, Integer position) {
        SavedStop stop = savedStop(viewer, item);
        if (stop == null || position == null || stop.get() != position) return -1;
        return stop.session == settingsSession && Pref.resumeReelsAfterScrolling() ? 1 : 0;
    }

    public static synchronized int takeReattachMode(Object viewer, Object player, Object item) {
        SavedStop previous = pending.remove(player);
        if (previous == null || savedStop(viewer, item) != previous || !Pref.resumeReelsAfterScrolling()) return 0;
        return previous.reattachMode;
    }

    public static int reattachPosition(Integer stoppedMs, int currentMs, int durationMs, int mode) {
        if (stoppedMs == null || stoppedMs <= 0 || stoppedMs >= durationMs || currentMs < 0) return 0;
        // An adjacent player can start playing before its reel is selected again.
        if (mode == REATTACH_SAVED_POSITION) {
            return currentMs == 0 || Math.abs(currentMs - stoppedMs) > START_DRIFT_MS ? stoppedMs : 0;
        }
        // A reused player may have played a few frames from the beginning before the resume callback.
        boolean restarted = currentMs == 0
                || (currentMs > 0 && currentMs <= START_DRIFT_MS && stoppedMs - currentMs > START_DRIFT_MS);
        return mode == REATTACH_SCROLL && restarted ? stoppedMs : 0;
    }

    public static synchronized void discardReattach(Object player) {
        pending.remove(player);
    }
}
