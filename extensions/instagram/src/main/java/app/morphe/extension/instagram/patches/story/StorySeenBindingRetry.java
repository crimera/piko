/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class StorySeenBindingRetry {
    interface FrameCallback {
        boolean onFrame();
    }

    interface FrameHost {
        boolean isAttached();

        void addFrameCallback(FrameCallback callback);

        void removeFrameCallback(FrameCallback callback);
    }

    private final FrameHost host;
    private final BooleanSupplier latestCapture;
    private final BooleanSupplier bindAttempt;
    private final Consumer<Boolean> onStopped;
    private final LongSupplier clock;
    private final int maxAttempts;
    private final long timeoutMillis;
    private final FrameCallback frameCallback = this::onFrame;
    private boolean active;
    private int attempts;
    private long startedAt;

    StorySeenBindingRetry(
            FrameHost host,
            BooleanSupplier latestCapture,
            BooleanSupplier bindAttempt,
            Consumer<Boolean> onStopped,
            LongSupplier clock,
            int maxAttempts,
            long timeoutMillis) {
        this.host = host;
        this.latestCapture = latestCapture;
        this.bindAttempt = bindAttempt;
        this.onStopped = onStopped;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.timeoutMillis = timeoutMillis;
    }

    boolean start() {
        if (active || !latestCapture.getAsBoolean()) {
            return false;
        }
        active = true;
        startedAt = clock.getAsLong();
        try {
            host.addFrameCallback(frameCallback);
            return true;
        } catch (Throwable ignored) {
            removeFrameCallbackSafely();
            active = false;
            notifyStopped(false);
            return false;
        }
    }

    void cancel() {
        stop(false);
    }

    private boolean onFrame() {
        if (!active) {
            return false;
        }
        try {
            if (!host.isAttached()
                    || !latestCapture.getAsBoolean()
                    || clock.getAsLong() - startedAt > timeoutMillis) {
                stop(false);
                return false;
            }

            attempts++;
            if (bindAttempt.getAsBoolean()) {
                stop(true);
                return true;
            } else if (attempts >= maxAttempts) {
                stop(false);
            }
        } catch (Throwable ignored) {
            stop(false);
        }
        return false;
    }

    private void stop(boolean completed) {
        if (!active) {
            return;
        }
        active = false;
        removeFrameCallbackSafely();
        notifyStopped(completed);
    }

    private void removeFrameCallbackSafely() {
        try {
            host.removeFrameCallback(frameCallback);
        } catch (Throwable ignored) {
            // The view may already be tearing down; listener removal is best-effort.
        }
    }

    private void notifyStopped(boolean completed) {
        try {
            onStopped.accept(completed);
        } catch (Throwable ignored) {
            // Cleanup failures must not interrupt Instagram's draw pass.
        }
    }
}
