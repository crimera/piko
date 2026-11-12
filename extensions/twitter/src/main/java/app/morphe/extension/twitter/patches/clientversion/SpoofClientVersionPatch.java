/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.clientversion;

import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class SpoofClientVersionPatch {
    private static final Pattern USER_AGENT_VERSION = Pattern.compile("^TwitterAndroid/[^ ]+ \\(\\d+-");

    // The patch replaces these values with its options.
    private static String version() {
        return "12.41.0-prod.01";
    }

    private static int versionCode() {
        return 312410001;
    }

    private static final ThreadLocal<java.net.URI> REQUEST = new ThreadLocal<>();

    /** Injection point: the legacy stack is about to add its client headers for this request. */
    public static void onRequest(java.net.URI uri) {
        REQUEST.set(uri);
        if (DEBUG && uri != null) android.util.Log.i("PikoSpoof", String.valueOf(uri.getPath()));
    }

    private static final boolean DEBUG = false;

    private static boolean isXChatRequest() {
        java.net.URI uri = REQUEST.get();
        String path = uri == null ? null : uri.getPath();
        return path != null && path.toLowerCase(java.util.Locale.ROOT).contains("xchat");
    }

    /** Injection point: X-Twitter-Client-Version of the legacy stack. */
    public static String legacyVersion(String version) {
        return isXChatRequest() ? version() : version;
    }

    /** Injection point: User-Agent of the legacy stack. */
    public static String legacyUserAgent(String userAgent) {
        return isXChatRequest() ? userAgent(userAgent) : userAgent;
    }

    /**
     * Injection point: the X Chat (com.x) network stack's user agent.
     * The legacy stack keeps the app's real user agent.
     */
    public static String userAgent(String userAgent) {
        if (userAgent == null) return null;
        return USER_AGENT_VERSION.matcher(userAgent)
            .replaceFirst("TwitterAndroid/" + version() + " (" + versionCode() + "-");
    }
}
