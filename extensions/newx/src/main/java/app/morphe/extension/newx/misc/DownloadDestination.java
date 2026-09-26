package app.morphe.extension.newx.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.URLConnection;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Storage Access Framework writer for NewX media downloads.
 *
 * <p>This is the only writer: the user picks a folder per media type and every byte is written
 * through {@link DocumentsContract}, so there is a single collision authority and no staging copy
 * that the platform can reclaim.
 */
public final class DownloadDestination {
    private static final String NOTIFICATION_CHANNEL_ID = "piko_newx_downloads";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    // Per-read stall budget, not a total deadline. Large transfers on mobile routinely stall
    // for seconds at a time; 30s killed 20MB-class downloads on fluctuating connections.
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_REDIRECTS = 5;
    /** No HttpURLConnection constant exists for 416. */
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    // Full passes over one URL: the first streams from zero, later ones resume from the
    // bytes already on disk. Large video variants (50-300MB) otherwise die on the first
    // reset and restart from zero on manual retry.
    // Backoff between attempts; DNS/link flaps that outlast fixed 1-2s delays used to
    // fail downloads that the next minute would have completed.
    private static final long[] RETRY_DELAYS_MS = {0, 2_000, 5_000, 15_000, 30_000, 60_000};
    private static final int MAX_ATTEMPTS = RETRY_DELAYS_MS.length;
    /** Total window an invocation keeps riding out flaps before failing closed. */
    private static final long RETRY_WINDOW_MS = 10 * 60 * 1_000;
    private static final int MAX_NAME_ATTEMPTS = 32;
    private static final int MAX_CAUSE_DEPTH = 16;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final AtomicInteger NEXT_NOTIFICATION_ID = new AtomicInteger(1);
    /** Cancel flags per in-flight transfer, keyed by notification id. */
    private static final ConcurrentHashMap<Integer, AtomicBoolean> CANCEL_FLAGS =
            new ConcurrentHashMap<>();
    /** Live connections per in-flight transfer, so cancel unblocks a stalled read. */
    private static final ConcurrentHashMap<Integer, HttpURLConnection> ACTIVE_CONNECTIONS =
            new ConcurrentHashMap<>();

    /**
     * Marks an in-flight transfer cancelled. The stream loop aborts on its next chunk;
     * a stalled socket read is unblocked immediately via disconnect.
     */
    static void requestCancel(Context context, int id) {
        if (id <= 0) return;
        AtomicBoolean flag = CANCEL_FLAGS.get(id);
        if (flag != null) {
            flag.set(true);
        } else {
            // No active transfer: just dismiss whatever notice is showing.
            cancelNotification(context, id);
            return;
        }
        HttpURLConnection connection = ACTIVE_CONNECTIONS.get(id);
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static boolean isCancelled(int id) {
        AtomicBoolean flag = CANCEL_FLAGS.get(id);
        return flag != null && flag.get();
    }

    private static void clearCancelFlag(int id) {
        CANCEL_FLAGS.remove(id);
    }

    /**
     * Discards a cancelled transfer without a failure notice. Non-null when cancelled.
     */
    @Nullable
    private static SaveState cancelledResult(Context context, Target target, int notificationId) {
        if (notificationId > 0 && isCancelled(notificationId)) {
            discard(context, target);
            cancelNotification(context, notificationId);
            clearCancelFlag(notificationId);
            return SaveState.CANCELLED;
        }
        return null;
    }

    private DownloadDestination() {
    }

    public enum MediaKind {
        IMAGES,
        VIDEOS,
    }

    public enum ConflictPolicy {
        OVERWRITE,
        RENAME,
        SKIP,
    }

    /** A reserved destination document. The file exists but has no content yet. */
    public static final class Target {
        final Uri documentUri;
        final String fileName;
        final MediaKind kind;
        final String mimeType;

        Target(Uri documentUri, String fileName, MediaKind kind, String mimeType) {
            this.documentUri = documentUri;
            this.fileName = fileName;
            this.kind = kind;
            this.mimeType = mimeType;
        }

        public Uri documentUri() {
            return documentUri;
        }

        public String fileName() {
            return fileName;
        }

        public MediaKind kind() {
            return kind;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    /** Routes a MIME type to its destination. Unknown media types fail closed. */
    public static MediaKind mediaKindFor(String mimeType) {
        if (mimeType == null) {
            throw new IllegalArgumentException("Cannot route a download without a MIME type");
        }
        if (mimeType.startsWith("image/")) return MediaKind.IMAGES;
        if (mimeType.startsWith("video/")) return MediaKind.VIDEOS;
        throw new IllegalArgumentException("Unsupported download MIME type: " + mimeType);
    }

    @Nullable
    public static Uri treeUri(MediaKind kind) {
        String value = kind == MediaKind.VIDEOS
                ? DownloadSettings.videosTreeUri()
                : DownloadSettings.imagesTreeUri();
        return value.isEmpty() ? null : Uri.parse(value);
    }

    @Nullable
    public static String displayPath(MediaKind kind) {
        String value = kind == MediaKind.VIDEOS
                ? DownloadSettings.videosDisplayPath()
                : DownloadSettings.imagesDisplayPath();
        return value.isEmpty() ? null : value;
    }

    static String treeSettingId(MediaKind kind) {
        return kind == MediaKind.VIDEOS ? DownloadSettings.VIDEOS_TREE_URI : DownloadSettings.IMAGES_TREE_URI;
    }

    static String displayPathSettingId(MediaKind kind) {
        return kind == MediaKind.VIDEOS
                ? DownloadSettings.VIDEOS_DISPLAY_PATH
                : DownloadSettings.IMAGES_DISPLAY_PATH;
    }

    /** Persisted grant, live-but-unpersisted access, or unusable. Restored backups keep the
     * URI string without the grant, so stored != writable. */
    public enum DestinationState {
        /** No folder stored for this media type. */
        UNSET,
        /** Stored folder with a persisted write grant. */
        PERSISTED,
        /** Stored folder usable this process only; the provider refused persistence. */
        LIVE,
        /** Stored folder is revoked, deleted, or from another device. */
        UNUSABLE,
    }

    public static boolean isUsable(@Nullable DestinationState state) {
        return state == DestinationState.PERSISTED || state == DestinationState.LIVE;
    }

    /** True when this media type has a currently usable folder. */
    public static boolean isConfigured(Context context, MediaKind kind) {
        return isUsable(destinationState(context, kind));
    }

    public static DestinationState destinationState(Context context, MediaKind kind) {
        Uri tree = treeUri(kind);
        if (tree == null || context == null) return DestinationState.UNSET;

        ContentResolver resolver = context.getContentResolver();
        if (hasPersistedWritePermission(resolver, tree)) return DestinationState.PERSISTED;

        // Persisted grants cannot show transient access from providers that refuse persistence.
        return hasLiveTreeAccess(resolver, tree) ? DestinationState.LIVE : DestinationState.UNUSABLE;
    }

    public static boolean hasPersistedWritePermission(ContentResolver resolver, Uri tree) {
        String treeDocumentId = treeDocumentIdOf(tree);
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (!permission.isWritePermission()) continue;

            Uri granted = permission.getUri();
            if (granted.equals(tree)) return true;

            // Providers may normalize the uri, so compare document ids too. Authority must
            // match so a grant from another device with the same id cannot validate this tree.
            if (!sameAuthority(granted, tree)) continue;
            String grantedDocumentId = treeDocumentIdOf(granted);
            if (treeDocumentId != null && treeDocumentId.equals(grantedDocumentId)) return true;
        }
        return false;
    }

    private static boolean sameAuthority(Uri left, Uri right) {
        String authority = left.getAuthority();
        return authority != null && authority.equals(right.getAuthority());
    }

    /** Whether the tree answers right now. Not gated on advertised flags: some writable
     * providers omit the create flag, and honoring it would leave no pickable folder. */
    public static boolean hasLiveTreeAccess(ContentResolver resolver, Uri tree) {
        try {
            String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID};
            try (Cursor cursor = resolver.query(directoryUri(tree), projection, null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            }
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Drops an unwritable folder so the next tap re-prompts instead of failing the same way. */
    public static void invalidate(MediaKind kind) {
        try {
            DownloadSettings.setString(treeSettingId(kind), "");
            DownloadSettings.setString(displayPathSettingId(kind), "");
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to clear the unusable NewX download folder", exception);
        }
    }

    /** True when DNS/connectivity was down (bounded cause walk, like below). */
    static boolean isNoConnection(@Nullable Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof java.net.UnknownHostException) return true;
            current = current.getCause();
        }
        return false;
    }

    /** True when the destination itself is gone (revoked, deleted, unrecognized tree).
     * Network and HTTP errors must never clear the picked folder. */
    public static boolean isDestinationLoss(@Nullable Throwable failure) {
        // Bounded walk: cause chains can cycle, so never loop unbounded on a download thread.
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SecurityException
                    || current instanceof FileNotFoundException) {
                return true;
            }
            // Only tree-uri rejections count: a generic IllegalArgumentException
            // (bad filename, bad mime) must never clear the picked folder.
            if (current instanceof IllegalArgumentException
                    && isTreeUriError((IllegalArgumentException) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTreeUriError(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("uri") || lower.contains("tree") || lower.contains("document");
    }

    /** Logs destination state for diagnostics. Call before clearing so the stored value is kept. */
    public static void captureDestination(Context context, MediaKind kind, String event) {
        try {
            StringBuilder detail = new StringBuilder(kind.name())
                    .append(" event=").append(event)
                    .append(" state=").append(destinationState(context, kind).name());
            Uri tree = treeUri(kind);
            if (tree == null) {
                detail.append(" tree=unset");
            } else {
                detail.append(" authority=").append(tree.getAuthority());
                String documentId = treeDocumentIdOf(tree);
                detail.append(" treeId=").append(documentId == null ? "unknown" : documentId);
            }
            detail.append(" notifications=").append(notificationsEnabled(context) ? "enabled" : "blocked");
            NewXLogger.captureDownloadFailure(detail.toString(), null);
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect download behaviour.
        }
    }

    @Nullable
    private static String treeDocumentIdOf(Uri uri) {
        if (uri == null) return null;
        try {
            return DocumentsContract.getTreeDocumentId(uri);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Creates the destination before network work, so a skipped download costs nothing.
     * Probes path-encoded ids first, listing only when an opaque-id provider renames on create.
     *
     * @return the reserved document, or {@code null} when {@link ConflictPolicy#SKIP} collides.
     */
    @Nullable
    public static Target reserve(
            Context context,
            MediaKind kind,
            String fileName,
            String mimeType,
            ConflictPolicy policy
    ) throws IOException {
        if (policy == null) {
            throw new IOException("Unknown download conflict policy");
        }
        ContentResolver resolver = context.getContentResolver();
        final Uri directory;
        try {
            directory = directoryUri(kind);
        } catch (RuntimeException exception) {
            captureDestination(context, kind, "reserve/invalid-tree");
            invalidate(kind);
            throw new IOException("Stored download folder is not a usable tree", exception);
        }
        String requested = fileName;

        String candidate = requested;
        int suffix = 0;
        try {
            for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
                Uri existing = findDocumentByPath(context, directory, candidate);
                if (existing != null) {
                    if (policy == ConflictPolicy.SKIP) return null;
                    if (policy == ConflictPolicy.RENAME) {
                        candidate = appendSuffix(requested, ++suffix);
                        continue;
                    }
                    // OVERWRITE reuses the occupant; save() truncates it at transfer time.
                    return new Target(existing, candidate, kind, mimeType);
                }

                Uri created = DocumentsContract.createDocument(resolver, directory, mimeType, candidate);
                if (created == null) {
                    throw new IOException("Could not create download file " + candidate);
                }

                String actualName = displayNameOf(resolver, created);
                if (actualName == null) {
                    DocumentsContract.deleteDocument(resolver, created);
                    throw new IOException("Could not read the created download name for " + candidate);
                }

                if (candidate.equals(actualName)) {
                    return new Target(created, actualName, kind, mimeType);
                }

                // Provider renamed on create: name was taken but probe missed (opaque ids).
                DocumentsContract.deleteDocument(resolver, created);
                if (policy == ConflictPolicy.SKIP) return null;
                if (policy == ConflictPolicy.OVERWRITE) {
                    Uri occupant = findChildDocument(resolver, directory, candidate);
                    if (occupant != null) {
                        if (!DocumentsContract.deleteDocument(resolver, occupant)) {
                            throw new IOException("Could not replace existing file " + candidate);
                        }
                        continue;
                    }
                }

                candidate = appendSuffix(requested, ++suffix);
            }
        } catch (SecurityException | FileNotFoundException | IllegalArgumentException exception) {
            captureDestination(context, kind, "reserve/refused");
            invalidate(kind);
            NewXLogger.captureDownloadFailure(kind.name() + " event=reserve/refused file=" + requested, exception);
            throw exception;
        }

        throw new IOException("Could not find an unused name for " + requested);
    }

    /** Resolves the persisted policy, failing closed on foreign or hand-edited values. */
    public static ConflictPolicy conflictPolicy() {
        String value = DownloadSettings.conflictPolicy();
        if (DownloadSettings.CONFLICT_OVERWRITE.equals(value)) return ConflictPolicy.OVERWRITE;
        if (DownloadSettings.CONFLICT_RENAME.equals(value)) return ConflictPolicy.RENAME;
        if (DownloadSettings.CONFLICT_SKIP.equals(value)) return ConflictPolicy.SKIP;
        throw new IllegalStateException("Unknown download conflict policy: " + value);
    }

    /** Reserves a progress notification before the transfer is scheduled. */
    static int beginDownloadNotification(Context context, String fileName) {
        return beginNotification(context, fileName);
    }

    /** A dead folder is a different failure from a dead link. */
    public enum SaveState {
        SAVED,
        /** Stored folder can no longer be written; the setting was cleared. */
        DESTINATION_LOST,
        FAILED,
        /** The user cancelled the transfer; never shown as a failure. */
        CANCELLED,
        /** DNS/connectivity was down; the transfer never started. Retriable. */
        NO_CONNECTION,
    }

    /** Streams a URL into a reserved document, resuming across resets, then the larger image variant. */
    public static SaveState save(Context context, Target target, String url, int notificationId) {
        return save(context, target, url, notificationId, null);
    }

    /** Streams a URL into a reserved document, wiring the failure notice to a retry. */
    public static SaveState save(
            Context context, Target target, String url, int notificationId, String username) {
        if (notificationId > 0) {
            CANCEL_FLAGS.putIfAbsent(notificationId, new AtomicBoolean(false));
        }
        // Cancel tapped while queued: never hit the network.
        SaveState cancelled = cancelledResult(context, target, notificationId);
        if (cancelled != null) return cancelled;

        Failure failure = new Failure();
        if (saveWithAttempts(context, target, url, notificationId, failure)) {
            clearCancelFlag(notificationId);
            return SaveState.SAVED;
        }
        cancelled = cancelledResult(context, target, notificationId);
        if (cancelled != null) return cancelled;

        boolean lost = isDestinationLoss(failure.cause);

        String retryUrl = largerVariantUrl(url);
        if (retryUrl != null) {
            cancelled = cancelledResult(context, target, notificationId);
            if (cancelled != null) return cancelled;
            Failure retryFailure = new Failure();
            if (saveAttempt(context, target, retryUrl, notificationId, 0, new TransferState(), retryFailure)) {
                clearCancelFlag(notificationId);
                return SaveState.SAVED;
            }
            lost = lost || isDestinationLoss(retryFailure.cause);
            if (retryFailure.cause != null) failure.cause = retryFailure.cause;
        }

        cancelled = cancelledResult(context, target, notificationId);
        if (cancelled != null) return cancelled;

        boolean destinationLost = lost;
        captureDestination(context, target.kind, destinationLost ? "transfer/folder-lost" : "transfer/failed");
        if (destinationLost) invalidate(target.kind);
        discard(context, target);
        NewXLogger.captureDownloadFailure(
                target.kind.name() + " event=transfer file=" + target.fileName(),
                failure.cause
        );
        // Offline looks identical to a broken download unless named: DNS never resolved,
        // so the transfer never started and Retry is the whole fix.
        boolean noConnection = !destinationLost && isNoConnection(failure.cause);
        // Replace the progress notification in place; cancelling alone leaves no trace when
        // the in-app host is not showing.
        notifyFailure(context, notificationId, target.fileName(), target.kind(), target.mimeType(),
                url, username, destinationLost, noConnection);
        clearCancelFlag(notificationId);
        if (destinationLost) return SaveState.DESTINATION_LOST;
        if (noConnection) return SaveState.NO_CONNECTION;
        return SaveState.FAILED;
    }

    /** Last transfer exception. */
    private static final class Failure {
        Throwable cause;
    }

    /** Streams produced merge output without an in-memory copy. */
    public static SaveState save(Context context, Target target, ContentWriter writer) {
        // "wt" truncates explicitly; plain "w" left stale trailing bytes on overwrite.
        try (OutputStream output = context.getContentResolver().openOutputStream(target.documentUri, "wt")) {
            if (output == null) throw new IOException("Could not open " + target.fileName);
            writer.write(output);
            output.flush();
            return SaveState.SAVED;
        } catch (IOException | RuntimeException exception) {
            boolean destinationLost = isDestinationLoss(exception);
            NewXLogger.printException(() -> "Failed to write " + target.fileName, exception);
            captureDestination(context, target.kind,
                    destinationLost ? "write/folder-lost" : "write/failed");
            if (destinationLost) invalidate(target.kind);
            discard(context, target);
            return destinationLost ? SaveState.DESTINATION_LOST : SaveState.FAILED;
        }
    }

    /** Writes an in-memory payload into a reserved document. */
    public static SaveState save(Context context, Target target, byte[] data) {
        return save(context, target, output -> output.write(data));
    }

    public interface ContentWriter {
        void write(OutputStream output) throws IOException;
    }

    /** Deletes a reserved document, used when a download fails partway through. */
    public static void discard(Context context, Target target) {
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), target.documentUri);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to discard " + target.fileName, exception);
        }
    }

    /** Bytes already written, kept across attempts so a reset resumes instead of restarting. */
    private static final class TransferState {
        long expectedTotal = -1;
    }

    /** HTTP status failures, so retries distinguish dead links (4xx) from sick servers (5xx). */
    static final class HttpStatusException extends IOException {
        private final int status;

        HttpStatusException(int status, String url) {
            super("HTTP " + status + " for " + url);
            this.status = status;
        }

        int status() {
            return status;
        }
    }

    /**
     * Streams the URL with resume across transient resets. Later attempts continue from the
     * bytes already on disk via Range; servers without range support restart from zero.
     */
    private static boolean saveWithAttempts(
            Context context,
            Target target,
            String url,
            int notificationId,
            Failure failure
    ) {
        TransferState state = new TransferState();
        long deadline = SystemClock.elapsedRealtime() + RETRY_WINDOW_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            final int attemptNumber = attempt;
            if (notificationId > 0 && isCancelled(notificationId)) return false;
            if (attempt > 1 && !sleepCancellable(RETRY_DELAYS_MS[attempt - 1], notificationId)) {
                return false;
            }
            // Flap riding: wait for a validated path instead of burning an attempt
            // into a dead link. Offline-at-tap lands here too, not in instant failure.
            if (!waitForOnline(context, notificationId, target.fileName(), deadline)) {
                if (failure.cause == null) {
                    failure.cause = new UnknownHostException("No validated network");
                }
                return false;
            }
            if (notificationId > 0 && isCancelled(notificationId)) return false;
            long resumeFrom = attempt == 1 ? 0 : currentSize(context, target);
            if (state.expectedTotal > 0 && resumeFrom >= state.expectedTotal) {
                if (resumeFrom == state.expectedTotal) {
                    if (notificationId > 0) completeNotification(context, notificationId, target);
                    return true;
                }
                resumeFrom = 0;
            }
            Failure attemptFailure = new Failure();
            if (saveAttempt(context, target, url, notificationId, resumeFrom, state, attemptFailure)) {
                return true;
            }
            failure.cause = attemptFailure.cause;
            if (!isRetryable(attemptFailure.cause)) return false;
            NewXLogger.printInfo(() -> "Retrying download " + target.fileName()
                    + " (attempt " + attemptNumber + ")");
        }
        return false;
    }

    /** Chunked sleep that stays responsive to Cancel. False when cancelled. */
    private static boolean sleepCancellable(long totalMs, int notificationId) {
        long end = SystemClock.elapsedRealtime() + totalMs;
        while (true) {
            if (notificationId > 0 && isCancelled(notificationId)) return false;
            long remaining = end - SystemClock.elapsedRealtime();
            if (remaining <= 0) return true;
            try {
                Thread.sleep(Math.min(remaining, 1_000));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * Validated internet path. Fail-open (assume online) when the service is missing,
     * so the attempt itself becomes the probe.
     */
    private static boolean isOnline(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /**
     * Waits for a validated path until the deadline, so transient DNS/link flaps ride
     * out instead of failing. False on cancel or deadline expiry.
     */
    private static boolean waitForOnline(
            Context context, int notificationId, String fileName, long deadline) {
        if (isOnline(context)) return true;
        if (notificationId > 0) showWaiting(context, notificationId, fileName);
        while (SystemClock.elapsedRealtime() < deadline) {
            if (notificationId > 0 && isCancelled(notificationId)) return false;
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isOnline(context)) return true;
        }
        return isOnline(context);
    }

    /** Transient network failures resume; dead links and lost folders fail closed. */
    static boolean isRetryable(@Nullable Throwable cause) {
        if (cause == null) return false;
        if (isDestinationLoss(cause)) return false;
        if (cause instanceof HttpStatusException http) {
            int status = http.status();
            return status == HTTP_RANGE_NOT_SATISFIABLE
                    || status == 429
                    || status >= 500;
        }
        return cause instanceof IOException;
    }

    private static boolean saveAttempt(
            Context context,
            Target target,
            String url,
            int notificationId,
            long resumeFrom,
            TransferState state,
            Failure failure
    ) {
        HttpURLConnection connection = null;
        if (notificationId > 0) showIndeterminate(context, notificationId, target.fileName);
        try {
            connection = openConnection(url, resumeFrom);
            trackConnection(notificationId, connection);
            int status = connection.getResponseCode();
            boolean resumed = resumeFrom > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            long base = resumed ? resumeFrom : 0;
            long remaining = connection.getContentLengthLong();
            if (remaining >= 0) {
                state.expectedTotal = base + remaining;
            } else if (base == 0) {
                state.expectedTotal = -1;
            }

            OutputStream output;
            if (resumed) {
                try {
                    output = context.getContentResolver().openOutputStream(target.documentUri, "wa");
                } catch (IOException | RuntimeException appendFailure) {
                    // Provider cannot append: redo this attempt from zero on a fresh connection.
                    untrackConnection(notificationId, connection);
                    connection.disconnect();
                    connection = openConnection(url, 0);
                    trackConnection(notificationId, connection);
                    remaining = connection.getContentLengthLong();
                    state.expectedTotal = remaining >= 0 ? remaining : -1;
                    output = context.getContentResolver().openOutputStream(target.documentUri, "wt");
                }
            } else {
                output = context.getContentResolver().openOutputStream(target.documentUri, "wt");
            }
            if (output == null) throw new IOException("Could not open " + target.fileName);

            final HttpURLConnection finalConnection = connection;
            long total = base;
            try (InputStream input = new BufferedInputStream(finalConnection.getInputStream());
                 OutputStream body = output) {
                byte[] buffer = new byte[64 * 1024];
                long lastUpdate = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (notificationId > 0 && isCancelled(notificationId)) return false;
                    body.write(buffer, 0, read);
                    total += read;

                    if (notificationId > 0 && state.expectedTotal > 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 200) {
                            updateNotification(context, notificationId, target.fileName,
                                    progressOf(total, state.expectedTotal));
                            lastUpdate = now;
                        }
                    }
                }
                body.flush();
            }

            // A cleanly-closed short stream used to report success, leaving a truncated
            // file behind. Fail closed so the next attempt resumes instead.
            if (state.expectedTotal > 0 && total != state.expectedTotal) {
                throw new IOException("Short read for " + target.fileName()
                        + ": got " + total + " of " + state.expectedTotal + " bytes");
            }

            if (notificationId > 0) completeNotification(context, notificationId, target);
            return true;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof HttpStatusException http
                    && http.status() == HTTP_RANGE_NOT_SATISFIABLE) {
                if (state.expectedTotal > 0
                        && currentSize(context, target) >= state.expectedTotal) {
                    // Stale size finished the file between attempts.
                    if (notificationId > 0) completeNotification(context, notificationId, target);
                    return true;
                }
                // Server file shrank: drop the stale partial so the retry restarts fresh.
                truncate(context, target);
                state.expectedTotal = -1;
            }
            failure.cause = exception;
            NewXLogger.printException(() -> "Failed to download " + target.fileName, exception);
            return false;
        } finally {
            untrackConnection(notificationId, connection);
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /** Bytes already stored in the reserved document; zero when the provider stays silent. */
    private static long currentSize(Context context, Target target) {
        try (Cursor cursor = context.getContentResolver().query(
                target.documentUri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Math.max(0, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
            // Unknown size restarts from zero; never fail the transfer on a probe.
        }
        return 0;
    }

    /** Empties a reserved document so a stale partial cannot poison the next attempt. */
    private static void truncate(Context context, Target target) {
        try (OutputStream output = context.getContentResolver()
                .openOutputStream(target.documentUri, "wt")) {
            // Opening with "wt" truncates; nothing to write.
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to truncate " + target.fileName, exception);
        }
    }

    private static void trackConnection(int notificationId, HttpURLConnection connection) {
        if (notificationId > 0 && connection != null) {
            ACTIVE_CONNECTIONS.put(notificationId, connection);
        }
    }

    private static void untrackConnection(int notificationId, HttpURLConnection connection) {
        if (notificationId > 0 && connection != null) {
            ACTIVE_CONNECTIONS.remove(notificationId, connection);
        }
    }

    private static Uri directoryUri(MediaKind kind) throws IOException {
        Uri tree = treeUri(kind);
        if (tree == null) {
            throw new IOException("No download folder selected for " + kind.name().toLowerCase());
        }
        return directoryUri(tree);
    }

    /**
     * The tree's root document. Throws when the stored value is not a tree uri, as after a
     * hand-edited or foreign restore.
     */
    private static Uri directoryUri(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private static Uri childDocumentsUri(Uri parentUri) {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri,
                DocumentsContract.getDocumentId(parentUri)
        );
    }

    @Nullable
    private static Uri findChildDocument(ContentResolver resolver, Uri directory, String displayName) {
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        };
        try (Cursor cursor = resolver.query(childDocumentsUri(directory), projection, null, null, null)) {
            if (cursor == null) return null;

            while (cursor.moveToNext()) {
                if (!displayName.equals(cursor.getString(1))) continue;
                return DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(0));
            }
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to query download folder", exception);
        }
        return null;
    }

    /**
     * Child lookup for path-encoded providers, avoiding a full /children enumeration.
     * Null means "not found or opaque ids"; callers must not treat it as "name is free".
     */
    @Nullable
    private static Uri findDocumentByPath(Context context, Uri directory, String displayName) {
        final String parentId;
        try {
            parentId = DocumentsContract.getDocumentId(directory);
        } catch (RuntimeException exception) {
            return null;
        }
        if (parentId == null || parentId.isEmpty()) return null;

        final Uri child;
        try {
            child = DocumentsContract.buildDocumentUriUsingTree(directory, parentId + "/" + displayName);
        } catch (RuntimeException exception) {
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(
                child,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null,
                null,
                null
        )) {
            return cursor != null && cursor.moveToFirst() ? child : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Provider-assigned name via one document lookup. */
    @Nullable
    private static String displayNameOf(ContentResolver resolver, Uri documentUri) {
        try (Cursor cursor = resolver.query(
                documentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            return cursor.getString(0);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to read created document name", exception);
            return null;
        }
    }

    private static String appendSuffix(String fileName, int suffix) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName + "_" + suffix;
        return fileName.substring(0, dot) + "_" + suffix + fileName.substring(dot);
    }

    /** Image retry for unavailable {@code name=orig} variants. */
    @Nullable
    static String largerVariantUrl(String url) {
        if (url == null || !url.contains("name=orig")) return null;
        return url.replace("name=orig", "name=4096x4096");
    }

    private static HttpURLConnection openConnection(String address, long resumeFrom) throws IOException {
        String current = address;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = openSingleConnection(current, resumeFrom);
            int status = connection.getResponseCode();
            if (!isRedirect(status)) {
                if (status < 200 || status >= 300) {
                    connection.disconnect();
                    throw new HttpStatusException(status, current);
                }
                return connection;
            }

            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null) {
                throw new IOException("Redirect without a location for " + current);
            }
            current = new URL(new URL(current), location).toString();
        }
        throw new IOException("Too many redirects for " + address);
    }

    private static HttpURLConnection openSingleConnection(String address, long resumeFrom) throws IOException {
        URLConnection connection = new URL(address).openConnection();
        if (!(connection instanceof HttpURLConnection httpConnection)) {
            throw new IOException("Unsupported download URL " + address);
        }
        httpConnection.setInstanceFollowRedirects(true);
        httpConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        httpConnection.setReadTimeout(READ_TIMEOUT_MS);
        httpConnection.setRequestProperty("User-Agent", USER_AGENT);
        if (resumeFrom > 0) {
            httpConnection.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
        }
        httpConnection.connect();
        return httpConnection;
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private static int progressOf(long total, long contentLength) {
        int percent = (int) (total * 100 / contentLength);
        return Math.min(percent, 99);
    }

    private static int beginNotification(Context context, String fileName) {
        int id = NEXT_NOTIFICATION_ID.getAndIncrement();
        showIndeterminate(context, id, fileName);
        return id;
    }

    /** Indeterminate notice while waiting for a validated path. Keeps the Cancel action. */
    static void showWaiting(Context context, int id, String fileName) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setContentText("Waiting for connection")
                    .setOngoing(true)
                    .setProgress(100, 0, true);
            PendingIntent cancel = DownloadCancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel);
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to post download waiting notification", exception);
        }
    }

    static void showIndeterminate(Context context, int id, String fileName) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setOngoing(true)
                    .setProgress(100, 0, true);
            PendingIntent cancel = DownloadCancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel);
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to post download notification", exception);
        }
    }

    private static void updateNotification(Context context, int id, String fileName, int progress) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setOngoing(true)
                    .setProgress(100, progress, false);
            PendingIntent cancel = DownloadCancelReceiver.cancelPendingIntent(context, id);
            if (cancel != null) {
                builder.addAction(
                        android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel);
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to update download notification", exception);
        }
    }

    private static void completeNotification(Context context, int id, Target target) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(target.fileName())
                    .setContentText("Download Completed")
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            PendingIntent share = shareIntent(context, target, id);
            if (share != null) {
                builder.addAction(android.R.drawable.ic_menu_share, "Share", share);
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to complete download notification", exception);
        }
    }

    /** Shares the downloaded document; null when there is nothing shareable. */
    @Nullable
    private static PendingIntent shareIntent(Context context, Target target, int id) {
        if (target.documentUri() == null) return null;
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(target.mimeType() != null ? target.mimeType() : "*/*");
            share.putExtra(Intent.EXTRA_STREAM, target.documentUri());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(share, "Share " + target.fileName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            return PendingIntent.getActivity(context, id, chooser, flags);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to build download share intent", exception);
            return null;
        }
    }

    /**
     * Replaces the progress notification with a failure notice carrying a retry button.
     * The retry reserves a fresh destination for the same name and streams the URL again.
     */
    static void notifyFailure(
            Context context,
            int id,
            String fileName,
            MediaKind kind,
            String mimeType,
            String url,
            String username,
            boolean destinationLost,
            boolean noConnection
    ) {
        if (id <= 0 || !notificationsEnabled(context)) return;
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(fileName)
                    .setContentText(destinationLost
                            ? "Download folder is no longer available"
                            : noConnection
                                    ? "No connection — tap Retry when online"
                                    : "Download failed")
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            PendingIntent retry = DownloadRetryReceiver.retryPendingIntent(
                    context, fileName, kind, mimeType, url, username, id);
            if (retry != null) {
                builder.addAction(android.R.drawable.stat_sys_download, "Retry", retry);
            }
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to post download failure notification", exception);
        }
    }

    /** Whether app permission and the download channel let notifications through. */
    public static boolean notificationsEnabled(Context context) {
        NotificationManager manager = notificationManager(context);
        if (manager == null) return false;

        try {
            if (!manager.areNotificationsEnabled()) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Only an explicitly muted channel hides notifications; a missing one is
                // created on the next post.
                NotificationChannel channel = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
                if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static void cancelNotification(Context context, int id) {
        if (id <= 0) return;
        try {
            NotificationManager manager = notificationManager(context);
            if (manager != null) manager.cancel(id);
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private static NotificationManager notificationManager(Context context) {
        Object service = context.getSystemService(Context.NOTIFICATION_SERVICE);
        return service instanceof NotificationManager manager ? manager : null;
    }

    private static Notification.Builder notificationBuilder(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
            return new Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
        }
        return new Notification.Builder(context);
    }

    private static void createNotificationChannel(Context context) {
        NotificationManager manager = notificationManager(context);
        if (manager == null) return;
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }

    /** Persists the picker result. Values are registry settings so backups carry them. */
    public static void store(Context context, MediaKind kind, Uri treeUri, @Nullable String label) {
        DownloadSettings.setString(treeSettingId(kind), treeUri == null ? "" : treeUri.toString());
        DownloadSettings.setString(displayPathSettingId(kind), label == null ? "" : label);
    }

    /** Human-readable path for a tree URI, falling back to the raw document id. */
    public static String displayPathFor(Uri treeUri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(treeUri);
            if (documentId.startsWith("primary:")) {
                return "/" + documentId.substring("primary:".length());
            }
            int colon = documentId.indexOf(':');
            return colon > 0 ? documentId.substring(0, colon) + "/" + documentId.substring(colon + 1)
                    : documentId;
        } catch (RuntimeException exception) {
            return treeUri.toString();
        }
    }
}
