package app.morphe.extension.newx.settings;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;

/** Gates NewX diagnostics because Morphe's info and exception log methods are unconditional. */
public final class NewXLogger {
    static final String LOGGING_SETTING_ID = "newx.advanced.debug_tools.logging";
    static final String SERVER_LOGGING_SETTING_ID = "newx.advanced.debug_tools.server_logging";

    private static final String X_ERRORS_CLASS_NAME = "com.x.repositories.errors.XErrors";
    private static final String EMPTY_X_ERRORS_PREFIX = "XErrors(errors=[],";
    private static final int MAX_THROWABLE_TEXT_CHARS = 4 * 1024;
    private static final int MAX_STACK_TRACE_CHARS = 12 * 1024;
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(\\b(?:authorization|cookie|set-cookie|access_token|refresh_token|id_token|"
                    + "oauth_token|api[_-]?key|client_secret|password|secret|token|"
                    + "bounce[_-]?deeplink|bounceDeeplink|media[_-]?url|mediaUrl|"
                    + "request[_-]?body|response[_-]?body|payload|body)\\b\\s*[:=]\\s*)"
                    + "[^\\s,;)}\\]]+"
    );
    private static final Pattern URI_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?|ftp|content|file)://[^\\s\\[\\]<>\\\"']+"
    );
    private static final ServerLogBuffer SERVER_LOG_BUFFER = new ServerLogBuffer();

    private NewXLogger() {
    }

    public static boolean isLoggingEnabled() {
        return SettingsRegistry.getBooleanOrDefault(LOGGING_SETTING_ID, false);
    }

    public static boolean isServerLoggingEnabled() {
        // The settings registry is loaded lazily; the patch's opt-in is the safe fallback.
        return SettingsRegistry.getBooleanOrDefault(SERVER_LOGGING_SETTING_ID, true);
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

    /** Captures parsed server errors without allowing diagnostics to affect app behavior. */
    public static void captureServerError(Throwable throwable) {
        if (throwable == null) return;

        try {
            if (!isServerLoggingEnabled() || isEmptyXErrors(throwable)) return;
            recordEntry(formatErrorEntry("server_error", null, throwable));
        } catch (Throwable ignored) {
            // Diagnostics must never turn a handled server error into an app crash.
        }
    }

    /** Records the final submit result with its post operation type. */
    public static void captureSubmitFailure(Throwable throwable, Object operation) {
        if (throwable == null) return;

        try {
            if (!isServerLoggingEnabled()) return;
            String operationName = operation == null ? "unknown" : String.valueOf(operation);
            recordEntry(formatErrorEntry("POST_FAILURE", operationName, throwable));
        } catch (Throwable ignored) {
            // Diagnostics must never turn a handled submit failure into an app crash.
        }
    }

    static List<String> snapshotServerLogs() {
        return SERVER_LOG_BUFFER.snapshot();
    }

    private static void recordEntry(String entry) {
        SERVER_LOG_BUFFER.add(entry);
        if (isLoggingEnabled()) {
            Logger.printInfo(() -> entry);
        }
    }

    private static boolean isEmptyXErrors(Throwable throwable) {
        if (!X_ERRORS_CLASS_NAME.equals(throwable.getClass().getName())) return false;
        return throwable.toString().startsWith(EMPTY_X_ERRORS_PREFIX);
    }

    private static String formatErrorEntry(
            String event,
            String operation,
            Throwable throwable
    ) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
        String operationSuffix = operation == null
                ? ""
                : " operation=" + sanitizeText(operation);
        String throwableText = boundText(
                sanitizeText(throwable.toString()),
                MAX_THROWABLE_TEXT_CHARS
        );
        String stackTrace = boundText(
                sanitizeText(Log.getStackTraceString(throwable)),
                MAX_STACK_TRACE_CHARS
        );
        return "[" + timestamp + "] event=" + event + operationSuffix
                + " thread=" + Thread.currentThread().getName()
                + " type=" + throwable.getClass().getName() + "\n"
                + "error=" + throwableText + "\n"
                + "stacktrace=\n" + stackTrace;
    }

    private static String sanitizeText(String value) {
        String sanitized = SENSITIVE_VALUE_PATTERN.matcher(value)
                .replaceAll("$1[redacted]");
        return URI_PATTERN.matcher(sanitized).replaceAll("[url redacted]");
    }

    private static String boundText(String value, int maxChars) {
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars) + "\n[text truncated]";
    }
}
