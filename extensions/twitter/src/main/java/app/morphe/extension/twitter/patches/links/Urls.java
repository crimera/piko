/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.links;

import com.twitter.model.json.core.JsonUrlEntity;

import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.settings.SettingsStatus;
import app.morphe.extension.crimera.PikoUtils;
import com.x.models.ContextualPost;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Urls {
    private static final Pattern POST_URL_PATTERN = Pattern.compile(
            "^(https://(?:x\\.com|twitter\\.com)/)[^/]+(/status/.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final boolean unShortUrl;
    static {
        unShortUrl = SettingsStatus.unshortenlink && Pref.unShortUrl();
    }
    public static JsonUrlEntity unshort(JsonUrlEntity entity) {
        try {
            if(unShortUrl){
                entity.e = entity.c;
            }
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
        return entity;
    }

    private static final String FULL_DOMAIN_PATTERN = "(?i)(?:[a-z0-9-]+\\.)+[a-z]{2,63}";
    private static final String BARE_DOMAIN_PATTERN = "(?i)[a-z0-9-]+";

    public static String changeDomain(String urlString) {
        if (urlString == null) return null;

        String customDomainName = normalizeCustomDomain(Pref.customSharingDomain());
        if (customDomainName.length() < 1) return urlString;
        if (customDomainName.matches(BARE_DOMAIN_PATTERN)) {
            customDomainName += ".com";
        }
        if (!customDomainName.matches(FULL_DOMAIN_PATTERN)) return urlString;

        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (host.equalsIgnoreCase("x.com") || host.equalsIgnoreCase("twitter.com")) {
                return new URL(url.getProtocol(), customDomainName, url.getPort(), url.getFile()).toString();
            }
        } catch (Exception ex) {
            PikoUtils.logger(ex);
        }
        return urlString;
    }

    private static String normalizeCustomDomain(String value) {
        if (value == null) return "";

        String normalized = value.trim();
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String hookShareSheetLink(ContextualPost contextualPost, String link){
        try {
            if (SettingsStatus.legacyShareLink) {
                Matcher matcher = POST_URL_PATTERN.matcher(link);
                if (matcher.matches()) {
                    String username = contextualPost.getCanonicalPost().getAuthor().getScreenName();
                    link = matcher.group(1) + username + matcher.group(2);
                }
            }
        }catch (Exception ex) {
            PikoUtils.logger(ex);
        }
        return changeDomain(link);
    }
}
