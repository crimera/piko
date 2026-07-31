package app.morphe.extension.xlite.misc;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.xlite.settings.SettingsRegistry;

/**
 * Resolves the default reply-sorting mode for X-Lite tweet detail requests and remembers the
 * last user-selected mode.
 */
public final class ReplySortingResolver {
    private static final String PREFERENCES_NAME = "piko_xlite_reply_sorting";
    private static final String LAST_KEY = "last_filter";
    private static final String DEFAULT_FALLBACK = "Relevance";

    private static final String DEFAULT_SETTING = "xlite.timeline.default_reply_sorting";
    private static final String REMEMBER_SETTING = "xlite.timeline.remember_reply_sorting";

    /** Lazily created on first use; the extension hook always runs before any call site. */
    private static PikoSharedPrefCategory preferences;

    private ReplySortingResolver() {
    }

    public static Object getEnumDefault(Class<?> enumClass) {
        try {
            Object[] constants = enumClass.getEnumConstants();
            if (constants != null && constants.length > 0) {
                String mode = getDefault();
                for (Object constant : constants) {
                    if (constant instanceof Enum<?> e && e.name().equalsIgnoreCase(mode)) {
                        return constant;
                    }
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "Failed to resolve X-Lite reply sorting enum", t);
        }
        return null;
    }

    public static String getDefault() {
        try {
            if (SettingsRegistry.getBoolean(REMEMBER_SETTING)) {
                String last = readLast();
                if (isValidMode(last)) return last;
            }
            String configured = SettingsRegistry.getString(DEFAULT_SETTING);
            if (isValidMode(configured)) return configured;
            return DEFAULT_FALLBACK;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to read X-Lite default reply sorting", exception);
            return DEFAULT_FALLBACK;
        }
    }

    private static boolean isValidMode(String mode) {
        return "Relevance".equalsIgnoreCase(mode)
                || "Recency".equalsIgnoreCase(mode)
                || "Likes".equalsIgnoreCase(mode);
    }

    public static void remember(Object enumObject) {
        if (enumObject == null) return;
        try {
            if (!SettingsRegistry.getBoolean(REMEMBER_SETTING)) return;
            String name = null;
            if (enumObject instanceof Enum<?> e && isValidMode(e.name())) {
                name = e.name();
            } else {
                name = enumObject.toString();
            }
            if (name == null || name.isEmpty()) return;

            preferences().saveString(LAST_KEY, name);
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to save X-Lite last reply sorting", exception);
        }
    }

    @Nullable
    private static String readLast() {
        // getString requires a non-null default; an empty value means unset.
        String last = preferences().getString(LAST_KEY, "");
        return last.isEmpty() ? null : last;
    }

    private static PikoSharedPrefCategory preferences() {
        if (preferences == null) {
            preferences = new PikoSharedPrefCategory(PREFERENCES_NAME);
        }
        return preferences;
    }

}
