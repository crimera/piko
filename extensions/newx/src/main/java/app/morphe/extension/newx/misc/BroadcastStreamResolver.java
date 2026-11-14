/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Resolves Twitter/X broadcast and live stream replays into standard DownloadItem objects
 * with selectable resolution options from the HLS master playlist.
 */
final class BroadcastStreamResolver {
    private static final String TWITTER_BEARER_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA";

    private static final Pattern MEDIA_KEY_PATTERN =
            Pattern.compile("CardBinding\\(key=broadcast_media_key,\\s*value=(?:StringValue\\(value=([^)]*)\\)|[^)]*value=([^),]+))");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("CardBinding\\(key=broadcast_title,\\s*value=(?:StringValue\\(value=([^)]*)\\)|[^)]*value=([^),]+))");
    private static final Pattern STATE_PATTERN =
            Pattern.compile("CardBinding\\(key=broadcast_state,\\s*value=(?:StringValue\\(value=([^)]*)\\)|[^)]*value=([^),]+))");
    private static final Pattern ID_PATTERN =
            Pattern.compile("CardBinding\\(key=broadcast_id,\\s*value=(?:StringValue\\(value=([^)]*)\\)|[^)]*value=([^),]+))");
    private static final Pattern THUMBNAIL_PATTERN =
            Pattern.compile("CardBinding\\(key=full_size_thumbnail_url,\\s*value=ImageValue\\([^)]*url=([^),]+)");

    private static final Pattern STREAM_INF_PATTERN =
            Pattern.compile("#EXT-X-STREAM-INF:.*?(?:RESOLUTION=(\\d+x\\d+))?.*?(?:BANDWIDTH=(\\d+))?");

    static final class BroadcastMetadata {
        final String mediaKey;
        final String broadcastId;
        final String title;
        final String state;
        final String thumbnailUrl;

        BroadcastMetadata(String mediaKey, String broadcastId, String title, String state, String thumbnailUrl) {
            this.mediaKey = mediaKey;
            this.broadcastId = broadcastId;
            this.title = title;
            this.state = state;
            this.thumbnailUrl = thumbnailUrl;
        }

        boolean isLive() {
            return "RUNNING".equalsIgnoreCase(state);
        }
    }

    private BroadcastStreamResolver() {
    }

    static boolean isBroadcast(Object legacyCard) {
        if (legacyCard == null) return false;
        String text = legacyCard.toString();
        return text != null
                && (text.contains("Broadcast") || text.contains("broadcast"))
                && text.contains("broadcast_media_key");
    }

    @Nullable
    static BroadcastMetadata parseMetadata(Object legacyCard) {
        if (legacyCard == null) return null;
        String text = legacyCard.toString();
        if (text == null) return null;

        String mediaKey = matchGroup(MEDIA_KEY_PATTERN, text);
        if (mediaKey == null || mediaKey.isEmpty()) return null;

        String title = matchGroup(TITLE_PATTERN, text);
        String state = matchGroup(STATE_PATTERN, text);
        String broadcastId = matchGroup(ID_PATTERN, text);
        String thumbnail = matchGroup(THUMBNAIL_PATTERN, text);

        return new BroadcastMetadata(mediaKey, broadcastId, title, state, thumbnail);
    }

    @Nullable
    private static String matchGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String val = matcher.group(1);
            return (val != null && !val.isEmpty()) ? val : matcher.group(2);
        }
        return null;
    }

    /**
     * Queries X's live stream endpoint and returns a DownloadItem with resolution options.
     */
    @Nullable
    static InlineDownloadButton.DownloadItem resolve(BroadcastMetadata broadcast) {
        try {
            String statusUrl = "https://api.twitter.com/1.1/live_video_stream/status/"
                    + broadcast.mediaKey + ".json?latest_replay_playlist=true&stream_type=hls";

            String jsonText = fetchHttp(statusUrl, "Bearer " + TWITTER_BEARER_TOKEN);
            if (jsonText == null) return null;

            JSONObject root = new JSONObject(jsonText);
            JSONObject source = root.optJSONObject("source");
            if (source == null) return null;

            String playlistUrl = source.optString("noRedirectPlaybackUrl", null);
            if (playlistUrl == null || playlistUrl.isEmpty()) {
                playlistUrl = source.optString("location", null);
            }
            if (playlistUrl == null || playlistUrl.isEmpty()) {
                NewXLogger.printInfo(() -> "No playback URL in live stream status response");
                return null;
            }

            return parseMasterPlaylist(playlistUrl, broadcast);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to resolve broadcast replay stream", exception);
            return null;
        }
    }

    private static InlineDownloadButton.DownloadItem parseMasterPlaylist(String masterUrl, BroadcastMetadata broadcast) {
        String label = (broadcast.title != null && !broadcast.title.isEmpty()) ? broadcast.title : "Live Replay";
        List<InlineDownloadButton.DownloadItem> options = new ArrayList<>();

        try {
            String playlistContent = fetchHttp(masterUrl, null);
            if (playlistContent != null && playlistContent.contains("#EXT-X-STREAM-INF")) {
                String[] lines = playlistContent.split("\n");
                String currentResolution = null;
                int currentBitrate = 0;

                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.startsWith("#EXT-X-STREAM-INF:")) {
                        Matcher matcher = STREAM_INF_PATTERN.matcher(line);
                        if (matcher.find()) {
                            currentResolution = matcher.group(1);
                            String bwStr = matcher.group(2);
                            currentBitrate = bwStr != null ? Integer.parseInt(bwStr) : 0;
                        }
                    } else if (!line.startsWith("#") && !line.isEmpty()) {
                        String variantUrl = resolveUrl(masterUrl, line);
                        String optionLabel = currentResolution != null ? currentResolution : label;
                        String detail = formatBitRate(currentBitrate);

                        options.add(new InlineDownloadButton.DownloadItem(
                                variantUrl,
                                "mp4",
                                "video/mp4",
                                optionLabel,
                                broadcast.thumbnailUrl,
                                null,
                                currentResolution,
                                detail,
                                null,
                                Collections.emptyList()
                        ));
                        currentResolution = null;
                        currentBitrate = 0;
                    }
                }
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to parse master HLS playlist variants", exception);
        }

        if (options.isEmpty()) {
            return new InlineDownloadButton.DownloadItem(
                    masterUrl,
                    "mp4",
                    "video/mp4",
                    label,
                    broadcast.thumbnailUrl,
                    null,
                    null,
                    "Live Replay",
                    null,
                    Collections.emptyList()
            );
        }

        // Sort options descending by resolution / bitrate if available
        InlineDownloadButton.DownloadItem best = options.get(0);
        return new InlineDownloadButton.DownloadItem(
                best.url,
                "mp4",
                "video/mp4",
                label,
                broadcast.thumbnailUrl,
                null,
                best.resolution,
                null,
                null,
                options
        );
    }

    private static String resolveUrl(String baseUrl, String candidateUrl) {
        try {
            if (candidateUrl.startsWith("http://") || candidateUrl.startsWith("https://")) {
                return candidateUrl;
            }
            URI base = URI.create(baseUrl);
            return base.resolve(candidateUrl).toString();
        } catch (Exception ignored) {
            return candidateUrl;
        }
    }

    @Nullable
    static String fetchHttp(String urlString, @Nullable String authHeader) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader);
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                NewXLogger.printInfo(() -> "HTTP " + code + " for " + urlString);
                return null;
            }

            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String formatBitRate(int bitRate) {
        if (bitRate <= 0) return "";
        if (bitRate >= 1_000_000) {
            return String.format(java.util.Locale.US, "%.1f Mbps", bitRate / 1_000_000.0);
        }
        return (bitRate / 1000) + " kbps";
    }
}
