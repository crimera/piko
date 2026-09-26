/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.featureflags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

final class FeatureFlagAudit {
    static final class Issue {
        final int position;
        final int copies;
        final boolean conflicting;
        final List<String> suggestions;

        Issue(int position, int copies, boolean conflicting, List<String> suggestions) {
            this.position = position;
            this.copies = copies;
            this.conflicting = conflicting;
            this.suggestions = suggestions;
        }
    }

    static List<Issue> inspect(List<FeatureFlag> flags, FeatureFlagCatalog catalog) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> states = new HashMap<>();
        for (FeatureFlag flag : flags) {
            String name = flag.getName().trim();
            counts.put(name, counts.getOrDefault(name, 0) + 1);
            states.put(name, states.getOrDefault(name, 0) | (flag.getEnabled() ? 1 : 2));
        }
        List<Issue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < flags.size(); i++) {
            String name = flags.get(i).getName().trim();
            if (!seen.add(name)) continue;
            boolean unknown = !catalog.isKnown(name);
            int count = counts.get(name);
            if (count > 1 || unknown) {
                issues.add(new Issue(i, count, states.get(name) == 3,
                        unknown ? catalog.corrections(name) : java.util.Collections.emptyList()));
            }
        }
        return issues;
    }

    static void mergeDuplicates(List<FeatureFlag> flags, String name, boolean enabled) {
        int first = -1;
        for (int i = 0; i < flags.size(); i++) {
            if (flags.get(i).getName().trim().equals(name)) {
                if (first < 0) {
                    first = i;
                    flags.set(i, new FeatureFlag(name, enabled));
                } else {
                    flags.remove(i--);
                }
            }
        }
    }
}
