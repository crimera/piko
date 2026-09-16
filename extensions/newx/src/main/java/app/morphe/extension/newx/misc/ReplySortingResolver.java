package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.settings.preference.PikoSharedPrefCategory;
import app.morphe.extension.newx.settings.SettingsRegistry;

/**
 * Resolves the default reply-sorting mode for NewX tweet detail requests and remembers the
 * last user-selected mode.
 */
public final class ReplySortingResolver {
    private static final String PREFERENCES_NAME = "piko_newx_reply_sorting";
    private static final String LAST_KEY = "last_filter";
    private static final String DEFAULT_FALLBACK = "Relevance";

    private static final String DEFAULT_SETTING = "newx.timeline.default_reply_sorting";
    private static final String REMEMBER_SETTING = "newx.timeline.remember_reply_sorting";

    /** Lazily created on first use; the extension hook always runs before any call site. */
    private static PikoSharedPrefCategory preferences;

    private ReplySortingResolver() {
    }

    public static Object getEnumDefault(Class<?> enumClass) {
        if (enumClass == null || !enumClass.isEnum()) {
            NewXLogger.printException(
                    () -> "Failed to resolve NewX reply sorting enum: target is not an enum",
                    new IllegalArgumentException("Expected an enum class: " + enumClass)
            );
            return null;
        }

        try {
            Object[] constants = enumClass.getEnumConstants();
            if (constants == null || constants.length == 0) {
                throw new IllegalStateException("Enum has no constants: " + enumClass.getName());
            }

            String mode = getDefault();
            for (Object constant : constants) {
                if (constant instanceof Enum<?> enumConstant
                        && enumConstant.name().equalsIgnoreCase(mode)) {
                    return constant;
                }
            }

            throw new IllegalStateException(
                    "Enum " + enumClass.getName() + " has no reply sorting mode: " + mode
            );
        } catch (Throwable t) {
            NewXLogger.printException(() -> "Failed to resolve NewX reply sorting enum", t);
            return null;
        }
    }

    public static String getDefault() {
        try {
            if (SettingsRegistry.getBooleanOrDefault(REMEMBER_SETTING, false)) {
                String last = readLast();
                if (isValidMode(last)) return last;
            }
            String configured = SettingsRegistry.getStringOrDefault(
                    DEFAULT_SETTING,
                    DEFAULT_FALLBACK
            );
            if (isValidMode(configured)) return configured;
            return DEFAULT_FALLBACK;
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to read NewX default reply sorting", exception);
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
        if (!(enumObject instanceof Enum<?> enumValue)) {
            NewXLogger.printException(
                    () -> "Ignoring non-enum NewX reply sorting value",
                    new IllegalArgumentException("Expected an enum value: " + enumObject.getClass().getName())
            );
            return;
        }

        try {
            if (!SettingsRegistry.getBooleanOrDefault(REMEMBER_SETTING, false)) return;
            String name = enumValue.name();
            if (!isValidMode(name)) return;

            preferences().saveString(LAST_KEY, name);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to save NewX last reply sorting", exception);
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
