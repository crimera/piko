package app.morphe.extension.newx.settings;

import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.crimera.logging.LogSanitizer;
import app.morphe.extension.crimera.logging.PikoLogger;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;

/**
 * NewX binding of the shared {@link PikoLogger}: supplies the NewX settings as its gates and the
 * X-specific server error filtering. Morphe's info and exception log methods are unconditional, so
 * NewX code logs through here.
 */
public final class NewXLogger {
    static final String LOGGING_SETTING_ID = "newx.advanced.debug_tools.logging";
    static final String SERVER_LOGGING_SETTING_ID = "newx.advanced.debug_tools.server_logging";

    private static final String X_ERRORS_CLASS_NAME = "com.x.repositories.errors.XErrors";
    private static final String EMPTY_X_ERRORS_PREFIX = "XErrors(errors=[],";
    private static final PikoLogger LOGGER = new PikoLogger(
            NewXLogger::isLoggingEnabled,
            NewXLogger::isServerLoggingEnabled,
            LogSanitizer.withExtraKeys("bounce[_-]?deeplink")
    );
    // Setting objects are stable after freeze with volatile values. Caching the
    // reference skips the registry map lookup on every log-gate check in hot paths.
    private static volatile Setting<?> cachedLoggingSetting;
    private static volatile Setting<?> cachedServerLoggingSetting;

    private NewXLogger() {
    }

    public static boolean isLoggingEnabled() {
        Setting<?> cached = cachedLoggingSetting;
        if (cached instanceof BooleanSetting booleanSetting) {
            try {
                return booleanSetting.get();
            } catch (RuntimeException ignored) {
            }
        }
        boolean enabled = SettingsRegistry.getBooleanOrDefault(LOGGING_SETTING_ID, false);
        Setting<?> resolved = SettingsRegistry.settingOrNull(LOGGING_SETTING_ID);
        if (resolved instanceof BooleanSetting) cachedLoggingSetting = resolved;
        return enabled;
    }

    public static boolean isServerLoggingEnabled() {
        Setting<?> cached = cachedServerLoggingSetting;
        if (cached instanceof BooleanSetting booleanSetting) {
            try {
                return booleanSetting.get();
            } catch (RuntimeException ignored) {
            }
        }
        // The settings registry is loaded lazily; the patch's opt-in is the safe fallback.
        boolean enabled = SettingsRegistry.getBooleanOrDefault(SERVER_LOGGING_SETTING_ID, true);
        Setting<?> resolved = SettingsRegistry.settingOrNull(SERVER_LOGGING_SETTING_ID);
        if (resolved instanceof BooleanSetting) cachedServerLoggingSetting = resolved;
        return enabled;
    }

    public static void printInfo(Logger.LogMessage message) {
        LOGGER.printInfo(message);
    }

    public static void printInfo(Logger.LogMessage message, Exception exception) {
        LOGGER.printInfo(message, exception);
    }

    public static void printException(Logger.LogMessage message) {
        LOGGER.printException(message);
    }

    public static void printException(Logger.LogMessage message, Throwable throwable) {
        LOGGER.printException(message, throwable);
    }

    public static void logger(Object value) {
        LOGGER.log(value);
    }

    /** Captures parsed server errors without allowing diagnostics to affect app behavior. */
    public static void captureServerError(Throwable throwable) {
        if (throwable == null) return;

        try {
            if (!isServerLoggingEnabled() || isEmptyXErrors(throwable)) return;
            LOGGER.capture("server_error", null, throwable);
        } catch (Throwable ignored) {
            // Diagnostics must never turn a handled server error into an app crash.
        }
    }

    /** Records the final submit result with its post operation type. */
    public static void captureSubmitFailure(Throwable throwable, Object operation) {
        if (throwable == null) return;

        try {
            String operationName = operation == null ? "unknown" : String.valueOf(operation);
            LOGGER.capture("POST_FAILURE", operationName, throwable);
        } catch (Throwable ignored) {
            // Diagnostics must never turn a handled submit failure into an app crash.
        }
    }

    static List<String> snapshotServerLogs() {
        return LOGGER.snapshotCaptured();
    }

    /**
     * Records download outcome for diagnostics. Device state (missing grant, refused
     * provider, blocked notifications) cannot be reproduced from a verbal report.
     */
    public static void captureDownloadFailure(String detail, @Nullable Throwable throwable) {
        LOGGER.capture("download_failure", detail, throwable);
    }

    private static boolean isEmptyXErrors(Throwable throwable) {
        if (!X_ERRORS_CLASS_NAME.equals(throwable.getClass().getName())) return false;
        return throwable.toString().startsWith(EMPTY_X_ERRORS_PREFIX);
    }
}
