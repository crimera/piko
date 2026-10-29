package app.morphe.extension.newx.misc;

import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.ToStringParser;
import app.morphe.extension.crimera.settings.SettingsRegistry;

/**
 * Removes share attribution and optionally rewrites post links to the configured domain.
 *
 * The configured value is used as-is: no default extension is appended and no path is stripped.
 * A scheme prefix is tolerated so "https://fxtwitter.com" behaves like "fxtwitter.com". Input the
 * URL parser rejects leaves the link unchanged; a wrong domain is the caller's responsibility.
 */
public final class ShareUrlResolver {
    private static final String CUSTOM_DOMAIN_SETTING = "newx.content.custom_sharing_domain";
    private static final String PERMANENT_LINKS_SETTING = "newx.links.permanent_share_links";
    private static final String REMOVE_SHARE_TRACKING_QUERY_SETTING = "newx.links.remove_share_tracking_query";
    private static final String VALIDATION_PATH = "/i/status/0";
    private static final Pattern STATUS_PATH = Pattern.compile("^/[^/]+/status/([0-9]+)(/.*)?$");
    private static final Pattern PROFILE_USER_PATH = Pattern.compile("^/i/user/([0-9]+)(?:/.*)?$");
    private static final Pattern NUMERIC_USER_ID = Pattern.compile("[0-9]+");

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

    public static String rewriteSharedUrl(String urlString) {
        if (urlString == null) return null;

        try {
            return rewriteSharedUrl(
                    urlString,
                    SettingsRegistry.getBooleanOrDefault(PERMANENT_LINKS_SETTING, false),
                    SettingsRegistry.getBooleanOrDefault(REMOVE_SHARE_TRACKING_QUERY_SETTING, false),
                    SettingsRegistry.getStringOrDefault(CUSTOM_DOMAIN_SETTING, "")
            );
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to rewrite NewX shared URL", exception);
            return urlString;
        }
    }

    static String rewriteSharedUrl(String urlString, String customDomainInput) {
        return rewriteSharedUrl(urlString, false, true, customDomainInput);
    }

    static String rewriteSharedUrl(
            String urlString,
            boolean removeShareTrackingParameter,
            String customDomainInput
    ) {
        return rewriteSharedUrl(urlString, false, removeShareTrackingParameter, customDomainInput);
    }

    private static String rewriteSharedUrl(
            String urlString,
            boolean permanentLinksEnabled,
            boolean removeShareTrackingParameter,
            String customDomainInput
    ) {
        if (urlString == null) return null;

        try {
            String resolvedUrl = permanentLinksEnabled ? rewritePermanentPath(urlString) : urlString;
            String cleanedUrl = removeShareTrackingParameter
                    ? removeShareAttributionParameter(resolvedUrl)
                    : resolvedUrl;
            return rewriteCustomDomain(cleanedUrl, customDomainInput);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to rewrite NewX shared URL", exception);
            return urlString;
        }
    }

    /** Rewrites a profile route using the stable account ID carried by its profile model. */
    public static String rewriteProfileUrl(String urlString, Object profile) {
        if (urlString == null) return null;

        try {
            if (!SettingsRegistry.getBooleanOrDefault(PERMANENT_LINKS_SETTING, false)) return urlString;

            String userId = profileUserId(profile);
            if (userId == null) userId = profileUserIdFromUrl(urlString);
            if (userId == null) return urlString;

            return rewriteCustomDomain(permanentUrl("/intent/user", "user_id=" + userId));
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to rewrite NewX profile link", exception);
            return urlString;
        }
    }

    private static String rewritePermanentPath(String urlString) throws Exception {
        URL url = new URL(urlString);
        if (!isXHost(url.getHost())) return urlString;

        URI uri = url.toURI();
        String path = uri.getRawPath();
        Matcher status = STATUS_PATH.matcher(path);
        if (status.matches()) {
            String suffix = status.group(2) == null ? "" : status.group(2);
            return permanentUrl("/i/status/" + status.group(1) + suffix, uri.getRawQuery());
        }

        Matcher profile = PROFILE_USER_PATH.matcher(path);
        if (profile.matches()) {
            return permanentUrl("/intent/user", "user_id=" + profile.group(1));
        }
        return urlString;
    }

    private static String profileUserId(Object profile) {
        if (profile == null) return null;

        String profileId = ToStringParser.fieldValue(profile.toString(), "id");
        return isNumericUserId(profileId) ? profileId : null;
    }

    private static String profileUserIdFromUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        if (!isXHost(url.getHost())) return null;

        Matcher profile = PROFILE_USER_PATH.matcher(url.toURI().getRawPath());
        return profile.matches() ? profile.group(1) : null;
    }

    private static boolean isNumericUserId(String userId) {
        return userId != null && NUMERIC_USER_ID.matcher(userId).matches();
    }

    private static boolean isXHost(String host) {
        return host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com");
    }

    private static String permanentUrl(String path, String query) throws Exception {
        String file = query == null || query.isEmpty() ? path : path + "?" + query;
        return new URL("https", "x.com", -1, file).toExternalForm();
    }

    private static String rewriteCustomDomain(String urlString) throws Exception {
        return rewriteCustomDomain(
                urlString,
                SettingsRegistry.getStringOrDefault(CUSTOM_DOMAIN_SETTING, "")
        );
    }

    private static String rewriteCustomDomain(String urlString, String customDomainInput) throws Exception {
        String customDomain = normalizeCustomDomain(customDomainInput == null ? "" : customDomainInput);
        if (customDomain.isEmpty() || !isValidNormalizedDomain(customDomain)) return urlString;

        URL url = new URL(urlString);
        if (!isXHost(url.getHost())) return urlString;
        String rewrittenUrl = new URL(url.getProtocol(), customDomain, url.getPort(), url.getFile()).toString();
        return url.getRef() == null ? rewrittenUrl : rewrittenUrl + "#" + url.getRef();
    }

    private static String removeShareAttributionParameter(String urlString) throws Exception {
        URL url = new URL(urlString);
        if (!isXHost(url.getHost())) return urlString;

        int fragmentIndex = urlString.indexOf('#');
        String fragment = fragmentIndex < 0 ? "" : urlString.substring(fragmentIndex);
        String withoutFragment = fragmentIndex < 0 ? urlString : urlString.substring(0, fragmentIndex);
        int queryIndex = withoutFragment.indexOf('?');
        if (queryIndex < 0) return urlString;

        String query = withoutFragment.substring(queryIndex + 1);
        StringBuilder retainedParameters = new StringBuilder();
        boolean removedParameter = false;
        boolean firstRetainedParameter = true;
        for (String parameter : query.split("&", -1)) {
            int valueSeparator = parameter.indexOf('=');
            String name = valueSeparator < 0 ? parameter : parameter.substring(0, valueSeparator);
            if ("a".equals(name) || "s".equals(name)) {
                removedParameter = true;
                continue;
            }
            if (!firstRetainedParameter) retainedParameters.append('&');
            retainedParameters.append(parameter);
            firstRetainedParameter = false;
        }
        if (!removedParameter) return urlString;

        StringBuilder result = new StringBuilder(withoutFragment.substring(0, queryIndex));
        if (!firstRetainedParameter) result.append('?').append(retainedParameters);
        return result.append(fragment).toString();
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
