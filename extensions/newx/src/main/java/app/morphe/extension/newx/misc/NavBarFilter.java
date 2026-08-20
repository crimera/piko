package app.morphe.extension.newx.misc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;

public final class NavBarFilter {
    private NavBarFilter() {
    }

    /** Removes hidden enum tabs while preserving map order. */
    public static Map<Object, Object> filter(Map<Object, Object> tabData, Set<String> hiddenTabs) {
        if (tabData == null || hiddenTabs == null || hiddenTabs.isEmpty()) return tabData;

        try {
            Map<Object, Object> filtered = new LinkedHashMap<>(tabData.size());
            boolean changed = false;
            for (Map.Entry<Object, Object> entry : tabData.entrySet()) {
                Object key = entry.getKey();
                if (key != null && hiddenTabs.contains(key.toString())) {
                    changed = true;
                    continue;
                }
                filtered.put(key, entry.getValue());
            }
            return changed ? filtered : tabData;
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to customize NewX navigation bar", exception);
            return tabData;
        }
    }
}
