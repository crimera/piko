package app.morphe.extension.shared;

/**
 * Test-source stand-in for {@code app.morphe.extension.shared.Logger} from the {@code compileOnly}
 * morphe-extensions-library artifact, which is absent from the unit-test runtime classpath.
 * Avoids Android {@code android.util.Log} so exception-fallback paths can be exercised under JUnit.
 */
public final class Logger {

    public interface LogMessage {
        String buildMessageString();
    }

    private Logger() {
    }

    public static void printInfo(LogMessage message) {
        print("INFO", null, message);
    }

    public static void printInfo(LogMessage message, Exception exception) {
        print("INFO", exception, message);
    }

    public static void printException(LogMessage message) {
        print("ERROR", null, message);
    }

    public static void printException(LogMessage message, Throwable throwable) {
        print("ERROR", throwable, message);
    }

    private static void print(String level, Throwable throwable, LogMessage message) {
        System.err.println(
                level + " [NewX test logger]: " + (message == null ? null : message.buildMessageString())
        );
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }
}