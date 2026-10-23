package app.morphe.extension.newx.misc;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;

/** Removes hidden actions from the NewX photo and video long-press bottom sheet. */
public final class MediaSheetFilter {
    private MediaSheetFilter() {
    }

    /**
     * Filters the media sheet rows by the string-resource entry name of each row's title. The
     * hidden item IDs are those stable resource names, so a renamed release resource fails the
     * patch-time validation instead of silently keeping the row.
     */
    public static List<?> filter(List<?> items, Set<String> hiddenItemIds) {
        if (items == null || items.isEmpty() || hiddenItemIds == null || hiddenItemIds.isEmpty()) {
            return items;
        }

        try {
            List<Object> filtered = null;
            int index = 0;
            for (Object item : items) {
                if (shouldHide(item, hiddenItemIds)) {
                    if (filtered == null) {
                        filtered = new ArrayList<>(items.size());
                        filtered.addAll(items.subList(0, index));
                    }
                } else if (filtered != null) {
                    filtered.add(item);
                }
                index++;
            }
            return filtered == null ? items : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX media sheet", exception);
            return items;
        }
    }

    private static boolean shouldHide(Object item, Set<String> hiddenItemIds) {
        String resourceName = labelResourceName(item);
        return resourceName != null && hiddenItemIds.contains(resourceName);
    }

    private static String labelResourceName(Object item) {
        int resourceId = getLabelResourceId(item);
        if (resourceId == 0) return null;

        try {
            Context context = Utils.getContext();
            return context == null ? null : context.getResources().getResourceEntryName(resourceId);
        } catch (Resources.NotFoundException exception) {
            return null;
        }
    }

    /**
     * Returns the string-resource id of the row title. The placeholder body is replaced at patch
     * time with the release-specific title getter and resource field of the target APK, so no
     * release class is reflected at runtime.
     */
    private static int getLabelResourceId(Object item) {
        return 0;
    }
}
