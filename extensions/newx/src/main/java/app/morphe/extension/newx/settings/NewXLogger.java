package app.morphe.extension.newx.settings;

import app.morphe.extension.shared.Logger;

/** Gates NewX diagnostics because Morphe's info and exception log methods are unconditional. */
public final class NewXLogger {
    static final String LOGGING_SETTING_ID = "newx.advanced.debug_tools.logging";

    private NewXLogger() {
    }

    public static boolean isLoggingEnabled() {
        return SettingsRegistry.getBooleanOrDefault(LOGGING_SETTING_ID, false);
    }

    public static void printInfo(Logger.LogMessage message) {
        if (!isLoggingEnabled()) return;
        Logger.printInfo(message);
    }

    public static void printInfo(Logger.LogMessage message, Exception exception) {
        if (!isLoggingEnabled()) return;
        Logger.printInfo(message, exception);
    }

    public static void printException(Logger.LogMessage message) {
        if (!isLoggingEnabled()) return;
        Logger.printException(message);
    }

    public static void printException(Logger.LogMessage message, Throwable throwable) {
        if (!isLoggingEnabled()) return;
        Logger.printException(message, throwable);
    }

    public static void logger(Object value) {
        if (!isLoggingEnabled()) return;
        if (value instanceof Exception exception) {
            Logger.printInfo(() -> String.valueOf(value), exception);
            return;
        }
        Logger.printInfo(() -> String.valueOf(value));
    }
}
