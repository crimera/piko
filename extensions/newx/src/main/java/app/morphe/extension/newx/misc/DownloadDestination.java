package app.morphe.extension.newx.misc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_NAME_ATTEMPTS = 32;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static final AtomicInteger NEXT_NOTIFICATION_ID = new AtomicInteger(1);

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

        Target(Uri documentUri, String fileName, MediaKind kind) {
            this.documentUri = documentUri;
            this.fileName = fileName;
            this.kind = kind;
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

    /**
     * True when this media type has a folder whose read/write grant is still persisted. A restored
     * backup carries the URI string but not the grant, so this must be re-checked before writing.
     */
    public static boolean isConfigured(Context context, MediaKind kind) {
        Uri tree = treeUri(kind);
        if (tree == null || context == null) return false;
        return hasPersistedWritePermission(context.getContentResolver(), tree);
    }

    public static boolean hasPersistedWritePermission(ContentResolver resolver, Uri tree) {
        String treeDocumentId = treeDocumentIdOf(tree);
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (!permission.isWritePermission()) continue;

            Uri granted = permission.getUri();
            if (granted.equals(tree)) return true;

            String grantedDocumentId = treeDocumentIdOf(granted);
            if (treeDocumentId != null && treeDocumentId.equals(grantedDocumentId)) return true;
        }
        return false;
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

    /**
     * Creates the destination document, applying the conflict policy before any network work so a
     * skipped download costs nothing.
     *
     * <p>Collision detection deliberately avoids listing the whole directory. A chosen folder can
     * hold thousands of files, and an unindexed {@code /children} query costs a full provider
     * round-trip of every row. Existence is instead probed with a single-document query built from
     * the folder's document id, which local providers answer in constant time. Only when that
     * lookup misses and the provider nevertheless renames on create (the collision signal for
     * opaque document ids) does the code pay for one listing.
     *
     * @return the reserved document, or {@code null} when the conflict policy is
     *         {@link ConflictPolicy#SKIP} and the name is already taken.
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
        Uri directory = directoryUri(kind);
        String requested = fileName;

        String candidate = requested;
        int suffix = 0;
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            Uri existing = findDocumentByPath(context, directory, candidate);
            if (existing != null) {
                if (policy == ConflictPolicy.SKIP) return null;
                if (policy == ConflictPolicy.RENAME) {
                    candidate = appendSuffix(requested, ++suffix);
                    continue;
                }
                // OVERWRITE reuses the occupant document in place, so the tap path performs no
                // delete or create; save() opens the existing document for writing at transfer time.
                return new Target(existing, candidate, kind);
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
                return new Target(created, actualName, kind);
            }

            // The provider renamed the document on create, so the name was occupied even though
            // the probe missed, which means it uses opaque document ids.
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

        throw new IOException("Could not find an unused name for " + requested);
    }

    /**
     * The persisted conflict policy. Resolved once per download action, and unlike a display
     * read it fails closed: a value the settings screen cannot produce (hand-edited or foreign
     * backup) must not silently pick a policy that overwrites the user's files.
     */
    public static ConflictPolicy conflictPolicy() {
        String value = DownloadSettings.conflictPolicy();
        if (DownloadSettings.CONFLICT_OVERWRITE.equals(value)) return ConflictPolicy.OVERWRITE;
        if (DownloadSettings.CONFLICT_RENAME.equals(value)) return ConflictPolicy.RENAME;
        if (DownloadSettings.CONFLICT_SKIP.equals(value)) return ConflictPolicy.SKIP;
        throw new IllegalStateException("Unknown download conflict policy: " + value);
    }

    /**
     * Reserves a progress notification for a queued transfer. The caller posts it at enqueue
     * time, before the transfer is scheduled, so the notification no longer waits behind the
     * shared transfer queue and the previous download's entire byte copy.
     */
    static int beginDownloadNotification(Context context, String fileName) {
        return beginNotification(context, fileName);
    }

    /**
     * Streams a URL into a reserved document. Retries once with the larger image variant, and
     * re-uses the same notification for the retry so a failed first attempt is not silent.
     */
    public static boolean save(Context context, Target target, String url, int notificationId) {
        if (saveOnce(context, target, url, notificationId)) return true;

        String retryUrl = largerVariantUrl(url);
        if (retryUrl != null && saveOnce(context, target, retryUrl, notificationId)) return true;

        cancelNotification(context, notificationId);
        discard(context, target);
        return false;
    }

    /**
     * Writes produced content into a reserved document, used by the merged-image path so a merge
     * streams from the encoder instead of materializing a second copy in memory.
     */
    public static boolean save(Context context, Target target, ContentWriter writer) {
        // "wt" truncates explicitly: plain "w" is provider-defined truncation and has not
        // truncated on Android 10+, which would leave stale trailing bytes when overwriting
        // a longer occupant in place. Harmless for freshly created (empty) documents.
        try (OutputStream output = context.getContentResolver().openOutputStream(target.documentUri, "wt")) {
            if (output == null) throw new IOException("Could not open " + target.fileName);
            writer.write(output);
            output.flush();
            return true;
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to write " + target.fileName, exception);
            discard(context, target);
            return false;
        }
    }

    /** Writes an in-memory payload into a reserved document. */
    public static boolean save(Context context, Target target, byte[] data) {
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

    private static boolean saveOnce(Context context, Target target, String url, int notificationId) {
        HttpURLConnection connection = null;
        if (notificationId > 0) showIndeterminate(context, notificationId, target.fileName);
        try {
            connection = openConnection(url);
            int contentLength = connection.getContentLength();

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = context.getContentResolver()
                         .openOutputStream(target.documentUri, "wt")) {
                if (output == null) throw new IOException("Could not open " + target.fileName);

                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                long lastUpdate = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;

                    if (notificationId > 0 && contentLength > 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 200) {
                            updateNotification(context, notificationId, target.fileName,
                                    progressOf(total, contentLength));
                            lastUpdate = now;
                        }
                    }
                }
                output.flush();
            }

            if (notificationId > 0) completeNotification(context, notificationId, target.fileName);
            return true;
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to download " + target.fileName, exception);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static Uri directoryUri(MediaKind kind) throws IOException {
        Uri tree = treeUri(kind);
        if (tree == null) {
            throw new IOException("No download folder selected for " + kind.name().toLowerCase());
        }
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
     * O(1) lookup of a child document for providers whose document ids encode the path (the local
     * external-storage provider does), avoiding the full {@code /children} enumeration. Returns
     * null when nothing is there or the provider uses opaque ids, so callers must not treat null
     * alone as "the name is free".
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

    /**
     * Reads back the name the provider actually assigned, which may differ from the request.
     * Queries the document itself, not its parent's children, so the cost is one document lookup
     * regardless of how many files the folder holds.
     */
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

    /** The existing one-shot retry for images whose {@code name=orig} variant is unavailable. */
    @Nullable
    static String largerVariantUrl(String url) {
        if (url == null || !url.contains("name=orig")) return null;
        return url.replace("name=orig", "name=4096x4096");
    }

    private static HttpURLConnection openConnection(String address) throws IOException {
        String current = address;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = openSingleConnection(current);
            int status = connection.getResponseCode();
            if (!isRedirect(status)) {
                if (status < 200 || status >= 300) {
                    connection.disconnect();
                    throw new IOException("HTTP " + status + " for " + current);
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

    private static HttpURLConnection openSingleConnection(String address) throws IOException {
        URLConnection connection = new URL(address).openConnection();
        if (!(connection instanceof HttpURLConnection httpConnection)) {
            throw new IOException("Unsupported download URL " + address);
        }
        httpConnection.setInstanceFollowRedirects(true);
        httpConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        httpConnection.setReadTimeout(READ_TIMEOUT_MS);
        httpConnection.setRequestProperty("User-Agent", USER_AGENT);
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

    private static int progressOf(long total, int contentLength) {
        int percent = (int) (total * 100 / contentLength);
        return Math.min(percent, 99);
    }

    private static int beginNotification(Context context, String fileName) {
        int id = NEXT_NOTIFICATION_ID.getAndIncrement();
        showIndeterminate(context, id, fileName);
        return id;
    }

    private static void showIndeterminate(Context context, int id, String fileName) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(fileName)
                    .setOngoing(true)
                    .setProgress(100, 0, true);
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
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to update download notification", exception);
        }
    }

    private static void completeNotification(Context context, int id, String fileName) {
        try {
            NotificationManager manager = notificationManager(context);
            if (manager == null) return;

            Notification.Builder builder = notificationBuilder(context);
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(fileName)
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            manager.notify(id, builder.build());
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to complete download notification", exception);
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
