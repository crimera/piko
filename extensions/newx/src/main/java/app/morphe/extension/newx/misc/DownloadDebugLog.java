package app.morphe.extension.newx.misc;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;

/**
 * Appends download diagnostics to {@code Download/piko-download-log.txt}.
 *
 * <p>The file is contributed through MediaStore, which needs no storage permission on API 29+,
 * and then appended to in place so a tester only has to send one file. Logging is best-effort:
 * every failure is reported to the app log and never affects the download or the reporting call.
 */
public final class DownloadDebugLog {
    private static final String FILE_NAME = "piko-download-log.txt";
    private static final String MIME_TYPE = "text/plain";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT);
    private static final DateTimeFormatter FILE_SUFFIX =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private DownloadDebugLog() {
    }

    /** Appends one entry, creating the log (with a device/app header) on first use. */
    public static void append(Context context, String text) {
        if (!NewXLogger.isLoggingEnabled()) return;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            Uri existing = findExisting(resolver, collection);
            if (existing != null) {
                if (appendTo(resolver, existing, text)) {
                    return;
                }
                // The app's own row should always be writable; a fresh timestamped file keeps
                // later entries reportable instead of failing every append from here on.
                createFile(context, resolver, collection, text, uniqueFileName());
                return;
            }

            if (!createFile(context, resolver, collection, text, FILE_NAME)) {
                createFile(context, resolver, collection, text, uniqueFileName());
            }
        } catch (Throwable throwable) {
            NewXLogger.printException(() -> "Could not write the download log", throwable);
        }
    }

    /** Current local time in the log's entry format. */
    static String timestamp() {
        return TIMESTAMP.format(ZonedDateTime.now());
    }

    private static boolean appendTo(ContentResolver resolver, Uri file, String text) {
        try (OutputStream output = resolver.openOutputStream(file, "wa")) {
            if (output == null) {
                return false;
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Could not append to the download log", exception);
            return false;
        }
    }

    @Nullable
    private static Uri findExisting(ContentResolver resolver, Uri collection) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] arguments = {FILE_NAME, Environment.DIRECTORY_DOWNLOADS + "/"};
        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.MediaColumns._ID},
                selection,
                arguments,
                MediaStore.MediaColumns._ID + " DESC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0));
            }
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Could not query the download log", exception);
        }
        return null;
    }

    private static boolean createFile(
            Context context,
            ContentResolver resolver,
            Uri collection,
            String text,
            String fileName
    ) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri file;
        try {
            file = resolver.insert(collection, values);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Could not create the download log", exception);
            return false;
        }
        if (file == null) {
            return false;
        }

        try {
            try (OutputStream output = resolver.openOutputStream(file, "w")) {
                if (output == null) {
                    throw new IOException("The provider did not open the new log file");
                }
                output.write((header(context) + text).getBytes(StandardCharsets.UTF_8));
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(file, complete, null, null);
            NewXLogger.printInfo(() -> "Download log created: " + fileName);
            return true;
        } catch (IOException | RuntimeException exception) {
            NewXLogger.printException(() -> "Could not write the download log", exception);
            try {
                resolver.delete(file, null, null);
            } catch (RuntimeException ignored) {
                // A leftover pending row is invisible to the user and gets cleaned up by MediaStore.
            }
            return false;
        }
    }

    private static String header(Context context) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("Piko download log\n")
                .append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("app: ").append(context.getPackageName()).append(' ')
                .append(appVersion(context)).append('\n');
        try {
            builder.append("patch: ").append(Utils.getPatchesReleaseVersion()).append('\n');
        } catch (Throwable ignored) {
            // The version is informational; a missing helper must not block the log.
        }
        return builder.append("log start: ").append(timestamp()).append('\n').toString();
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String uniqueFileName() {
        return "piko-download-log-" + FILE_SUFFIX.format(ZonedDateTime.now()) + ".txt";
    }
}
