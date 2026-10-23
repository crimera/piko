/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;

/**
 * A media's picture as its embed page lists it to a link preview's crawler, at upload size, where the
 * app lists a photo story with music as a smaller video cover. Look it up off the main thread.
 */
final class EmbedCover {
    private static final String EMBED = "https://www.instagram.com/p/%s/embed/captioned/";
    // To a browser's User-Agent the embed page answers an empty shell.
    private static final String CRAWLER = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)";
    // The page's post, a JSON string in its script.
    private static final String CONTEXT = "\"contextJSON\":";
    private static final int TIMEOUT_MS = 10_000;

    final String url;
    final int width;
    final int height;

    private EmbedCover(String url, int width, int height) {
        this.url = url;
        this.width = width;
        this.height = height;
    }

    /**
     * The picture the embed page of media {@code mediaId}, by its {@code shortcode}, lists when its
     * short side exceeds {@code shortSide}; null when it does not, or on any failure.
     */
    static EmbedCover largerThan(String shortcode, String mediaId, int shortSide) {
        try {
            String page = fetch(String.format(EMBED, shortcode));
            int at = page.indexOf(CONTEXT);
            Object context = at < 0 ? null : new JSONTokener(page.substring(at + CONTEXT.length())).nextValue();
            if (!(context instanceof String)) return null; // no post: private, expired or gone
            JSONObject media = new JSONObject((String) context)
                    .getJSONObject("gql_data")
                    .getJSONObject("shortcode_media");
            // Guards against the page answering for another media, such as a carousel for one of its children.
            if (!mediaId.equals(media.getString("id"))) return null;
            JSONObject dimensions = media.getJSONObject("dimensions");
            int width = dimensions.getInt("width");
            int height = dimensions.getInt("height");
            return Math.min(width, height) > shortSide
                    ? new EmbedCover(media.getString("display_url"), width, height)
                    : null;
        } catch (Exception e) {
            Logger.printInfo(() -> "No embed cover for " + shortcode, e);
            return null;
        }
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestProperty("User-Agent", CRAWLER);
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("embed page answered HTTP " + conn.getResponseCode());
            }
            return Requester.parseString(conn);
        } finally {
            conn.disconnect();
        }
    }
}
