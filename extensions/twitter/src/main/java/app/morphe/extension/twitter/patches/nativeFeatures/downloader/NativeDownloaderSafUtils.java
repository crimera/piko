/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.downloader;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.settings.Settings;

public final class NativeDownloaderSafUtils {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private NativeDownloaderSafUtils() {
    }

    public static boolean isConfigured() {
        return Pref.hasNativeDownloaderSafTreeUri();
    }

    public static String getFolderSummary() {
        if (!isConfigured()) {
            return strRes("piko_pref_download_saf_not_set");
        }

        String label = Pref.getNativeDownloaderSafFolderLabel();
        if (label.isBlank()) {
            return strRes("piko_pref_download_saf_active_unknown");
        }
        return strRes("piko_pref_download_saf_active", label);
    }

    public static void persistTreeUri(Context context, Uri treeUri, int resultFlags) {
        int persistFlags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (persistFlags == 0) {
            persistFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }

        if (!isWritableDirectory(context, treeUri)) {
            throw new IllegalStateException("Selected tree is not writable");
        }

        String previousUri = Pref.getNativeDownloaderSafTreeUri();
        if (!previousUri.isBlank() && !previousUri.equals(treeUri.toString())) {
            releasePersistableUriPermission(context, previousUri);
        }

        context.getContentResolver().takePersistableUriPermission(treeUri, persistFlags);

        String label = resolveFolderLabel(context, treeUri);
        Utils.setStringPref(Settings.VID_SAF_TREE_URI.key, treeUri.toString());
        Utils.setStringPref(Settings.VID_SAF_FOLDER_LABEL.key, label);
    }

    public static void clearTreeUri(Context context) {
        String treeUri = Pref.getNativeDownloaderSafTreeUri();
        if (!treeUri.isBlank()) {
            releasePersistableUriPermission(context, treeUri);
        }

        Utils.setStringPref(Settings.VID_SAF_TREE_URI.key, "");
        Utils.setStringPref(Settings.VID_SAF_FOLDER_LABEL.key, "");
    }

    public static void downloadFile(Context context, String url, String mediaName, String ext) {
        String treeUri = Pref.getNativeDownloaderSafTreeUri();
        if (treeUri.isBlank()) {
            toast(strRes("piko_pref_download_saf_invalid_folder"));
            return;
        }

        EXECUTOR.execute(() -> downloadFileBlocking(context, treeUri, url, mediaName, ext));
    }

    private static void downloadFileBlocking(Context context, String treeUriString, String url, String mediaName, String ext) {
        Uri treeUri = Uri.parse(treeUriString);
        if (!isWritableDirectory(context, treeUri)) {
            toast(strRes("piko_pref_download_saf_invalid_folder"));
            return;
        }

        String filename = mediaName + "." + ext;
        String outputName = filename;
        HttpURLConnection connection = null;
        Uri outputUri = null;

        try {
            outputUri = createUniqueFile(context, treeUri, filename, mimeTypeForExtension(ext));

            String displayName = queryDocumentString(context, outputUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (displayName != null && !displayName.isBlank()) {
                outputName = displayName;
            }

            connection = openConnection(url);

            try (
                    InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    OutputStream outputStream = new BufferedOutputStream(
                            context.getContentResolver().openOutputStream(outputUri)
                    )
            ) {
                if (outputStream == null) {
                    throw new IOException("Output stream is null");
                }
                copy(inputStream, outputStream);
            }

            toast(strRes("exo_download_completed") + ": " + outputName);
        } catch (Exception ex) {
            if (outputUri != null) {
                deleteDocument(context, outputUri);
            }
            app.morphe.extension.crimera.Utils.logger(ex);
            toast(strRes("piko_pref_download_saf_download_failed"));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isWritableDirectory(Context context, Uri treeUri) {
        Uri documentUri = getTreeDocumentUri(treeUri);
        if (documentUri == null) {
            return false;
        }

        String[] projection = {
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
        };

        try (Cursor cursor = context.getContentResolver().query(documentUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }

            String mimeType = cursor.getString(0);
            int flags = cursor.getInt(1);
            if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                return false;
            }
            return (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0;
        } catch (Exception ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
            return false;
        }
    }

    private static Uri createUniqueFile(Context context, Uri treeUri, String filename, String mimeType) throws IOException {
        String candidate = filename;
        int duplicateIndex = 1;

        while (findChildDocumentUri(context, treeUri, candidate) != null) {
            candidate = appendDuplicateSuffix(filename, duplicateIndex);
            duplicateIndex++;
        }

        Uri treeDocumentUri = getTreeDocumentUri(treeUri);
        if (treeDocumentUri == null) {
            throw new IOException("Failed to resolve tree document URI");
        }

        Uri documentUri = DocumentsContract.createDocument(
                context.getContentResolver(),
                treeDocumentUri,
                mimeType,
                candidate
        );
        if (documentUri != null) {
            return documentUri;
        }

        throw new IOException("Failed to create destination file");
    }

    private static Uri findChildDocumentUri(Context context, Uri treeUri, String displayName) {
        String treeDocumentId = getTreeDocumentId(treeUri);
        if (treeDocumentId == null) {
            return null;
        }

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        };

        try (Cursor cursor = context.getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return null;
            }

            while (cursor.moveToNext()) {
                String childDisplayName = cursor.getString(0);
                if (!displayName.equals(childDisplayName)) {
                    continue;
                }

                String childDocumentId = cursor.getString(1);
                if (childDocumentId == null || childDocumentId.isBlank()) {
                    return null;
                }
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId);
            }
        } catch (Exception ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
        }

        return null;
    }

    private static HttpURLConnection openConnection(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            return connection;
        }

        throw new IOException("HTTP " + responseCode);
    }

    private static String appendDuplicateSuffix(String filename, int duplicateIndex) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 1) {
            return filename + " (" + duplicateIndex + ")";
        }

        String name = filename.substring(0, dotIndex);
        String extension = filename.substring(dotIndex);
        return name + " (" + duplicateIndex + ")" + extension;
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
    }

    private static String mimeTypeForExtension(String ext) {
        String normalizedExt = ext.toLowerCase(Locale.ROOT);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(normalizedExt);
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }

        switch (normalizedExt) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "m3u8":
                return "application/x-mpegURL";
            default:
                return "application/octet-stream";
        }
    }

    private static String resolveFolderLabel(Context context, Uri treeUri) {
        Uri documentUri = getTreeDocumentUri(treeUri);
        if (documentUri != null) {
            String name = queryDocumentString(context, documentUri, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }

        String documentId = getTreeDocumentId(treeUri);
        if (documentId == null || documentId.isBlank()) {
            return treeUri.toString();
        }
        if (!documentId.contains(":")) {
            return documentId;
        }

        String[] parts = documentId.split(":", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return parts[0];
        }
        return parts[1];
    }

    private static String queryDocumentString(Context context, Uri documentUri, String column) {
        try (Cursor cursor = context.getContentResolver().query(documentUri, new String[]{column}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            return cursor.getString(0);
        } catch (Exception ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
            return null;
        }
    }

    private static Uri getTreeDocumentUri(Uri treeUri) {
        String documentId = getTreeDocumentId(treeUri);
        if (documentId == null) {
            return null;
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
    }

    private static String getTreeDocumentId(Uri treeUri) {
        try {
            return DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
            return null;
        }
    }

    private static void deleteDocument(Context context, Uri documentUri) {
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(), documentUri);
        } catch (Exception ignored) {
        }
    }

    private static void releasePersistableUriPermission(Context context, String treeUriString) {
        try {
            Uri treeUri = Uri.parse(treeUriString);
            context.getContentResolver().releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }
    }

    private static String strRes(String tag) {
        return StringRef.str(tag);
    }

    private static String strRes(String tag, Object... args) {
        return StringRef.str(tag, args);
    }

    private static void toast(String msg) {
        MAIN_HANDLER.post(() -> app.morphe.extension.crimera.Utils.toast(msg));
    }
}
