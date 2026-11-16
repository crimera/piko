package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Reorders and hides the NewX profile page tabs the app builds from its profile tab enum.
 *
 * <p>The hook runs on the app-owned page-config list right before the Decompose pages component
 * consumes it, so the app still owns every page outside the strip and only the visible strip
 * changes. Tabs the app adds that this build's catalog does not know are kept, appended in the
 * app's order.
 */
public final class ProfileTabFilter {
    private ProfileTabFilter() {
    }

    /** Hook entry point: applied to the list produced by the profile page builder. */
    public static List<Object> filter(List<Object> tabs) {
        return filter(tabs, ProfileTabsCatalog::tabIdOf, ProfileTabsConfig.shared());
    }

    /** Package-private so tests can drive resolution and configuration without the app. */
    static List<Object> filter(
            List<Object> tabs,
            Function<Object, String> tabIdOf,
            ProfileTabsConfig config
    ) {
        if (tabs == null || tabs.isEmpty()) return tabs;

        try {
            Map<String, Object> pagesById = new LinkedHashMap<>(tabs.size());
            List<String> availableIds = new ArrayList<>(tabs.size());
            List<Object> unresolved = new ArrayList<>();
            for (Object page : tabs) {
                String id = page == null ? null : tabIdOf.apply(page);
                if (id == null || id.isEmpty() || pagesById.containsKey(id)) {
                    // An unresolvable or duplicate page keeps its element rather than being dropped.
                    unresolved.add(page);
                    continue;
                }
                pagesById.put(id, page);
                availableIds.add(id);
            }

            ProfileTabsCatalog.updateLiveTabIds(availableIds);

            Set<String> hidden = config.hiddenTabs();
            List<Object> filtered = new ArrayList<>(tabs.size());
            for (String id : config.orderedTabs(availableIds)) {
                if (hidden.contains(id)) continue;
                Object page = pagesById.get(id);
                if (page != null) filtered.add(page);
            }
            filtered.addAll(unresolved);

            if (filtered.isEmpty()) {
                // Hiding every tab would leave the profile strip blank; keep the app's first tab.
                filtered.add(tabs.get(0));
            }
            return keepsSameElements(tabs, filtered) ? tabs : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX profile tabs", exception);
            return tabs;
        }
    }

    private static boolean keepsSameElements(List<Object> before, List<Object> after) {
        if (before.size() != after.size()) return false;
        for (int index = 0; index < before.size(); index++) {
            if (before.get(index) != after.get(index)) return false;
        }
        return true;
    }
}
