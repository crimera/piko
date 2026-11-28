package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Patch-time resolved NewX profile tab metadata: every tab the target release's profile tab enum
 * declares, with the app's own localized title resource for the editor.
 *
 * <p>Entries are registered once from the settings registry load hook. {@link #tabIdOf(Object)} is
 * a placeholder replaced at patch time with a direct field read, so the runtime never reflects on
 * the release-specific page-config class.
 */
public final class ProfileTabsCatalog {
    public static final class Tab {
        public final String id;
        public final int titleResourceId;

        Tab(String id, int titleResourceId) {
            this.id = id;
            this.titleResourceId = titleResourceId;
        }
    }

    private static final Map<String, Tab> TABS = new ConcurrentHashMap<>();
    private static final List<String> TAB_ORDER = new CopyOnWriteArrayList<>();
    private static final Set<String> LIVE_TAB_IDS = ConcurrentHashMap.newKeySet();
    private static volatile boolean liveTabsKnown;

    private ProfileTabsCatalog() {
    }

    /** Injection point: registers one profile tab for the editor. */
    public static void registerTab(String id, int titleResourceId) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Profile tab needs an id");
        }
        if (TABS.put(id, new Tab(id, titleResourceId)) == null) {
            TAB_ORDER.add(id);
        }
    }

    /**
     * Injection point: resolves the tab id of an app page-config object.
     *
     * <p>The body is replaced at patch time with the resolved page-config field read; the
     * placeholder returns null so an unpatched build keeps every tab.
     */
    @Nullable
    public static String tabIdOf(Object pageConfig) {
        return null;
    }

    public static List<String> tabIds() {
        return new ArrayList<>(TAB_ORDER);
    }

    public static List<Tab> tabs() {
        List<Tab> result = new ArrayList<>(TAB_ORDER.size());
        for (String id : TAB_ORDER) {
            Tab tab = TABS.get(id);
            if (tab != null) result.add(tab);
        }
        return result;
    }

    @Nullable
    public static Tab tab(String id) {
        return id == null ? null : TABS.get(id);
    }

    /** Records the tab ids present in the current app-owned profile page list. */
    static void updateLiveTabIds(Collection<String> tabIds) {
        LIVE_TAB_IDS.clear();
        LIVE_TAB_IDS.addAll(tabIds);
        liveTabsKnown = true;
    }

    /** Returns registered tabs that are present in the current app-owned profile page list. */
    public static List<String> liveTabIds() {
        if (!liveTabsKnown) return tabIds();
        List<String> result = new ArrayList<>();
        for (String tabId : TAB_ORDER) {
            if (LIVE_TAB_IDS.contains(tabId)) result.add(tabId);
        }
        return result;
    }
}
