/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.clientversion;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class SpoofClientVersionPatch {
    private static final Pattern USER_AGENT_VERSION = Pattern.compile("^TwitterAndroid/[^ ]+ \\(\\d+-");

    // The patch replaces these values with its options.
    private static String version() {
        return "12.44.0-prod.01";
    }

    private static int versionCode() {
        return 312440001;
    }

    // The patch fills this with every X Chat GraphQL operation name of the app (comma separated).
    private static String xchatOperations() {
        return "";
    }

    private static Set<String> operations;

    /** Injection point */
    public static boolean isXChatRequest(URI uri) {
        String path = uri == null ? null : uri.getPath();
        if (path == null) return false;
        if (path.toLowerCase(Locale.ROOT).contains("xchat")) return true;
        if (operations == null) {
            operations = new HashSet<>(Arrays.asList(xchatOperations().split(",")));
        }
        int slash = path.lastIndexOf('/');
        return path.contains("/graphql/") && operations.contains(path.substring(slash + 1));
    }

    /** Injection point: Replaces the version name and version code in a user agent. */
    public static String userAgent(String userAgent) {
        if (userAgent == null) return null;
        return USER_AGENT_VERSION.matcher(userAgent)
            .replaceFirst("TwitterAndroid/" + version() + " (" + versionCode() + "-");
    }
}
