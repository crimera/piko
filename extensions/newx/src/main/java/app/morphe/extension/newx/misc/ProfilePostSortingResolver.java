package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;

/** Resolves the initial latest/popular mode for profile post timelines. */
public final class ProfilePostSortingResolver {
    private static final String DEFAULT_SETTING =
            "newx.post_actions_media.profile_post_sort_default";

    private ProfilePostSortingResolver() {
    }

    public static Boolean getDefault() {
        try {
            String configured = SettingsRegistry.getStringOrDefault(DEFAULT_SETTING, "Latest");
            return "Popular".equalsIgnoreCase(configured) ? Boolean.TRUE : Boolean.FALSE;
        } catch (RuntimeException exception) {
            NewXLogger.printException(
                    () -> "Failed to read NewX profile post sorting default",
                    exception
            );
            return Boolean.FALSE;
        }
    }
}
