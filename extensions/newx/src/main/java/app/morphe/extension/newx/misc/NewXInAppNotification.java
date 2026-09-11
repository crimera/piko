package app.morphe.extension.newx.misc;

import app.morphe.extension.shared.Utils;

/**
 * Small release-neutral bridge to NewX's in-app notification pipeline.
 *
 * <p>The native notification implementation and model classes are obfuscated and are therefore
 * filled in by the NewX patch at patch time.  This class deliberately exposes only Object/String
 * contracts to the extension.  If the native facade has not been constructed yet, or if the
 * injected call cannot be completed, the original short Toast remains the fallback.</p>
 */
public final class NewXInAppNotification {
    private static volatile Object facade;

    private NewXInAppNotification() {
    }

    /** Stores the native in-app notification facade once its constructor has completed. */
    public static void capture(Object value) {
        if (value != null) facade = value;
    }

    /** Shows a status message in NewX, falling back to the existing short Toast. */
    public static void show(String message) {
        boolean sent = false;
        try {
            sent = send(message);
        } catch (Throwable ignored) {
            // A target-specific bridge failure must not remove the status notification entirely.
        }
        if (!sent) Utils.showToastShort(message);
    }

    /** Shows a status message while identifying the post's author. */
    public static void showForUser(String message, String username) {
        show(formatForUser(message, username));
    }

    static String formatForUser(String message, String username) {
        if (username == null) return message;

        String normalizedUsername = username.trim();
        if (normalizedUsername.isEmpty()) return message;
        if (normalizedUsername.charAt(0) == '@') {
            normalizedUsername = normalizedUsername.substring(1).trim();
        }
        if (normalizedUsername.isEmpty()) return message;

        return message + " — @" + normalizedUsername;
    }

    // Replaced with direct smali calls to the resolved NewX notification model/sender.
    private static boolean send(String message) {
        return false;
    }
}
