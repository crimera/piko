package app.morphe.extension.newx.misc;

import android.content.Context;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/**
 * Routes NewX's own download buttons into the SAF download pipeline.
 *
 * <p>The stock buttons resolve a media URL and MIME type, then hand them to DownloadManager
 * (or to the in-app watermarked-video writer) after discarding the post context. The patch
 * calls this router while that context is still live. When the user has picked a folder for
 * the media type, the router reserves the document, starts the transfer under the configured
 * filename template, and reports the download as handled. When no folder is configured, the
 * feature is disabled, or the media cannot be routed, it returns false and the stock flow
 * runs unchanged.
 */
public final class NativeDownloadRouter {
    static final String REDIRECT_SETTING = DownloadSettings.REDIRECT_NATIVE_DOWNLOADS;

    // Transfers only; routing and destination checks stay on the calling thread so the
    // native dispatch can be skipped synchronously when the download is handled.
    private static final int TRANSFER_THREADS = 4;
    private static final ExecutorService DOWNLOAD_EXECUTOR =
            Executors.newFixedThreadPool(TRANSFER_THREADS);

    private NativeDownloadRouter() {
    }

    /**
     * Attempts to take over a native download.
     *
     * @param post the NewX post model the media belongs to, or null when the caller has none
     * @param url the resolved media URL
     * @param mimeType the content type the app resolved for the URL
     * @return true when the SAF pipeline accepted the download; false to keep the native flow
     */
    public static boolean route(Object post, String url, String mimeType) {
        try {
            return routeOrFallback(post, url, mimeType);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to route the NewX native download", exception);
            return false;
        }
    }

    private static boolean routeOrFallback(
            Object post,
            String url,
            String mimeType
    ) {
        if (!DownloadSettings.redirectNativeDownloads()) return false;
        if (!NewXUtils.isHttpUrl(url)) return false;

        Context context = Utils.getContext();
        if (context == null) return false;

        final DownloadDestination.MediaKind kind;
        try {
            kind = DownloadDestination.mediaKindFor(mimeType);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        // A folder the user has not chosen, or one whose grant is gone, is not routed: the
        // native flow stays the fallback and the framework handles its own error reporting.
        if (!DownloadDestination.isConfigured(context, kind)) return false;

        String postText = post == null ? null : post.toString();
        String username = NewXUtils.sourceUsername(postText);
        String template = DownloadSettings.filenameTemplate();
        DownloadFileName.PostContext postContext = DownloadFileName.PostContext.fromText(postText);
        int index = 0;
        int mediaCount = 1;
        try {
            int[] position = InlineDownloadButton.mediaPosition(post, url);
            if (position != null) {
                index = position[0];
                mediaCount = position[1];
            }
        } catch (RuntimeException exception) {
            NewXLogger.printException(
                    () -> "Failed to resolve the NewX native download media position", exception);
        }
        String fileName = DownloadFileName.render(
                template,
                postContext,
                index,
                mediaCount,
                extensionFor(mimeType, url)
        );

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        DOWNLOAD_EXECUTOR.execute(() ->
                transfer(safeContext, kind, fileName, url, mimeType, username));
        return true;
    }

    private static void transfer(
            Context context,
            DownloadDestination.MediaKind kind,
            String fileName,
            String url,
            String mimeType,
            String username
    ) {
        final DownloadDestination.ConflictPolicy policy;
        try {
            policy = DownloadDestination.conflictPolicy();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Unsupported NewX download conflict policy", exception);
            postStatus("Could not start download", username);
            return;
        }

        final DownloadDestination.Target target;
        try {
            target = DownloadDestination.reserve(context, kind, fileName, mimeType, policy);
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to create the NewX download file", exception);
            // reserve() clears refused folders, so the next tap falls back to the native flow.
            postStatus(DownloadDestination.isDestinationLoss(exception)
                    ? InlineDownloadButton.FOLDER_LOST_MESSAGE
                    : "Could not start download", username);
            return;
        }
        // The conflict policy is SKIP and the name is already taken; nothing to download.
        if (target == null) {
            postStatus("Already downloaded", username);
            return;
        }

        // The stock download toast is skipped when the router takes over, so report the
        // download start the same way the inline download button does.
        postStatus("Download started", username);
        int notificationId =
                DownloadDestination.beginDownloadNotification(context, target.fileName());
        DownloadDestination.SaveState state;
        try {
            state = DownloadDestination.save(context, target, url, notificationId, username);
        } catch (RuntimeException exception) {
            // save() handles its own failures; this covers throws before it could clean up.
            NewXLogger.printException(() -> "Failed to download " + target.fileName(), exception);
            boolean lost = DownloadDestination.isDestinationLoss(exception);
            if (lost) DownloadDestination.invalidate(target.kind());
            DownloadDestination.discard(context, target);
            state = lost
                    ? DownloadDestination.SaveState.DESTINATION_LOST
                    : DownloadDestination.SaveState.FAILED;
        }

        switch (state) {
            case SAVED -> {
                // Success shows via the OS notification; without it, say so in-app.
                if (!DownloadDestination.notificationsEnabled(context)) {
                    postStatus("Saved " + target.fileName(), username);
                }
            }
            case DESTINATION_LOST ->
                    postStatus(InlineDownloadButton.FOLDER_LOST_MESSAGE, username);
            case FAILED -> postStatus("Could not save " + target.fileName(), username);
            case CANCELLED -> postStatus("Download cancelled", username);
            case NO_CONNECTION ->
                    postStatus("No connection — tap Retry when online", username);
        }
    }

    private static void postStatus(String message, String username) {
        // The in-app host only: a status Toast on top of the app's own banner is redundant.
        NewXUtils.runOnUiThread(() -> NewXInAppNotification.tryShowForUser(message, username));
    }

    /**
     * Filename extension for the download. The URL is authoritative (twimg serves the same
     * path with several formats); the MIME type covers URLs without a usable suffix.
     */
    static String extensionFor(String mimeType, String url) {
        String fromUrl = extensionFromUrl(url);
        if (fromUrl != null) return fromUrl;
        if (mimeType == null) return "bin";

        switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "image/bmp":
                return "bmp";
            case "video/mp4":
                return "mp4";
            case "video/webm":
                return "webm";
            case "video/quicktime":
                return "mov";
            default:
                break;
        }
        int slash = mimeType.indexOf('/');
        String subtype = slash >= 0 ? mimeType.substring(slash + 1) : mimeType;
        return DownloadFileName.sanitizeSegment(subtype, "bin");
    }

    private static String extensionFromUrl(String url) {
        if (url == null) return null;

        int end = url.length();
        int query = url.indexOf('?');
        if (query >= 0) end = query;
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < end) end = fragment;
        int dot = url.lastIndexOf('.', end - 1);
        if (dot < 0 || dot >= end - 1) return null;

        String candidate = url.substring(dot + 1, end);
        if (candidate.length() > 5) return null;
        for (int index = 0; index < candidate.length(); index++) {
            if (!Character.isLetterOrDigit(candidate.charAt(index))) return null;
        }
        return candidate.toLowerCase(Locale.ROOT);
    }
}
