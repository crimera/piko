package app.morphe.extension.newx.settings;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Utils;

final class ServerLogExporter {
    static final String FILE_NAME = "piko-server-logs.txt";
    private static final String MIME_TYPE = "text/plain";

    private ServerLogExporter() {
    }

    static void write(Context context, List<String> entries) throws IOException {
        String content = formatContent(context, entries);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeWithMediaStore(context, content);
            return;
        }
        writeLegacy(context, content);
    }

    private static void writeWithMediaStore(Context context, String content) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri existing = findExisting(resolver, collection);
        if (existing != null) {
            try (OutputStream output = resolver.openOutputStream(existing, "wt")) {
                writeContent(output, content);
            }
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, downloadRelativePath());
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri destination = resolver.insert(collection, values);
        if (destination == null) {
            throw new IOException("Could not create MediaStore download");
        }

        try {
            try (OutputStream output = resolver.openOutputStream(destination, "w")) {
                writeContent(output, content);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(destination, completed, null, null) != 1) {
                throw new IOException("Could not finalize MediaStore download");
            }
        } catch (IOException | RuntimeException exception) {
            resolver.delete(destination, null, null);
            throw exception;
        }
    }

    private static Uri findExisting(ContentResolver resolver, Uri collection) {
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] arguments = {FILE_NAME, downloadRelativePath()};
        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.MediaColumns._ID},
                selection,
                arguments,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
        )) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
            return ContentUris.withAppendedId(collection, id);
        }
    }

    private static void writeLegacy(Context context, String content) throws IOException {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        );
        if (!downloads.isDirectory() && !downloads.mkdirs()) {
            throw new IOException("Could not create the Downloads directory");
        }

        File destination = new File(downloads, FILE_NAME);
        try (OutputStream output = new FileOutputStream(destination, false)) {
            writeContent(output, content);
        }
        MediaScannerConnection.scanFile(
                context,
                new String[]{destination.getPath()},
                new String[]{MIME_TYPE},
                null
        );
    }

    private static void writeContent(OutputStream output, String content) throws IOException {
        if (output == null) throw new IOException("Could not open server log destination");
        output.write(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String formatContent(Context context, List<String> entries) {
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
        StringBuilder content = new StringBuilder();
        content.append("Piko NewX server logs\n");
        content.append("Generated: ").append(timestamp).append("\n");
        content.append("Package: ").append(context.getPackageName()).append("\n");
        content.append("Patch version: ").append(Utils.getPatchesReleaseVersion()).append("\n");
        content.append("Entries: ").append(entries.size()).append("\n\n");
        if (entries.isEmpty()) {
            content.append("(No server errors were captured.)\n");
            return content.toString();
        }

        for (String entry : entries) {
            content.append(entry).append("\n\n");
        }
        return content.toString();
    }

    private static String downloadRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/";
    }
}
