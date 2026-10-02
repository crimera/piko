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
 * Patch-time resolved NewX navigation bar metadata: drawer destinations and the native tab slots
 * used by the navigation bar editor.
 *
 * <p>Entries are registered once from the settings registry load hook. Destination icons keep
 * their app objects for runtime rendering and their drawable resource ids for the editor UI.
 */
public final class NavBarCatalog {
    public static final class Destination {
        public final String id;
        public final int titleResourceId;
        public final Object icon;
        public final int drawableRes;

        Destination(String id, int titleResourceId, Object icon, int drawableRes) {
            this.id = id;
            this.titleResourceId = titleResourceId;
            this.icon = icon;
            this.drawableRes = drawableRes;
        }
    }

    public static final class Tab {
        public final String id;
        public final int drawableRes;
        public final String labelResourceName;

        Tab(String id, int drawableRes, String labelResourceName) {
            this.id = id;
            this.drawableRes = drawableRes;
            this.labelResourceName = labelResourceName;
        }
    }

    private static final Map<String, Destination> DESTINATIONS = new ConcurrentHashMap<>();
    private static final Map<String, Tab> TABS = new ConcurrentHashMap<>();
    private static final List<String> DESTINATION_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> TAB_ORDER = new CopyOnWriteArrayList<>();
    private static final Set<String> LIVE_TAB_IDS = ConcurrentHashMap.newKeySet();
    private static volatile boolean liveTabsKnown;

    private NavBarCatalog() {
    }

    /** Injection point: registers a patch-time resolved drawer destination. */
    public static void registerDestination(String id, int titleResourceId, Object icon, int drawableRes) {
        if (id == null || icon == null || drawableRes == 0) {
            throw new IllegalArgumentException("Navigation bar destination needs an id, icon, and drawable");
        }
        if (DESTINATIONS.put(id, new Destination(id, titleResourceId, icon, drawableRes)) == null) {
            DESTINATION_ORDER.add(id);
        }
    }

    /** Injection point: registers one navigation tab for the editor. */
    public static void registerTab(String id, int drawableRes, String labelResourceName) {
        if (id == null || drawableRes == 0 || labelResourceName == null) {
            throw new IllegalArgumentException("Navigation tab needs an id, drawable, and label");
        }
        if (TABS.put(id, new Tab(id, drawableRes, labelResourceName)) == null) {
            TAB_ORDER.add(id);
        }
    }

    @Nullable
    public static Destination destination(String id) {
        return id == null ? null : DESTINATIONS.get(id);
    }

    @Nullable
    public static Tab tab(String id) {
        return id == null ? null : TABS.get(id);
    }

    public static List<Destination> destinations() {
        return destinationsInOrder();
    }

    public static List<String> tabIds() {
        return new ArrayList<>(TAB_ORDER);
    }

    /** Records the native slots present in the current app-owned tab map. */
    static void updateLiveTabIds(Collection<String> tabIds) {
        LIVE_TAB_IDS.clear();
        LIVE_TAB_IDS.addAll(tabIds);
        liveTabsKnown = true;
    }

    /** Returns registered native slots that are present in the current app-owned tab map. */
    public static List<String> liveTabIds() {
        if (!liveTabsKnown) return tabIds();
        List<String> result = new ArrayList<>();
        for (String tabId : TAB_ORDER) {
            if (LIVE_TAB_IDS.contains(tabId)) result.add(tabId);
        }
        return result;
    }

    public static List<Tab> tabs() {
        List<Tab> result = new ArrayList<>(TAB_ORDER.size());
        for (String id : TAB_ORDER) {
            Tab tab = TABS.get(id);
            if (tab != null) result.add(tab);
        }
        return result;
    }

    private static List<Destination> destinationsInOrder() {
        List<Destination> result = new ArrayList<>(DESTINATION_ORDER.size());
        for (String id : DESTINATION_ORDER) {
            Destination destination = DESTINATIONS.get(id);
            if (destination != null) result.add(destination);
        }
        return result;
    }
}
