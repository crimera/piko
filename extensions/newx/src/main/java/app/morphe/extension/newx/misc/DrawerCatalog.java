package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Utils;

/**
 * Patch-time resolved NewX drawer metadata: drawable resource ids per drawer item, used by the
 * drawer editor to render native icons without referencing app classes.
 *
 * <p>Entries are registered once from the settings registry load hook. A drawable of {@code 0}
 * means the item has no icon and the editor renders the row without one.
 */
public final class DrawerCatalog {
    private static final String RESOURCE_NAME_OPTION_PREFIX = "RESOURCE_NAME_";
    private static final String RESOURCE_STRING_OPTION_PREFIX = "RESOURCE_STRING_";

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
        String canonicalId = canonicalOptionId(id);
        if (ITEMS.put(canonicalId, new Item(canonicalId, drawableRes)) == null) {
            ITEM_ORDER.add(canonicalId);
        }
    }

    /**
     * Converts a legacy numeric resource option to the stable entry-name form. New patches emit
     * RESOURCE_NAME_ directly; this keeps catalogs built by older patch bundles readable.
     */
    public static String canonicalOptionId(String id) {
        if (id == null || !id.startsWith(RESOURCE_STRING_OPTION_PREFIX)) return id;

        String encodedResourceId = id.substring(RESOURCE_STRING_OPTION_PREFIX.length());
        if (encodedResourceId.isEmpty()) return id;
        try {
            int resourceId = Integer.parseInt(encodedResourceId, 16);
            if (resourceId == 0 || Utils.getContext() == null) return id;
            String entryName = Utils.getContext().getResources().getResourceEntryName(resourceId);
            return RESOURCE_NAME_OPTION_PREFIX + entryName;
        } catch (RuntimeException ignored) {
            // A stale or malformed legacy ID must not discard the catalog entry.
            return id;
        }
    }

    /** Returns the registered drawable resource id, or {@code 0} when the item has no icon. */
    public static int drawableFor(String id) {
        if (id == null) return 0;
        Item item = ITEMS.get(canonicalOptionId(id));
        return item == null ? 0 : item.drawableRes;
    }
}
