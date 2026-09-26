package app.morphe.extension.newx.timeline;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;

/** Filters the initial NewX home timeline tab route array. */
public final class TimelineTabFilter {
    private static final String SETTING = "newx.timeline.tab_visibility";
    private static final String SHOW_BOTH = "show_both";
    private static final String HIDE_FOR_YOU = "hide_for_you";
    private static final String HIDE_FOLLOWING = "hide_following";

    private TimelineTabFilter() {
    }

    /**
     * Receives the route array as Object to keep release-specific NewX model descriptors out of
     * the extension API. The patched caller restores the array type before invoking NewX's list
     * factory.
     */
    public static Object filter(Object elements) {
        if (!(elements instanceof Object[])) return elements;

        try {
            String visibility = SettingsRegistry.getStringOrDefault(SETTING, SHOW_BOTH);
            return filter((Object[]) elements, visibility);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX timeline tabs", exception);
            return elements;
        }
    }

    /** Type-safe convenience overload for extension callers and tests. */
    public static Object[] filter(Object[] elements) {
        Object filtered = filter((Object) elements);
        return filtered instanceof Object[] ? (Object[]) filtered : elements;
    }

    static Object filter(Object[] elements, String visibility) {
        if (elements == null || elements.length == 0 || SHOW_BOTH.equals(visibility)) return elements;

        String hiddenTab = hiddenTabName(visibility);
        if (hiddenTab == null) return elements;

        List<Object> filtered = new ArrayList<>(elements.length);
        boolean changed = false;
        for (Object element : elements) {
            if (isHidden(element, hiddenTab)) {
                changed = true;
                continue;
            }
            filtered.add(element);
        }
        return changed ? filtered.toArray(new Object[0]) : elements;
    }

    private static String hiddenTabName(String visibility) {
        if (visibility == null) return null;

        return switch (visibility) {
            case HIDE_FOR_YOU -> "ForYou";
            case HIDE_FOLLOWING -> "Following";
            default -> null;
        };
    }

    private static boolean isHidden(Object element, String hiddenTabName) {
        if (element == null) return false;

        String representation = element.toString();
        return representation.equals(hiddenTabName) ||
                representation.contains("homeTabType=" + hiddenTabName);
    }
}
