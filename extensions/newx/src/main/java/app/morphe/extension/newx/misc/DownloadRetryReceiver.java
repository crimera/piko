package app.morphe.extension.newx.misc;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;

/**
 * Retries a failed inline download from its failure notification.
 *
 * <p>The failure notification carries everything needed to reserve a fresh
 * destination and stream the URL again, so a transient network error does not
 * force the user to find the post and re-tap download.
 */
public final class DownloadRetryReceiver extends BroadcastReceiver {
    static final String ACTION_RETRY = "app.morphe.extension.newx.action.RETRY_DOWNLOAD";
    private static final String EXTRA_URL = "app.morphe.extension.newx.extra.RETRY_URL";
    private static final String EXTRA_FILE_NAME = "app.morphe.extension.newx.extra.RETRY_FILE_NAME";
    private static final String EXTRA_MIME_TYPE = "app.morphe.extension.newx.extra.RETRY_MIME_TYPE";
    private static final String EXTRA_KIND = "app.morphe.extension.newx.extra.RETRY_KIND";
    private static final String EXTRA_USERNAME = "app.morphe.extension.newx.extra.RETRY_USERNAME";
    private static final String EXTRA_NOTIFICATION_ID =
            "app.morphe.extension.newx.extra.RETRY_NOTIFICATION_ID";

    private static final ExecutorService RETRY_EXECUTOR = Executors.newSingleThreadExecutor();

    /** PendingIntent for the failure notification's retry button. Null when unretriable. */
    static PendingIntent retryPendingIntent(
            Context context,
            DownloadDestination.Target target,
            String url,
            String username,
            int notificationId
    ) {
        if (target == null) return null;
        return retryPendingIntent(context, target.fileName(), target.kind(), target.mimeType(),
                url, username, notificationId);
    }

    /** PendingIntent for the failure notification's retry button. Null when unretriable. */
    static PendingIntent retryPendingIntent(
            Context context,
            String fileName,
            DownloadDestination.MediaKind kind,
            String mimeType,
            String url,
            String username,
            int notificationId
    ) {
        if (context == null || kind == null || notificationId <= 0) return null;
        if (!NewXUtils.isHttpUrl(url)) return null;
        if (fileName == null || fileName.isEmpty()) return null;

        try {
            Intent intent = new Intent(context, DownloadRetryReceiver.class);
            intent.setAction(ACTION_RETRY);
            intent.putExtra(EXTRA_URL, url);
            intent.putExtra(EXTRA_FILE_NAME, fileName);
            if (mimeType != null) intent.putExtra(EXTRA_MIME_TYPE, mimeType);
            intent.putExtra(EXTRA_KIND, kind.name());
            if (username != null) intent.putExtra(EXTRA_USERNAME, username);
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
            return PendingIntent.getBroadcast(context, notificationId, intent, pendingIntentFlags());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to build download retry intent", exception);
            return null;
        }
    }

    private static int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ACTION_RETRY.equals(intent.getAction())) return;

        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        String url = intent.getStringExtra(EXTRA_URL);
        String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        String mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);
        String kindName = intent.getStringExtra(EXTRA_KIND);
        String username = intent.getStringExtra(EXTRA_USERNAME);
        if (notificationId <= 0 || !NewXUtils.isHttpUrl(url)
                || fileName == null || fileName.isEmpty()
                || kindName == null || kindName.isEmpty()) {
            return;
        }

        DownloadDestination.MediaKind kind;
        try {
            kind = DownloadDestination.MediaKind.valueOf(kindName);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = kind == DownloadDestination.MediaKind.VIDEOS ? "video/mp4" : "image/jpeg";
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;
        // Swap the failure notice for progress immediately so a second tap cannot queue
        // a duplicate transfer while the reserve runs on the worker thread.
        try {
            DownloadDestination.showIndeterminate(safeContext, notificationId, fileName);
        } catch (RuntimeException ignored) {
        }

        String retryUrl = url;
        String retryFileName = fileName;
        String retryMimeType = mimeType;
        String retryUsername = username;
        RETRY_EXECUTOR.execute(() ->
                doRetry(safeContext, kind, retryFileName, retryMimeType, retryUrl,
                        retryUsername, notificationId));
    }

    private static void doRetry(
            Context context,
            DownloadDestination.MediaKind kind,
            String fileName,
            String mimeType,
            String url,
            String username,
            int notificationId
    ) {
        final DownloadDestination.ConflictPolicy policy;
        try {
            policy = DownloadDestination.conflictPolicy();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Unsupported NewX download conflict policy", exception);
            DownloadDestination.cancelNotification(context, notificationId);
            NewXUtils.runOnUiThread(() ->
                    InlineDownloadButton.reportDownloadStatus("Could not start download", username));
            return;
        }

        final DownloadDestination.Target target;
        try {
            target = DownloadDestination.reserve(context, kind, fileName, mimeType, policy);
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to reserve the NewX download retry", exception);
            boolean lost = DownloadDestination.isDestinationLoss(exception);
            // reserve() clears refused folders itself; the retry notice carries a fresh
            // retry button so the user can tap again after fixing the folder.
            DownloadDestination.notifyFailure(
                    context, notificationId, fileName, kind, mimeType, url, username, lost, false);
            NewXUtils.runOnUiThread(() -> InlineDownloadButton.reportDownloadStatus(
                    lost ? InlineDownloadButton.FOLDER_LOST_MESSAGE
                            : "Could not start download",
                    username));
            return;
        }
        if (target == null) {
            DownloadDestination.cancelNotification(context, notificationId);
            NewXUtils.runOnUiThread(() ->
                    NewXInAppNotification.showForUser("Already downloaded", username));
            return;
        }

        DownloadDestination.SaveState state;
        try {
            state = DownloadDestination.save(context, target, url, notificationId, username);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to retry download " + target.fileName(), exception);
            DownloadDestination.discard(context, target);
            DownloadDestination.notifyFailure(
                    context, notificationId, target.fileName(), kind, mimeType, url, username,
                    false, DownloadDestination.isNoConnection(exception));
            state = DownloadDestination.SaveState.FAILED;
        }

        switch (state) {
            case SAVED -> {
                if (!DownloadDestination.notificationsEnabled(context)) {
                    NewXUtils.runOnUiThread(() -> InlineDownloadButton.reportDownloadStatus(
                            "Saved " + target.fileName(), username));
                }
            }
            case DESTINATION_LOST -> NewXUtils.runOnUiThread(() ->
                    InlineDownloadButton.reportDownloadStatus(
                            InlineDownloadButton.FOLDER_LOST_MESSAGE, username));
            case FAILED -> NewXUtils.runOnUiThread(() ->
                    InlineDownloadButton.reportDownloadStatus(
                            "Could not save " + target.fileName(), username));
            case CANCELLED -> NewXUtils.runOnUiThread(() ->
                    NewXInAppNotification.showForUser("Download cancelled", username));
            case NO_CONNECTION -> NewXUtils.runOnUiThread(() ->
                    InlineDownloadButton.reportDownloadStatus(
                            "No connection — tap Retry when online", username));
        }
    }
}
