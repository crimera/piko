package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patch-time resolved NewX drawer metadata: drawable resource ids per drawer item, used by the
 * drawer editor to render native icons without referencing app classes.
 *
 * <p>Entries are registered once from the settings registry load hook. A drawable of {@code 0}
 * means the item has no icon and the editor renders the row without one.
 */
public final class DrawerCatalog {
    public static final class Item {
        public final String id;
        public final int drawableRes;

        Item(String id, int drawableRes) {
            this.id = id;
            this.drawableRes = drawableRes;
        }
    }

    private static final Map<String, Item> ITEMS = new ConcurrentHashMap<>();
    private static final List<String> ITEM_ORDER = new ArrayList<>();

    private DrawerCatalog() {
    }

    /** Injection point: registers a patch-time resolved drawer item icon. */
    public static synchronized void registerItem(String id, int drawableRes) {
        if (id == null) {
            throw new IllegalArgumentException("Drawer item needs an id");
        }
        if (ITEMS.put(id, new Item(id, drawableRes)) == null) {
            ITEM_ORDER.add(id);
        }
    }

    /** Returns the registered drawable resource id, or {@code 0} when the item has no icon. */
    public static int drawableFor(String id) {
        if (id == null) return 0;
        Item item = ITEMS.get(id);
        return item == null ? 0 : item.drawableRes;
    }
}
