/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.shared.Utils;

final class StorySeenState {
    private static final String PREFERENCES_NAME = Constants.SHARED_PREF_NAME;
    private static final String MARKED_STORIES_KEY = "marked_story_keys_v2";
    private static final long HISTORY_TTL_MILLIS = 48L * 60L * 60L * 1_000L;
    private static final int MAX_HISTORY_SIZE = 256;
    private static final StorySeenHistory HISTORY =
            new StorySeenHistory(HISTORY_TTL_MILLIS, MAX_HISTORY_SIZE);
    private static boolean loaded;

    private StorySeenState() {
    }

    static synchronized boolean contains(StorySeenKey key) {
        ensureLoaded();
        return HISTORY.contains(key, System.currentTimeMillis());
    }

    static synchronized void remember(StorySeenKey key) {
        ensureLoaded();
        long now = System.currentTimeMillis();
        HISTORY.remember(key, now);
        SharedPreferences preferences = preferences();
        if (preferences != null) {
            preferences.edit()
                    .putStringSet(MARKED_STORIES_KEY, HISTORY.encode(now))
                    .apply();
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        SharedPreferences preferences = preferences();
        if (preferences == null) {
            return;
        }
        Set<String> persisted = preferences.getStringSet(
                MARKED_STORIES_KEY,
                Collections.emptySet()
        );
        HISTORY.load(new HashSet<>(persisted), System.currentTimeMillis());
        loaded = true;
    }

    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        return context == null
                ? null
                : context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}

final class StorySeenHistory {
    private final long ttlMillis;
    private final int maxEntries;
    private final Map<StorySeenKey, Long> entries = new LinkedHashMap<>();

    StorySeenHistory(long ttlMillis, int maxEntries) {
        if (ttlMillis < 0L || maxEntries < 1) {
            throw new IllegalArgumentException("Invalid story-seen history bounds");
        }
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
    }

    void load(Set<String> persisted, long now) {
        entries.clear();
        if (persisted != null) {
            for (String encoded : persisted) {
                Entry entry = decode(encoded);
                if (entry != null) {
                    entries.merge(entry.key, entry.timestamp, Math::max);
                }
            }
        }
        prune(now);
    }

    void remember(StorySeenKey key, long now) {
        if (key == null) {
            return;
        }
        entries.put(key, now);
        prune(now);
    }

    boolean contains(StorySeenKey key, long now) {
        prune(now);
        return key != null && entries.containsKey(key);
    }

    Set<String> encode(long now) {
        prune(now);
        Set<String> encoded = new HashSet<>();
        for (Map.Entry<StorySeenKey, Long> entry : entries.entrySet()) {
            encoded.add(entry.getValue() + ":" + entry.getKey().encode());
        }
        return encoded;
    }

    private void prune(long now) {
        entries.entrySet().removeIf(entry -> entry.getValue() + ttlMillis < now);
        while (entries.size() > maxEntries) {
            StorySeenKey oldestKey = null;
            long oldestTimestamp = Long.MAX_VALUE;
            for (Map.Entry<StorySeenKey, Long> entry : entries.entrySet()) {
                if (entry.getValue() < oldestTimestamp) {
                    oldestKey = entry.getKey();
                    oldestTimestamp = entry.getValue();
                }
            }
            entries.remove(oldestKey);
        }
    }

    private static Entry decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            return null;
        }
        try {
            long timestamp = Long.parseLong(encoded.substring(0, separator));
            StorySeenKey key = StorySeenKey.decode(encoded.substring(separator + 1));
            return timestamp < 0L || key == null ? null : new Entry(key, timestamp);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class Entry {
        private final StorySeenKey key;
        private final long timestamp;

        private Entry(StorySeenKey key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }
    }
}
