/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.postfilter;

import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.settings.SettingsStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PostFilterPreferences {
    public static final int MAX_KEYWORD_LENGTH = 256;

    private static volatile Set<String> cachedSource = Collections.emptySet();
    private static volatile List<String> cachedNormalized = Collections.emptyList();
    private static volatile boolean cacheInitialized;

    private PostFilterPreferences() {}

    public static boolean isEnabled() {
        return SettingsStatus.postFilter && Utils.getBooleanPref(Settings.POST_FILTER_ENABLED);
    }

    public static List<String> getKeywords() {
        Set<String> stored = Utils.getSetPref(
                Settings.POST_FILTER_KEYWORDS.key,
                Collections.emptySet());
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(stored));
    }

    public static void saveKeywords(Collection<String> keywords) {
        if (keywords == null) throw new IllegalArgumentException("Keywords cannot be null");

        Set<String> values = new LinkedHashSet<>();
        Set<String> normalizedValues = new HashSet<>();
        for (String keyword : keywords) {
            String value = validate(keyword);
            String normalized = PostFilterMatcher.normalize(value);
            if (!normalizedValues.add(normalized)) {
                throw new IllegalArgumentException("Duplicate keyword: " + value);
            }
            values.add(value);
        }

        if (!Utils.setSetPref(Settings.POST_FILTER_KEYWORDS.key, new HashSet<>(values))) {
            throw new IllegalStateException("Could not save post-filter keywords");
        }
        invalidateCache();
    }

    public static String validate(String keyword) {
        if (keyword == null) throw new IllegalArgumentException("Keyword cannot be null");
        String value = keyword.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Keyword cannot be blank");
        if (value.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("Keyword exceeds " + MAX_KEYWORD_LENGTH + " characters");
        }
        return value;
    }

    public static void invalidateCache() {
        synchronized (PostFilterPreferences.class) {
            cachedSource = Collections.emptySet();
            cachedNormalized = Collections.emptyList();
            cacheInitialized = false;
        }
    }

    static List<String> getNormalizedKeywords() {
        if (!isEnabled()) return Collections.emptyList();

        Set<String> stored = Utils.getSetPref(
                Settings.POST_FILTER_KEYWORDS.key,
                Collections.emptySet());
        Set<String> source = stored == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(stored));

        if (cacheInitialized && source.equals(cachedSource)) return cachedNormalized;
        return refreshCache(source);
    }

    private static synchronized List<String> refreshCache(Set<String> source) {
        if (cacheInitialized && source.equals(cachedSource)) return cachedNormalized;

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String keyword : source) {
            if (keyword == null) continue;
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) continue;
            normalized.add(PostFilterMatcher.normalize(trimmed));
        }

        cachedSource = source;
        cachedNormalized = Collections.unmodifiableList(new ArrayList<>(normalized));
        cacheInitialized = true;
        return cachedNormalized;
    }
}
