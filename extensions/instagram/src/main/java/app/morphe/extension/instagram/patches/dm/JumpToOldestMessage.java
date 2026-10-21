/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/** Scrolls the open chat to its oldest message; Instagram loads older pages whenever the list reaches the top. */
public final class JumpToOldestMessage {
    private static final long SCROLL_STEP_MS = 60;
    private static final long TOP_POLL_MS = 250;
    private static final long TOP_IDLE_LIMIT_MS = 30000;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static boolean running;

    private JumpToOldestMessage() { }

    /** Starts the scroll, or cancels it if it is already running. */
    public static void toggle(View anchor) {
        try {
            if (running) {
                stop();
                Utils.showToastShort(str("piko_jump_oldest_cancelled"));
                return;
            }
            View list = anchor.getRootView().findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "message_list"));
            if (list == null || !list.isAttachedToWindow() || !list.isShown()) {
                return;
            }
            running = true;
            Utils.showToastShort(str("piko_jump_oldest_started"));
            step(list, 0);
        } catch (Throwable t) {
            stop();
            Logger.printException(() -> "JumpToOldestMessage.toggle failed", t);
        }
    }

    private static void stop() {
        running = false;
        HANDLER.removeCallbacksAndMessages(null);
    }

    private static void step(View list, long idleMs) {
        if (!running) return;
        try {
            if (!list.isAttachedToWindow() || !list.isShown()) {
                stop();
                return;
            }
            if (list.canScrollVertically(-1)) {
                list.scrollBy(0, -list.getHeight());
                HANDLER.postDelayed(() -> step(list, 0), SCROLL_STEP_MS);
            } else if (idleMs >= TOP_IDLE_LIMIT_MS) {
                // A quiet list cannot distinguish the oldest message from a stalled page load.
                stop();
                Utils.showToastShort(str("piko_jump_oldest_stopped"));
            } else {
                HANDLER.postDelayed(() -> step(list, idleMs + TOP_POLL_MS), TOP_POLL_MS);
            }
        } catch (Throwable t) {
            stop();
            Logger.printException(() -> "JumpToOldestMessage.step failed", t);
        }
    }
}
