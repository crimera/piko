package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Patch-time resolved NewX navigation bar metadata: drawer destinations, selectable icons, and the
 * tab list used by the navigation bar editor.
 *
 * <p>Entries are registered once from the settings registry load hook. Icons are app
 * {@code com.x.icons.b} objects at runtime and drawable resource ids for the editor UI.
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

    public static final class Icon {
        public final String id;
        public final Object icon;
        public final int drawableRes;
        public final String labelResourceName;

        Icon(String id, Object icon, int drawableRes, String labelResourceName) {
            this.id = id;
            this.icon = icon;
            this.drawableRes = drawableRes;
            this.labelResourceName = labelResourceName;
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
    private static final Map<String, Icon> ICONS = new ConcurrentHashMap<>();
    private static final Map<String, Tab> TABS = new ConcurrentHashMap<>();
    private static final List<String> DESTINATION_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> ICON_ORDER = new CopyOnWriteArrayList<>();
    private static final List<String> TAB_ORDER = new CopyOnWriteArrayList<>();

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

    /** Injection point: registers a patch-time resolved selectable icon. */
    public static void registerIcon(String id, Object icon, int drawableRes, String labelResourceName) {
        if (id == null || icon == null || drawableRes == 0 || labelResourceName == null) {
            throw new IllegalArgumentException("Navigation bar icon needs an id, icon, drawable, and label");
        }
        if (ICONS.put(id, new Icon(id, icon, drawableRes, labelResourceName)) == null) {
            ICON_ORDER.add(id);
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
    public static Icon icon(String id) {
        return id == null ? null : ICONS.get(id);
    }

    @Nullable
    public static Tab tab(String id) {
        return id == null ? null : TABS.get(id);
    }

    public static List<Destination> destinations() {
        return destinationsInOrder();
    }

    public static List<Icon> icons() {
        List<Icon> result = new ArrayList<>(ICON_ORDER.size());
        for (String id : ICON_ORDER) {
            Icon icon = ICONS.get(id);
            if (icon != null) result.add(icon);
        }
        return result;
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

    private static List<Destination> destinationsInOrder() {
        List<Destination> result = new ArrayList<>(DESTINATION_ORDER.size());
        for (String id : DESTINATION_ORDER) {
            Destination destination = DESTINATIONS.get(id);
            if (destination != null) result.add(destination);
        }
        return result;
    }
}
