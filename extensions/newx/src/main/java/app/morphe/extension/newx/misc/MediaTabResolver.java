package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;

/**
 * Resolves the default sub-tab for the NewX combined profile media tab
 * (Photos | Videos).
 *
 * <p>The combined profile timeline component seeds its selected sub-tab state with the
 * primary tab type — {@code Videos} for the combined media tab (created as
 * {@code ProfileTab(Videos, user, listOf(Photos))} behind the
 * {@code x_lite_profile_combined_photos_videos_enabled} feature switch). This bridge
 * swaps the seeded value to {@code Photos} when the user configured it, so both the
 * header dropdown selection and the displayed media grid start on Photos.
 *
 * <p>Only {@code Videos} seeds are rewritten; the combined Posts tab (Posts + Highlights)
 * goes through the same component and must stay untouched.
 */
public final class MediaTabResolver {
    private static final String DEFAULT_SETTING = "newx.post_actions_media.media_tab_default";

    private MediaTabResolver() {
    }

    /**
     * Returns the configured default {@code Photos}/{@code Videos} constant from the same
     * enum as {@code current}, or {@code current} unchanged when the setting is the stock
     * default or the current value is not the combined media tab's primary type.
     */
    public static Object getEnumDefault(Object current) {
        if (!(current instanceof Enum<?> currentEnum) || !"Videos".equalsIgnoreCase(currentEnum.name())) {
            return current;
        }
        try {
            String configuredDefault = SettingsRegistry.getStringOrDefault(DEFAULT_SETTING, "");
            if (!"Photos".equalsIgnoreCase(configuredDefault)) return current;
            // Enum.valueOf returns the same instance as the enum-constant scan, without cloning
            // the constants array; a missing constant throws IllegalArgumentException, which is
            // a RuntimeException and therefore handled below.
            return Enum.valueOf(currentEnum.getDeclaringClass(), "Photos");
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to resolve NewX media tab default", exception);
        }
        return current;
    }
}
