/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.featureflags;

import java.util.List;
import java.util.TreeSet;

final class FeatureFlagSelection {
    private final TreeSet<Integer> positions = new TreeSet<>();

    void toggle(int position) {
        if (!positions.remove(position)) positions.add(position);
    }
    boolean contains(int position) { return positions.contains(position); }
    int size() { return positions.size(); }
    void clear() { positions.clear(); }
    void remove(List<FeatureFlag> flags) {
        for (int position : positions.descendingSet()) flags.remove(position);
        clear();
    }
}
