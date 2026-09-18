package app.morphe.extension.newx.misc;

import android.content.Context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kotlin.jvm.functions.Function0;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;

/**
 * Runtime hooks for the editor-managed NewX navigation bar configuration. The drawer row click is
 * captured at runtime because it owns the real navigation logic.
 */
public final class NavBarReplacement {
    private static final Map<String, Function0<?>> DESTINATION_CLICKS = new ConcurrentHashMap<>();

    private NavBarReplacement() {
    }

    /** Injection point: captures the drawer row click that performs the real navigation. */
    public static void setDestinationClick(String destinationId, Function0<?> click) {
        if (destinationId == null || click == null) return;
        DESTINATION_CLICKS.put(destinationId, click);
    }

    /** Injection point: substitutes the icon with the configured destination's icon. */
    public static Object overrideIcon(Object tab, Object originalIcon) {
        String destinationId = configuredDestination(tab);
        if (destinationId == null) return originalIcon;
        NavBarCatalog.Destination destination = NavBarCatalog.destination(destinationId);
        if (destination == null) return originalIcon;
        return destination.icon;
    }

    /** Injection point: substitutes the localized label of the configured navigation bar item. */
    public static String overrideLabel(Object tab, String originalLabel) {
        String destinationId = configuredDestination(tab);
        if (destinationId == null) return originalLabel;
        NavBarCatalog.Destination destination = NavBarCatalog.destination(destinationId);
        if (destination == null) return originalLabel;

        try {
            Context context = Utils.getContext();
            if (context == null) return originalLabel;
            return context.getString(destination.titleResourceId);
        } catch (Exception exception) {
            NewXLogger.printException(
                    () -> "Failed to resolve the NewX navigation bar label",
                    exception
            );
            return originalLabel;
        }
    }

    /** Injection point: returns true if the tab is replaced and its badge should be cleared. */
    public static boolean shouldClearBadge(Object tab) {
        return configuredDestination(tab) != null;
    }

    /** Injection point: opens the replacement screen instead of changing the selected tab. */
    public static boolean openReplacementFor(Object tab) {
        String destinationId = configuredDestination(tab);
        if (destinationId == null) return false;
        Function0<?> click = DESTINATION_CLICKS.get(destinationId);
        if (click == null) return false;

        try {
            click.invoke();
            return true;
        } catch (Exception exception) {
            NewXLogger.printException(
                    () -> "Failed to open the NewX navigation bar replacement",
                    exception
            );
            return false;
        }
    }

    private static String configuredDestination(Object tab) {
        if (tab == null) return null;
        return NavBarConfig.shared().destinationFor(tab.toString());
    }
}
