/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.featureflags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FeatureFlagCatalog {
    private final Set<String> names = new LinkedHashSet<>();
    private final Set<String> added = new LinkedHashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();

    FeatureFlagCatalog(String source, List<FeatureFlag> flags) {
        for (String name : source.split(",")) {
            name = name.trim();
            if (isValidName(name)) names.add(name);
        }
        for (FeatureFlag flag : flags) added.add(flag.getName().trim());
    }

    static boolean isValidName(String name) {
        if (name.isEmpty()) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ',' || c == ':' || Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        return true;
    }

    static int indexOf(List<FeatureFlag> flags, String name) {
        for (int i = 0; i < flags.size(); i++) {
            if (flags.get(i).getName().trim().equals(name.trim())) return i;
        }
        return -1;
    }

    boolean isAdded(String name) { return added.contains(name); }
    boolean isKnown(String name) { return names.contains(name); }

    List<String> corrections(String input) {
        String query = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        if (query.length() > 256) return matches;
        for (String name : names) {
            if (distanceWithin(name.toLowerCase(Locale.ROOT), query, query.length() < 8 ? 1 : 2)) {
                matches.add(name);
            }
        }
        return matches;
    }
    boolean isSelected(String name) { return selected.contains(name); }
    Set<String> selected() { return Collections.unmodifiableSet(selected); }

    void toggle(String name) {
        if (!names.contains(name) || isAdded(name)) return;
        if (!selected.remove(name)) selected.add(name);
    }

    List<String> search(String input) {
        String query = input.trim().toLowerCase(Locale.ROOT);
        List<String> exact = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        List<String> similar = new ArrayList<>();
        for (String name : names) {
            String candidate = name.toLowerCase(Locale.ROOT);
            if (candidate.equals(query)) exact.add(name);
            else if (candidate.contains(query)) partial.add(name);
            else if (query.length() >= 4 && query.length() <= 256 && resembles(candidate, query)) similar.add(name);
        }
        exact.addAll(partial);
        exact.addAll(similar);
        return exact;
    }

    private static boolean resembles(String name, String query) {
        int limit = query.length() < 8 ? 1 : 2;
        if (distanceWithin(name, query, limit)) return true;
        for (String word : name.split("_")) {
            if (distanceWithin(word, query, limit)) return true;
        }
        return false;
    }

    private static boolean distanceWithin(String name, String query, int limit) {
        if (Math.abs(name.length() - query.length()) > limit) return false;
        int[] previous = new int[query.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= name.length(); i++) {
            int[] current = new int[query.length() + 1];
            current[0] = i;
            int minimum = i;
            for (int j = 1; j <= query.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (name.charAt(i - 1) == query.charAt(j - 1) ? 0 : 1));
                minimum = Math.min(minimum, current[j]);
            }
            if (minimum > limit) return false;
            previous = current;
        }
        return previous[query.length()] <= limit;
    }
}
