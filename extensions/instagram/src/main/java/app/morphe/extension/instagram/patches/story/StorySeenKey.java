/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import java.util.Objects;

final class StorySeenKey {
    private final String viewerId;
    private final String storyId;

    private StorySeenKey(String viewerId, String storyId) {
        this.viewerId = viewerId;
        this.storyId = storyId;
    }

    static StorySeenKey from(String viewerId, String storyId) {
        if (viewerId == null || viewerId.isEmpty() || storyId == null || storyId.isEmpty()) {
            return null;
        }
        return new StorySeenKey(viewerId, storyId);
    }

    String encode() {
        return viewerId.length() + ":" + viewerId + storyId;
    }

    static StorySeenKey decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            return null;
        }
        try {
            int viewerLength = Integer.parseInt(encoded.substring(0, separator));
            int viewerStart = separator + 1;
            int viewerEnd = viewerStart + viewerLength;
            if (viewerLength < 1 || viewerEnd >= encoded.length()) {
                return null;
            }
            return from(
                    encoded.substring(viewerStart, viewerEnd),
                    encoded.substring(viewerEnd)
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorySeenKey)) {
            return false;
        }
        StorySeenKey key = (StorySeenKey) other;
        return viewerId.equals(key.viewerId) && storyId.equals(key.storyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewerId, storyId);
    }
}
