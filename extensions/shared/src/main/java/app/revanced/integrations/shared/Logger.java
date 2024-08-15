package app.revanced.integrations.shared;

import static app.revanced.integrations.shared.settings.BaseSettings.DEBUG;
import static app.revanced.integrations.shared.settings.BaseSettings.DEBUG_STACKTRACE;
import static app.revanced.integrations.shared.settings.BaseSettings.DEBUG_TOAST_ON_ERROR;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.revanced.integrations.shared.settings.BaseSettings;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Logger {

    /**
     * Log messages using lambdas.
     */
    public interface LogMessage {
        @NonNull
        String buildMessageString();

        /**
         * @return For outer classes, this returns {@link Class#getSimpleName()}.
         * For inner, static, or anonymous classes, this returns the simple name of the enclosing class.<br>
         * <br>
         * For example, each of these classes return 'SomethingView':
         * <code>
         * com.company.SomethingView
         * com.company.SomethingView$StaticClass
         * com.company.SomethingView$1
         * </code>
         */
        private String findOuterClassSimpleName() {
            var selfClass = this.getClass();

            String fullClassName = selfClass.getName();
            final int dollarSignIndex = fullClassName.indexOf('$');
            if (dollarSignIndex == -1) {
                return selfClass.getSimpleName(); // already an outer class
            }

            // class is inner, static, or anonymous
            // parse the simple name full name
            // a class with no package returns index of -1, but incrementing gives index zero which is correct
            final int simpleClassNameStartIndex = fullClassName.lastIndexOf('.') + 1;
            return fullClassName.substring(simpleClassNameStartIndex, dollarSignIndex);
        }
    }

    private static final String REVANCED_LOG_PREFIX = "revanced: ";

    /**
     * Logs debug messages under the outer class name of the code calling this method.
     * Whenever possible, the log string should be constructed entirely inside {@link LogMessage#buildMessageString()}
     * so the performance cost of building strings is paid only if {@link BaseSettings#DEBUG} is enabled.
     */
    public static void printDebug(@NonNull LogMessage message) {
        if (DEBUG.get()) {
            var messageString = message.buildMessageString();

            if (DEBUG_STACKTRACE.get()) {
                var builder = new StringBuilder(messageString);
                var sw = new StringWriter();
                new Throwable().printStackTrace(new PrintWriter(sw));

                builder.append('\n').append(sw);
                messageString = builder.toString();
            }

            Log.d(REVANCED_LOG_PREFIX + message.findOuterClassSimpleName(), messageString);
        }
    }

    /**
     * Logs information messages using the outer class name of the code calling this method.
     */
    public static void printInfo(@NonNull LogMessage message) {
        printInfo(message, null);
    }

    /**
     * Logs information messages using the outer class name of the code calling this method.
     */
    public static void printInfo(@NonNull LogMessage message, @Nullable Exception ex) {
        String logTag = REVANCED_LOG_PREFIX + message.findOuterClassSimpleName();
        String logMessage = message.buildMessageString();
        if (ex == null) {
            Log.i(logTag, logMessage);
        } else {
            Log.i(logTag, logMessage, ex);
        }
    }

    /**
     * Logs exceptions under the outer class name of the code calling this method.
     */
    public static void printException(@NonNull LogMessage message) {
        printException(message, null, null);
    }

    /**
     * Logs exceptions under the outer class name of the code calling this method.
     */
    public static void printException(@NonNull LogMessage message, @Nullable Throwable ex) {
        printException(message, ex, null);
    }

    /**
     * Logs exceptions under the outer class name of the code calling this method.
     * <p>
     * If the calling code is showing it's own error toast,
     * instead use {@link #printInfo(LogMessage, Exception)}
     *
     * @param message          log message
     * @param ex               exception (optional)
     * @param userToastMessage user specific toast message to show instead of the log message (optional)
     */
    public static void printException(@NonNull LogMessage message, @Nullable Throwable ex,
                                      @Nullable String userToastMessage) {
        String messageString = message.buildMessageString();
        String outerClassSimpleName = message.findOuterClassSimpleName();
        String logMessage = REVANCED_LOG_PREFIX + outerClassSimpleName;
        if (ex == null) {
            Log.e(logMessage, messageString);
        } else {
            Log.e(logMessage, messageString, ex);
        }
        if (DEBUG_TOAST_ON_ERROR.get()) {
            String toastMessageToDisplay = (userToastMessage != null)
                    ? userToastMessage
                    : outerClassSimpleName + ": " + messageString;
            Utils.showToastLong(toastMessageToDisplay);
        }
    }

    /**
     * Logging to use if {@link BaseSettings#DEBUG} or {@link Utils#context} may not be initialized.
     * Always logs even if Debugging is not enabled.
     * Normally this method should not be used.
     */
    public static void initializationError(@NonNull Class<?> callingClass, @NonNull String message, @Nullable Exception ex) {
        Log.e(REVANCED_LOG_PREFIX + callingClass.getSimpleName(), message, ex);
    }

}