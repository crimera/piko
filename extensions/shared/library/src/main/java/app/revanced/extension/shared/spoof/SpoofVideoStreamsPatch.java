package app.revanced.extension.shared.spoof;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.Map;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.settings.Setting;
import app.revanced.extension.shared.spoof.requests.StreamingDataRequest;

@SuppressWarnings("unused")
public class SpoofVideoStreamsPatch {
    private static final boolean SPOOF_STREAMING_DATA = BaseSettings.SPOOF_VIDEO_STREAMS.get();

    private static final boolean FIX_HLS_CURRENT_TIME = SPOOF_STREAMING_DATA
            && BaseSettings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get() == ClientType.IOS_UNPLUGGED;

    /**
     * Any unreachable ip address.  Used to intentionally fail requests.
     */
    private static final String UNREACHABLE_HOST_URI_STRING = "https://127.0.0.0";
    private static final Uri UNREACHABLE_HOST_URI = Uri.parse(UNREACHABLE_HOST_URI_STRING);

    /**
     * @return If this patch was included during patching.
     */
    private static boolean isPatchIncluded() {
        return false; // Modified during patching.
    }

    public static boolean notSpoofingToAndroid() {
        return !isPatchIncluded()
                || !BaseSettings.SPOOF_VIDEO_STREAMS.get()
                || BaseSettings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get() == ClientType.IOS_UNPLUGGED;
    }

    /**
     * Injection point.
     * Blocks /get_watch requests by returning an unreachable URI.
     *
     * @param playerRequestUri The URI of the player request.
     * @return An unreachable URI if the request is a /get_watch request, otherwise the original URI.
     */
    public static Uri blockGetWatchRequest(Uri playerRequestUri) {
        if (SPOOF_STREAMING_DATA) {
            try {
                String path = playerRequestUri.getPath();

                if (path != null && path.contains("get_watch")) {
                    Logger.printDebug(() -> "Blocking 'get_watch' by returning unreachable uri");

                    return UNREACHABLE_HOST_URI;
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockGetWatchRequest failure", ex);
            }
        }

        return playerRequestUri;
    }

    /**
     * Injection point.
     * <p>
     * Blocks /initplayback requests.
     */
    public static String blockInitPlaybackRequest(String originalUrlString) {
        if (SPOOF_STREAMING_DATA) {
            try {
                var originalUri = Uri.parse(originalUrlString);
                String path = originalUri.getPath();

                if (path != null && path.contains("initplayback")) {
                    Logger.printDebug(() -> "Blocking 'initplayback' by clearing query");

                    return originalUri.buildUpon().clearQuery().build().toString();
                }
            } catch (Exception ex) {
                Logger.printException(() -> "blockInitPlaybackRequest failure", ex);
            }
        }

        return originalUrlString;
    }

    /**
     * Injection point.
     */
    public static boolean isSpoofingEnabled() {
        return SPOOF_STREAMING_DATA;
    }

    /**
     * Injection point.
     * Only invoked when playing a livestream on an iOS client.
     */
    public static boolean fixHLSCurrentTime(boolean original) {
        if (!SPOOF_STREAMING_DATA) {
            return original;
        }
        return false;
    }

    /**
     * Injection point.
     * Turns off a feature flag that interferes with spoofing.
     */
    public static boolean useMediaFetchHotConfigReplacement(boolean original) {
        if (original) {
            Logger.printDebug(() -> "useMediaFetchHotConfigReplacement is set on");
        }

        if (!SPOOF_STREAMING_DATA) {
            return original;
        }
        return false;
    }

    /**
     * Injection point.
     * Turns off a feature flag that interferes with video playback.
     */
    public static boolean usePlaybackStartFeatureFlag(boolean original) {
        if (original) {
            Logger.printDebug(() -> "usePlaybackStartFeatureFlag is set on");
        }

        if (!SPOOF_STREAMING_DATA) {
            return original;
        }
        return false;
    }

    /**
     * Injection point.
     */
    public static void fetchStreams(String url, Map<String, String> requestHeaders) {
        if (SPOOF_STREAMING_DATA) {
            try {
                Uri uri = Uri.parse(url);
                String path = uri.getPath();
                if (path == null || !path.contains("player")) {
                    return;
                }

                // 'get_drm_license' has no video id and appears to happen when waiting for a paid video to start.
                // 'heartbeat' has no video id and appears to be only after playback has started.
                // 'refresh' has no video id and appears to happen when waiting for a livestream to start.
                // 'ad_break' has no video id.
                if (path.contains("get_drm_license") || path.contains("heartbeat")
                        || path.contains("refresh") || path.contains("ad_break")) {
                    Logger.printDebug(() -> "Ignoring path: " + path);
                    return;
                }

                String id = uri.getQueryParameter("id");
                if (id == null) {
                    Logger.printException(() -> "Ignoring request with no id: " + url);
                    return;
                }

                StreamingDataRequest.fetchRequest(id, requestHeaders);
            } catch (Exception ex) {
                Logger.printException(() -> "buildRequest failure", ex);
            }
        }
    }

    /**
     * Injection point.
     * Fix playback by replace the streaming data.
     * Called after {@link #fetchStreams(String, Map)}.
     */
    @Nullable
    public static ByteBuffer getStreamingData(String videoId) {
        if (SPOOF_STREAMING_DATA) {
            try {
                StreamingDataRequest request = StreamingDataRequest.getRequestForVideoId(videoId);
                if (request != null) {
                    // This hook is always called off the main thread,
                    // but this can later be called for the same video id from the main thread.
                    // This is not a concern, since the fetch will always be finished
                    // and never block the main thread.
                    // But if debugging, then still verify this is the situation.
                    if (BaseSettings.DEBUG.get() && !request.fetchCompleted() && Utils.isCurrentlyOnMainThread()) {
                        Logger.printException(() -> "Error: Blocking main thread");
                    }

                    var stream = request.getStream();
                    if (stream != null) {
                        Logger.printDebug(() -> "Overriding video stream: " + videoId);
                        return stream;
                    }
                }

                Logger.printDebug(() -> "Not overriding streaming data (video stream is null): " + videoId);
            } catch (Exception ex) {
                Logger.printException(() -> "getStreamingData failure", ex);
            }
        }

        return null;
    }

    /**
     * Injection point.
     * Called after {@link #getStreamingData(String)}.
     */
    @Nullable
    public static byte[] removeVideoPlaybackPostBody(Uri uri, int method, byte[] postData) {
        if (SPOOF_STREAMING_DATA) {
            try {
                final int methodPost = 2;
                if (method == methodPost) {
                    String path = uri.getPath();
                    if (path != null && path.contains("videoplayback")) {
                        return null;
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "removeVideoPlaybackPostBody failure", ex);
            }
        }

        return postData;
    }

    /**
     * Injection point.
     */
    public static String appendSpoofedClient(String videoFormat) {
        try {
            if (SPOOF_STREAMING_DATA && BaseSettings.SPOOF_STREAMING_DATA_STATS_FOR_NERDS.get()
                    && !TextUtils.isEmpty(videoFormat)) {
                // Force LTR layout, to match the same LTR video time/length layout YouTube uses for all languages.
                return "\u202D" + videoFormat + "\u2009(" // u202D = left to right override
                        + StreamingDataRequest.getLastSpoofedClientName() + ")";
            }
        } catch (Exception ex) {
            Logger.printException(() -> "appendSpoofedClient failure", ex);
        }

        return videoFormat;
    }

    public static final class AudioStreamLanguageOverrideAvailability implements Setting.Availability {
        @Override
        public boolean isAvailable() {
            return BaseSettings.SPOOF_VIDEO_STREAMS.get()
                    && BaseSettings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get() == ClientType.ANDROID_VR_NO_AUTH;
        }
    }

    public static final class SpoofiOSAvailability implements Setting.Availability {
        @Override
        public boolean isAvailable() {
            return BaseSettings.SPOOF_VIDEO_STREAMS.get()
                    && BaseSettings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get() == ClientType.IOS_UNPLUGGED;
        }
    }
}
