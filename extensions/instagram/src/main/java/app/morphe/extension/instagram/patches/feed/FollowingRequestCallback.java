/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed;

/** Receives one user list response. Its superclass is set at patch time to Instagram's request callback. */
@SuppressWarnings("unused")
public class FollowingRequestCallback {
    volatile Object response;

    /** Renamed at patch time to the callback's success method. */
    public void onRequestSuccess(Object response) {
        this.response = response;
    }
}
