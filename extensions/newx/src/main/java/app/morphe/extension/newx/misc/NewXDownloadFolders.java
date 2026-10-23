package app.morphe.extension.newx.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.downloader.FolderPicker;
import app.morphe.extension.crimera.downloader.engine.DestinationCheck;
import app.morphe.extension.crimera.downloader.model.ConflictPolicy;
import app.morphe.extension.crimera.downloader.engine.DownloadNotifications;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

public final class NewXDownloadFolders {

    private NewXDownloadFolders() {
    }

    public enum MediaKind {
        IMAGES,
        VIDEOS,
    }

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
        return DestinationCheck.hasPersistedWritePermission(resolver, tree);
    }

    public static boolean sameAuthority(Uri left, Uri right) {
        return DestinationCheck.sameAuthority(left, right);
    }

    /** Whether the tree answers right now. Not gated on advertised flags: some writable
     * providers omit the create flag, and honoring it would leave no pickable folder. */
    public static boolean hasLiveTreeAccess(ContentResolver resolver, Uri tree) {
        return DestinationCheck.hasLiveTreeAccess(resolver, tree);
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
            detail.append(" notifications=").append(DownloadNotifications.notificationsEnabled(context, NewXDownloader.CHANNEL_ID) ? "enabled" : "blocked");
            NewXLogger.captureDownloadFailure(detail.toString(), null);
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect download behaviour.
        }
    }

    @Nullable
    static String treeDocumentIdOf(Uri uri) {
        if (uri == null) return null;
        try {
            return DocumentsContract.getTreeDocumentId(uri);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Resolves the persisted policy, failing closed on foreign or hand-edited values. */
    public static ConflictPolicy conflictPolicy() {
        String value = DownloadSettings.conflictPolicy();
        if (DownloadSettings.CONFLICT_OVERWRITE.equals(value)) return ConflictPolicy.OVERWRITE;
        if (DownloadSettings.CONFLICT_RENAME.equals(value)) return ConflictPolicy.RENAME;
        if (DownloadSettings.CONFLICT_SKIP.equals(value)) return ConflictPolicy.SKIP;
        throw new IllegalStateException("Unknown download conflict policy: " + value);
    }

    /** Persists the picker result. Values are registry settings so backups carry them. */
    public static void store(Context context, MediaKind kind, Uri treeUri, @Nullable String label) {
        DownloadSettings.setString(treeSettingId(kind), treeUri == null ? "" : treeUri.toString());
        DownloadSettings.setString(displayPathSettingId(kind), label == null ? "" : label);
    }

    /** Human-readable path for a tree URI, falling back to the raw document id. */
    public static String displayPathFor(Uri treeUri) {
        return DestinationCheck.displayPathFor(treeUri);
    }

    /** Launches the shared folder picker and updates the configured folder on success. */
    public static void choose(Context context, MediaKind kind) {
        try {
            FolderPicker.launch(context, new FolderPicker.Callback() {
                @Override
                public void onPicked(Uri tree, String displayPath, boolean persisted) {
                    store(context, kind, tree, displayPath);
                    captureDestination(context, kind, "folder-picked");
                    Utils.showToastShort(StringRef.str(persisted
                            ? "piko_newx_download_options_changed"
                            : "piko_newx_download_options_folder_session"));
                }

                @Override
                public void onCancelled() {
                    Utils.showToastShort(StringRef.str("piko_newx_download_options_cancelled"));
                }

                @Override
                public void onUnwritable() {
                    captureDestination(context, kind, "folder-picked/unwritable");
                    Utils.showToastShort(StringRef.str("piko_newx_download_options_folder_unwritable"));
                }
            });
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to launch folder picker", exception);
            Utils.showToastShort(StringRef.str("piko_newx_download_options_cancelled"));
        }
    }
}
