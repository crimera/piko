package app.morphe.extension.newx.misc;

import android.content.Context;
import android.content.res.Resources;

import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;

public final class DrawerItemFilter {
    private static final String RESOURCE_NAME_ITEM_ID_PREFIX = "RESOURCE_NAME_";
    private static final String RESOURCE_STRING_ITEM_ID_PREFIX = "RESOURCE_STRING_";

    private DrawerItemFilter() {
    }

    // Legacy drawer rows expose only their localized title. Stable option IDs carry the
    // string entry name, while RESOURCE_STRING_ remains readable for older saved settings.
    public static boolean shouldHide(String title, Set<String> hiddenItemIds) {
        if (title == null || hiddenItemIds == null || hiddenItemIds.isEmpty()) return false;

        try {
            Context context = Utils.getContext();
            if (context == null) return false;

            Resources resources = context.getResources();
            for (String hiddenItemId : hiddenItemIds) {
                if (hiddenItemId != null && matchesResourceOption(
                        title,
                        hiddenItemId,
                        context,
                        resources
                )) {
                    return true;
                }
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX drawer", exception);
        }
        return false;
    }

    // BETA PATH: the settings footer exposes a stable logical item ID instead of a title.
    public static boolean shouldHideId(String itemId, Set<String> hiddenItemIds) {
        return itemId != null && hiddenItemIds != null && hiddenItemIds.contains(itemId);
    }

    private static boolean matchesResourceOption(
            String title,
            String hiddenItemId,
            Context context,
            Resources resources
    ) {
        if (hiddenItemId.startsWith(RESOURCE_NAME_ITEM_ID_PREFIX)) {
            String entryName = hiddenItemId.substring(RESOURCE_NAME_ITEM_ID_PREFIX.length());
            if (entryName.isEmpty()) return false;

            int resourceId = resources.getIdentifier(
                    entryName,
                    "string",
                    context.getPackageName()
            );
            try {
                return resourceId != 0 && title.equals(resources.getString(resourceId));
            } catch (Resources.NotFoundException ignored) {
                // A stale or malformed option must not hide unrelated rows.
                return false;
            }
        }

        if (!hiddenItemId.startsWith(RESOURCE_STRING_ITEM_ID_PREFIX)) return false;

        String encodedResourceIds = hiddenItemId.substring(RESOURCE_STRING_ITEM_ID_PREFIX.length());
        if (encodedResourceIds.isEmpty()) return false;

        for (String encodedResourceId : encodedResourceIds.split("-")) {
            try {
                int resourceId = Integer.parseInt(encodedResourceId, 16);
                if (resourceId != 0 && title.equals(resources.getString(resourceId))) return true;
            } catch (NumberFormatException | Resources.NotFoundException ignored) {
                // A stale or malformed option must not hide unrelated rows.
            }
        }
        return false;
    }
}
