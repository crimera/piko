package app.morphe.extension.xlite.misc;

import java.net.URI;
import java.net.URL;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.xlite.settings.SettingsRegistry;

/**
 * Rewrites the host of shared post links to the user-configured domain.
 *
 * The configured value is used as-is: no default extension is appended and no path is stripped.
 * A scheme prefix is tolerated so "https://fxtwitter.com" behaves like "fxtwitter.com". Input the
 * URL parser rejects leaves the link unchanged; a wrong domain is the caller's responsibility.
 */
public final class ShareUrlResolver {
    private static final String CUSTOM_DOMAIN_SETTING = "xlite.content.custom_sharing_domain";
    private static final String VALIDATION_PATH = "/i/status/0";

    private ShareUrlResolver() {
    }

    public static boolean isValidCustomDomain(String input) {
        if (input == null) return false;

        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) return true;

        String normalizedDomain = normalizeCustomDomain(trimmedInput);
        if (normalizedDomain.isEmpty()) return false;

        return isValidNormalizedDomain(normalizedDomain);
    }

    public static String changeDomain(String urlString) {
        try {
            String customDomain = normalizeCustomDomain(
                    SettingsRegistry.getString(CUSTOM_DOMAIN_SETTING)
            );
            if (customDomain.isEmpty()) return urlString;
            if (!isValidNormalizedDomain(customDomain)) return urlString;

            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")) {
                return new URL(url.getProtocol(), customDomain, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to rewrite X-Lite sharing domain", exception);
            return urlString;
        }
        return urlString;
    }

    private static String normalizeCustomDomain(String input) {
        String normalized = input.trim();
        if (normalized.startsWith("https://")) {
            return normalized.substring("https://".length());
        }
        if (normalized.startsWith("http://")) {
            return normalized.substring("http://".length());
        }
        return normalized;
    }

    private static boolean isValidNormalizedDomain(String domain) {
        try {
            URI candidate = new URI("https://" + domain + VALIDATION_PATH);
            return domain.equalsIgnoreCase(candidate.getHost());
        } catch (Exception exception) {
            return false;
        }
    }
}
