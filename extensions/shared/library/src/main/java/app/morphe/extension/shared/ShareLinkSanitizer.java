/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.shared;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ShareLinkSanitizer {
    private final String firstPartyDomain;
    private final Set<String> functionalQueryParameters;
    private final Set<String> trackingQueryParameters;

    public ShareLinkSanitizer(
            String firstPartyDomain,
            Collection<String> functionalQueryParameters,
            Collection<String> trackingQueryParameters
    ) {
        this.firstPartyDomain = firstPartyDomain.toLowerCase(Locale.ROOT);
        this.functionalQueryParameters = new HashSet<>(functionalQueryParameters);
        this.trackingQueryParameters = new HashSet<>(trackingQueryParameters);
    }

    public String sanitize(String url, boolean enabled) {
        if (!enabled || url == null) {
            return url;
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String rawQuery = uri.getRawQuery();
            if (host == null || rawQuery == null) {
                return url;
            }

            List<String> filteredParameters = filterQuery(rawQuery, isFirstPartyHost(host));
            String filteredQuery = String.join("&", filteredParameters);
            if (filteredQuery.equals(rawQuery)) {
                return url;
            }
            return replaceQuery(url, filteredQuery, !filteredParameters.isEmpty());
        } catch (Exception ignored) {
            return url;
        }
    }

    private boolean isFirstPartyHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(firstPartyDomain)
                || normalizedHost.endsWith("." + firstPartyDomain);
    }

    private List<String> filterQuery(String rawQuery, boolean firstPartyHost) {
        List<String> filteredParameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&", -1)) {
            String parameterName = parameterName(parameter);
            boolean keepParameter = parameter.isEmpty()
                    ? !firstPartyHost
                    : firstPartyHost
                            ? functionalQueryParameters.contains(parameterName)
                            : !trackingQueryParameters.contains(parameterName);
            if (keepParameter) {
                filteredParameters.add(parameter);
            }
        }
        return filteredParameters;
    }

    private static String parameterName(String parameter) {
        int separatorIndex = parameter.indexOf('=');
        return separatorIndex < 0 ? parameter : parameter.substring(0, separatorIndex);
    }

    private static String replaceQuery(String url, String query, boolean keepQueryDelimiter) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }

        int fragmentStart = url.indexOf('#', queryStart);
        String fragment = fragmentStart < 0 ? "" : url.substring(fragmentStart);
        String sanitizedUrl = url.substring(0, queryStart);
        return keepQueryDelimiter
                ? sanitizedUrl + "?" + query + fragment
                : sanitizedUrl + fragment;
    }
}
