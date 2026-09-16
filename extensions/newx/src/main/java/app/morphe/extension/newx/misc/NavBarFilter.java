package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;

public final class NavBarFilter {
    private NavBarFilter() {
    }

    /** Reorders and removes editor-configured enum tabs while preserving map values. */
    public static Map<Object, Object> filter(Map<Object, Object> tabData) {
        if (tabData == null || tabData.isEmpty()) return tabData;

        try {
            NavBarConfig config = NavBarConfig.shared();
            Set<String> hidden = config.hiddenTabs();
            List<Object> availableKeys = new ArrayList<>(tabData.size());
            List<String> availableTabIds = new ArrayList<>(tabData.size());
            for (Object key : tabData.keySet()) {
                if (key == null) continue;
                availableKeys.add(key);
                availableTabIds.add(key.toString());
            }

            List<String> orderedTabIds = config.orderedTabs(availableTabIds);
            Map<String, Object> keysByTabId = new LinkedHashMap<>(availableKeys.size());
            Map<String, Object> valuesByTabId = new LinkedHashMap<>(availableKeys.size());
            for (Map.Entry<Object, Object> entry : tabData.entrySet()) {
                Object key = entry.getKey();
                if (key == null) continue;
                keysByTabId.put(key.toString(), key);
                valuesByTabId.put(key.toString(), entry.getValue());
            }

            Map<Object, Object> filtered = new LinkedHashMap<>(tabData.size());
            for (String tabId : orderedTabIds) {
                Object key = keysByTabId.get(tabId);
                if (key == null || hidden.contains(tabId)) continue;
                filtered.put(key, valuesByTabId.get(tabId));
            }

            List<Object> before = new ArrayList<>(tabData.keySet());
            List<Object> after = new ArrayList<>(filtered.keySet());
            return before.equals(after) ? tabData : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX navigation bar", exception);
            return tabData;
        }
    }
}
