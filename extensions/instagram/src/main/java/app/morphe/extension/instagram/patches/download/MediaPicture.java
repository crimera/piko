/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.graphics.BitmapFactory;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;

/**
 * A post's picture at a size the app does not list: a reel's cover as its embed page links it, above
 * 1080 px, or the upload as instagram.com's media redirect serves it, resized to fit 1080 px. Look it
 * up off the main thread.
 */
final class MediaPicture {
    private static final String EMBED = "https://www.instagram.com/p/%s/embed/captioned/";
    private static final String REDIRECT = "https://www.instagram.com/p/%s/media/?size=l";
    private static final String CONTEXT = "\"contextJSON\":";
    private static final int REDIRECT_WIDTH = 1080;
    private static final int TIMEOUT_MS = 10_000;

    final String url;
    final int width;
    final int height;

    private MediaPicture(String url, int width, int height) {
        this.url = url;
        this.width = width;
        this.height = height;
    }

    /**
     * The largest picture of post {@code shortcode}, sized by its own header, when its short side
     * exceeds {@code shortSide}, or null when none does, or on any failure.
     */
    static MediaPicture largerThan(String shortcode, int shortSide) {
        // The embed page's size field can disagree with the file it links, as for a photo story
        // with music, so only the file's header decides.
        String embedded = embeddedPicture(shortcode);
        MediaPicture picture = embedded == null ? null : measured(embedded, shortSide);
        if (picture != null && picture.width > REDIRECT_WIDTH) return picture;
        MediaPicture redirected = measured(String.format(REDIRECT, shortcode), shortSide);
        if (redirected == null) return picture;
        return picture == null || redirected.width * redirected.height >= picture.width * picture.height
                ? redirected
                : picture;
    }

    /** The picture URL the embed page lists for post {@code shortcode}, or null when it lists no post. */
    private static String embeddedPicture(String shortcode) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(String.format(EMBED, shortcode)).openConnection();
            String page;
            try {
                // A browser's User-Agent gets an empty shell. Android's default gets the post.
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
                page = Requester.parseString(conn);
            } finally {
                conn.disconnect();
            }
            int at = page.indexOf(CONTEXT);
            Object context = at < 0 ? null : new JSONTokener(page.substring(at + CONTEXT.length())).nextValue();
            if (!(context instanceof String)) return null; // no post: private, expired or gone
            return new JSONObject((String) context).getJSONObject("gql_data")
                    .getJSONObject("shortcode_media").getString("display_url");
        } catch (Exception e) {
            Logger.printInfo(() -> "No embed picture for " + shortcode, e);
            return null;
        }
    }

    /**
     * The picture at {@code url}, after any redirect, sized by its own header, when its short side
     * exceeds {@code shortSide}, or null when it does not or on any failure.
     */
    private static MediaPicture measured(String url, int shortSide) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            try {
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream picture = conn.getInputStream()) {
                    BitmapFactory.decodeStream(picture, null, bounds);
                }
                return Math.min(bounds.outWidth, bounds.outHeight) > shortSide
                        ? new MediaPicture(conn.getURL().toString(), bounds.outWidth, bounds.outHeight)
                        : null;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Logger.printInfo(() -> "Could not measure a media picture", e);
            return null;
        }
    }
}
