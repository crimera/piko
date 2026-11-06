package app.morphe.extension.instagram.patches.notification.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** The choices behind a group's icon, without Android types, so they are unit-tested. */
public final class GroupIconRules {
    private GroupIconRules() {
    }

    /**
     * Index of the image candidate to download: the narrowest at least {@code target} wide, else
     * the widest. A width of 0 means unknown; with no width known the first candidate is used.
     * Returns -1 for an empty list.
     */
    public static int bestCandidate(int[] widths, int target) {
        int best = -1;
        int bestWidth = 0;
        for (int i = 0; i < widths.length; i++) {
            int width = widths[i];
            boolean better = best < 0
                    || (bestWidth < target ? width > bestWidth : width >= target && width < bestWidth);
            if (better) {
                best = i;
                bestWidth = width;
            }
        }
        return best;
    }

    /**
     * The members whose photos make the composite, in drawing order: never the viewer, fixed by
     * lowest id (numeric order), so the icon does not change with each message.
     */
    public static List<String> compositeOrder(List<String> memberIds, Predicate<String> isViewer) {
        List<String> ids = new ArrayList<>();
        for (String id : memberIds) {
            if (id != null && !id.isEmpty() && !isViewer.test(id) && !ids.contains(id)) ids.add(id);
        }
        Collections.sort(ids, (a, b) -> a.length() != b.length() ? a.length() - b.length() : a.compareTo(b));
        return ids;
    }
}
