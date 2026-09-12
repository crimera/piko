package app.morphe.extension.newx.misc;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;

import app.morphe.extension.newx.settings.NewXLogger;

/** Best-effort single-file refresh after an owned MediaStore item has been published. */
final class PublishedMediaRefresh {
    private PublishedMediaRefresh() {}

    @SuppressWarnings("deprecation") // Read DATA of our own item; do not construct or write a path.
    static void request(Context context, Uri publishedUri, String mimeType) {
        if (context == null || publishedUri == null) return;
        try {
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            ContentResolver resolver = safeContext.getContentResolver();
            String path;
            try (Cursor cursor = resolver.query(publishedUri,
                    new String[]{MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.IS_PENDING},
                    null, null, null)) {
                if (cursor == null || !cursor.moveToFirst() || cursor.getInt(1) != 0) return;
                path = cursor.getString(0);
            }
            // All entry points call this only after closing the stream and publishing the item.
            // Notify the exact URI even if the provider hides DATA from the application.
            notifyItem(safeContext, publishedUri);
            if (path == null || path.isEmpty()) {
                record("media_refresh path_unavailable");
                return;
            }
            File media = new File(path);
            if (!media.isAbsolute() || !media.isFile() || media.length() == 0) {
                record("media_refresh file_unavailable");
                return;
            }
            // The provider-allocated path includes the owning user and any collision suffix.
            // Never translate /999 to /0 or scan the parent directory.
            record("media_scan requested");
            MediaScannerConnection.scanFile(safeContext, new String[]{path}, new String[]{mimeType},
                    (scannedPath, scannedUri) -> {
                        notifyItem(safeContext, publishedUri);
                        if (scannedUri != null && !scannedUri.equals(publishedUri)) {
                            notifyItem(safeContext, scannedUri);
                        }
                        record(
                                scannedUri != null ? "media_scan completed" : "media_scan no_result");
                    });
        } catch (RuntimeException exception) {
            // Scanning/observer errors must never escape into the transfer's rollback catch.
            record("media_refresh error=" + exception.getClass().getSimpleName());
        }
    }

    private static void record(String message) {
        try { NewXLogger.printInfo(() -> message); }
        catch (RuntimeException ignored) {
            // Diagnostic configuration must not affect a completed download.
        }
    }

    private static void notifyItem(Context context, Uri uri) {
        try {
            context.getContentResolver().notifyChange(uri, null, 0);
        } catch (RuntimeException exception) {
            record("media_notify error=" + exception.getClass().getSimpleName());
        }
    }
}
