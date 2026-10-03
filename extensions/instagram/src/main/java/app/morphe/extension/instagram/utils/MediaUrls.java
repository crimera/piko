/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.utils;

/** Helpers for the CDN urls piko stores and re-opens later. */
public final class MediaUrls {

    private MediaUrls() {
    }

    /**
     * When a CDN url dies, as epoch millis, or 0 when it carries no expiry and so never does.
     * fbcdn links hold it in their own "oe" parameter as a hex epoch; permalinks have none.
     */
    public static long expiresAt(String url) {
        if (url == null) return 0L;
        try {
            int i = url.indexOf("oe=");
            // Must start a parameter, so "?oe=" or "&oe=" — not the tail of another name.
            if (i < 1 || (url.charAt(i - 1) != '?' && url.charAt(i - 1) != '&')) return 0L;
            int end = i + 3;
            while (end < url.length() && Character.digit(url.charAt(end), 16) >= 0) end++;
            if (end == i + 3) return 0L;
            return Long.parseLong(url.substring(i + 3, end), 16) * 1000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** True once {@link #expiresAt} has passed. Urls without an expiry never report expired. */
    public static boolean isExpired(String url) {
        long expiry = expiresAt(url);
        return expiry > 0 && expiry <= System.currentTimeMillis();
    }
}
