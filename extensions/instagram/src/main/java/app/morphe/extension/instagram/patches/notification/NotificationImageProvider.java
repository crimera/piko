/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves cached notification images, read only. Not exported: the system UI reads an image
 * through the permission Android grants it for a notification's message data. Only files named
 * like the cache's own (a hash plus .jpg) directly in its directory are served.
 */
public final class NotificationImageProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    private File fileOf(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (uri.getPathSegments().size() != 1 || !NotificationImages.isCacheName(name)) {
            throw new FileNotFoundException(String.valueOf(uri));
        }
        File file = new File(NotificationImages.dir(getContext()), name);
        if (!file.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        return file;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new SecurityException("read only");
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        return name != null && name.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = fileOf(uri);
        } catch (FileNotFoundException ex) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
