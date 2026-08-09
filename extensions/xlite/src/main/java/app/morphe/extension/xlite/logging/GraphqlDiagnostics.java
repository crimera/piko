package app.morphe.extension.xlite.logging;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.xlite.settings.SettingsActionHandler;
import app.morphe.extension.xlite.settings.SettingsRegistry;

/**
 * Generalized network diagnostics logger for X-Lite.
 *
 * <p>Writes bounded, sanitized records for the GraphQL and durable-action lifecycle when
 * {@code xlite.advanced.debug_tools.log_network_diagnostics} is enabled. The bridges are
 * Object-typed on purpose: they are injected into obfuscated host bytecode and must not
 * reference any release-specific app or Apollo model type.</p>
 *
 * <p>Events: request_started, response_received, graphql_error, transport_error,
 * app_result_failure, request_succeeded, request_queued, retry_scheduled,
 * durable_failure, permanent_failure.</p>
 */
public final class GraphqlDiagnostics {
    private static final String SETTING_ID = "xlite.advanced.debug_tools.log_network_diagnostics";
    private static final String ALLOWLIST_ID = SETTING_ID + ".operation_allowlist";
    private static final String QUEUE_ID = SETTING_ID + ".include_queue_lifecycle";
    private static final String METADATA_ID = SETTING_ID + ".include_request_metadata";

    private static final String FILE_NAME = "X-Lite-GraphQL-Diagnostics.txt";
    private static final String TAG = "PikoXLiteGraphQL";

    private static final int MAX_RECORD_LENGTH = 6000;
    private static final long MAX_FILE_BYTES = 1024L * 1024L;      // 1 MiB
    private static final long KEEP_FILE_BYTES = 512L * 1024L;      // 512 KiB tail after rotation

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(authorization|session_token|guest_token|access_token|oauth_token|auth_token|cookie|" +
                    "x-csrf-token)(\\s*[:=]\\s*)[^,}\\]\\s]+"
    );
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+");
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b([45][0-9][0-9])\\b");

    private static boolean settingsWarningLogged = false;

    private GraphqlDiagnostics() {
    }

    /** Hooked at the shared GraphQL request entry (mutation and common query path). */
    public static void logRequestStarted(Object operation) {
        String operationName = operationName(operation);
        if (!isEnabled() || !matchesOperation(operationName)) return;

        StringBuilder record = new StringBuilder();
        record.append("event=request_started\n");
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("operation=").append(sanitize(operationName)).append('\n');
        appendMetadata(record, operation);
        write(record.toString());
    }

    /** Hooked at the shared Apollo-to-app response mapper; inspects errors and embedded exceptions. */
    public static void logApolloResponse(Object response) {
        if (!isEnabled()) return;

        ResponseSummary summary = responseSummary(response);
        if (summary == null) return; // unparseable Apollo summary; do not log data payloads wholesale
        String errorsField = summary.errors;
        String exceptionField = summary.exception;

        boolean hasErrors = errorsField != null && !"null".equals(errorsField) && !errorsField.isEmpty();
        boolean hasException = exceptionField != null && !"null".equals(exceptionField) && !exceptionField.isEmpty();

        StringBuilder record = new StringBuilder();
        record.append("event=").append(hasErrors ? "graphql_error" : "response_received").append('\n');
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("transport=graphql\n");
        record.append("graphql_errors_present=").append(hasErrors).append('\n');
        if (hasErrors) {
            record.append("graphql_errors=").append(sanitize(truncate(errorsField, MAX_RECORD_LENGTH))).append('\n');
        }
        record.append("embedded_exception=").append(hasException ? "present" : "none").append('\n');
        if (hasException) {
            record.append("exception_detail=").append(sanitize(truncate(exceptionField, MAX_RECORD_LENGTH))).append('\n');
            Matcher status = HTTP_STATUS_PATTERN.matcher(exceptionField);
            if (status.find()) {
                record.append("http_status=").append(status.group(1)).append('\n');
            }
        }
        write(record.toString());
    }

    /** Hooked at each return of the shared response mapper: the parsed app result. */
    public static void logAppResult(Object result) {
        if (!isEnabled()) return;

        boolean failure = result != null && result.getClass().getName().endsWith("b$a");
        StringBuilder record = new StringBuilder();
        if (failure) {
            record.append("event=app_result_failure\n");
        } else {
            record.append("event=request_succeeded\n");
        }
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("transport=graphql\n");
        if (result == null) {
            record.append("result=null\n");
        } else {
            record.append("result_class=").append(result.getClass().getName()).append('\n');
            if (failure) {
                record.append("result_detail=")
                        .append(sanitize(truncate(String.valueOf(result), MAX_RECORD_LENGTH)))
                        .append('\n');
            } else {
                appendMetadata(record, result);
            }
        }
        write(record.toString());
    }

    /** Hooked at the shared thrown-exception helper (DNS/TLS/timeout/socket/Apollo chains). */
    public static void logFailure(String operationName, Throwable throwable) {
        String safeName = sanitize(operationName);
        if (!isEnabled() || !matchesOperation(operationName)) return;

        StringBuilder record = new StringBuilder();
        record.append("event=transport_error\n");
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("operation=").append(safeName).append('\n');
        if (throwable == null) {
            record.append("throwable=null\n");
        } else {
            record.append("exception_class=").append(throwable.getClass().getName()).append('\n');
            String message = throwable.getMessage();
            if (message != null) {
                record.append("exception_message=").append(sanitize(truncate(message, 1200))).append('\n');
            }
            Matcher status = HTTP_STATUS_PATTERN.matcher(String.valueOf(throwable));
            if (status.find()) {
                record.append("http_status=").append(status.group(1)).append('\n');
            }
            record.append("cause_chain=")
                    .append(sanitize(truncate(causeChain(throwable), MAX_RECORD_LENGTH)))
                    .append('\n');
        }
        write(record.toString());
    }

    /** Hooked at the durable-action enqueue path of the post action repository. */
    public static void logDurableQueued(Object actionType, String actionName) {
        if (!isEnabled() || !queueLifecycleEnabled()) return;

        StringBuilder record = new StringBuilder();
        record.append("event=request_queued\n");
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("action=").append(sanitize(actionName)).append('\n');
        record.append("post_action_type=").append(sanitize(String.valueOf(actionType))).append('\n');
        record.append("queue_state=durable\n");
        write(record.toString());
    }

    /** Hooked before every retry persistence call in the durable action worker. */
    public static void logDurableRetry(Object action) {
        if (!isEnabled() || !queueLifecycleEnabled()) return;

        StringBuilder record = new StringBuilder();
        record.append("event=retry_scheduled\n");
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("action=").append(sanitize(truncate(String.valueOf(action), 1200))).append('\n');
        write(record.toString());
    }

    /** Hooked at the durable-action completion boundary (success or failed attempt). */
    public static void logDurableCompleted(String actionId, Object result, String message) {
        if (!isEnabled() || !queueLifecycleEnabled()) return;

        String outcome = String.valueOf(result);
        boolean success = "Success".equals(outcome) || (outcome != null && outcome.contains("Success"));
        StringBuilder record = new StringBuilder();
        record.append("event=").append(success ? "request_succeeded" : "durable_failure").append('\n');
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("action_id=").append(sanitize(actionId)).append('\n');
        record.append("outcome=").append(sanitize(outcome)).append('\n');
        if (message != null) {
            record.append("message=").append(sanitize(truncate(message, 1200))).append('\n');
        }
        write(record.toString());
    }

    /** Hooked at the durable action worker's terminal drop helper. */
    public static void logDurableDropped(Object cause, Object action, String detail) {
        if (!isEnabled() || !queueLifecycleEnabled()) return;

        StringBuilder record = new StringBuilder();
        record.append("event=permanent_failure\n");
        record.append("timestamp_ms=").append(System.currentTimeMillis()).append('\n');
        record.append("action=").append(sanitize(truncate(String.valueOf(action), 1200))).append('\n');
        record.append("drop_cause=").append(sanitize(String.valueOf(cause))).append('\n');
        if (detail != null) {
            record.append("detail=").append(sanitize(truncate(detail, 1200))).append('\n');
        }
        write(record.toString());
    }

    /** Settings action: delete the diagnostics file. */
    public static final class ClearFileAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            try {
                File file = diagnosticsFile();
                boolean deleted = file != null && file.exists() && file.delete();
                PikoUtils.toast(deleted ? "Diagnostics file cleared" : "No diagnostics file found");
            } catch (RuntimeException exception) {
                Log.e(TAG, "Could not clear diagnostics file", exception);
            }
        }
    }

    private static boolean isEnabled() {
        try {
            return SettingsRegistry.getBooleanOrDefault(SETTING_ID, false);
        } catch (RuntimeException exception) {
            warnOnce("Reading diagnostics setting failed", exception);
            return false;
        }
    }

    private static boolean queueLifecycleEnabled() {
        try {
            return SettingsRegistry.getBooleanOrDefault(QUEUE_ID, true);
        } catch (RuntimeException exception) {
            warnOnce("Reading queue diagnostics setting failed", exception);
            return true;
        }
    }

    private static boolean metadataEnabled() {
        try {
            return SettingsRegistry.getBooleanOrDefault(METADATA_ID, false);
        } catch (RuntimeException exception) {
            warnOnce("Reading metadata diagnostics setting failed", exception);
            return false;
        }
    }

    private static void warnOnce(String message, RuntimeException exception) {
        if (settingsWarningLogged) return;
        settingsWarningLogged = true;
        Log.w(TAG, message, exception);
    }

    /** Empty allowlist (the default) means every operation. Entries match case-insensitively by substring. */
    private static boolean matchesOperation(String operationName) {
        if (operationName == null || operationName.isEmpty()) return false;
        String allowlist;
        try {
            allowlist = SettingsRegistry.getString(ALLOWLIST_ID);
        } catch (RuntimeException exception) {
            warnOnce("Reading operation allowlist failed", exception);
            allowlist = "";
        }
        if (allowlist == null || allowlist.trim().isEmpty()) return true;
        String normalized = operationName.toLowerCase(Locale.US);
        for (String entry : allowlist.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty() && normalized.contains(trimmed.toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    private static void appendMetadata(StringBuilder record, Object object) {
        if (!metadataEnabled()) return;
        record.append("details=")
                .append(sanitize(truncate(String.valueOf(object), 1200)))
                .append('\n');
    }

    /** Apollo v2 operations are data classes: toString starts with the operation name. */
    private static String operationName(Object operation) {
        if (operation == null) return "unknown";
        String text = String.valueOf(operation);
        int separator = text.indexOf('(');
        if (separator > 0) return text.substring(0, separator);
        return truncate(text, 96);
    }

    private static ResponseSummary responseSummary(Object response) {
        if (response == null) return null;
        String text = String.valueOf(response);
        int errorsIndex = indexOfField(text, "errors");
        if (errorsIndex < 0) return null;

        String remainder = text.substring(errorsIndex + "errors".length()).trim();
        if (!remainder.startsWith("=")) {
            int equals = remainder.indexOf('=');
            if (equals < 0) return null;
            remainder = remainder.substring(equals + 1).trim();
        } else {
            remainder = remainder.substring(1).trim();
        }

        String errorsField = null;
        String exceptionField = null;
        int exceptionIndex = indexOfField(remainder, "exception");
        if (exceptionIndex >= 0) {
            errorsField = remainder.substring(0, exceptionIndex).trim();
            exceptionField = remainder.substring(exceptionIndex + "exception".length() + 1).trim();
        } else {
            errorsField = remainder;
        }
        int end = errorsField.length();
        if (end > 0 && errorsField.charAt(end - 1) == ')') {
            errorsField = errorsField.substring(0, end - 1);
        }
        if (errorsField != null && errorsField.isEmpty()) errorsField = null;
        if (exceptionField != null && exceptionField.isEmpty()) exceptionField = null;
        return new ResponseSummary(errorsField, exceptionField);
    }

    private static int indexOfField(String text, String field) {
        int index = text.indexOf(field + "=");
        if (index < 0) index = text.indexOf(field + " =");
        return index;
    }

    private static String causeChain(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 6) {
            if (chain.length() > 0) chain.append(" -> ");
            chain.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null) {
                chain.append(": ").append(message);
            }
            current = current.getCause();
            depth++;
        }
        return chain.toString();
    }

    private static String sanitize(String text) {
        if (text == null) return "null";
        String sanitized = SECRET_PATTERN.matcher(text).replaceAll("$1$2<redacted>");
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("bearer <redacted>");
        return truncate(sanitized, MAX_RECORD_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...[truncated]";
    }

    private static synchronized void write(String record) {
        String output = record + "\n";
        Log.e(TAG, record);
        try {
            File file = diagnosticsFile();
            if (file == null) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.isDirectory() && !parent.mkdirs()) {
                Log.w(TAG, "Could not create " + parent);
                return;
            }
            try (FileOutputStream outputStream = new FileOutputStream(file, true)) {
                outputStream.write(output.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            if (file.length() > MAX_FILE_BYTES) rotate(file);
        } catch (IOException | RuntimeException exception) {
            Log.e(TAG, "Could not write " + FILE_NAME, exception);
        }
    }

    private static File diagnosticsFile() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null) return null;
        return new File(new File(downloads, "Piko"), FILE_NAME);
    }

    /** Keeps the tail of an oversized diagnostics file instead of growing unbounded. */
    private static void rotate(File file) throws IOException {
        byte[] tail = new byte[(int) KEEP_FILE_BYTES];
        int read = 0;
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long start = Math.max(0L, randomAccessFile.length() - KEEP_FILE_BYTES);
            randomAccessFile.seek(start);
            read = randomAccessFile.read(tail);
        }
        String header = "\n=== rotated; older records pruned ===\n";
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(header.getBytes(StandardCharsets.UTF_8));
            if (read > 0) {
                outputStream.write(tail, 0, read);
            }
            outputStream.flush();
        }
    }

    private static final class ResponseSummary {
        final String errors;
        final String exception;

        ResponseSummary(String errors, String exception) {
            this.errors = errors;
            this.exception = exception;
        }
    }
}