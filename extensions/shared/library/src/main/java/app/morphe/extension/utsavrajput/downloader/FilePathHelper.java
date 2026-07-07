/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import java.io.File;

public class FilePathHelper {

    public static String getPathFromUri(Context context, Uri uri) {
        if (DocumentsContract.isTreeUri(uri)) {
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            String[] split = documentId.split(":");
            String type = split[0];

            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else {
                File[] externalFilesDirs = context.getExternalFilesDirs(null);
                for (File file : externalFilesDirs) {
                    if (file != null) {
                        String path = file.getAbsolutePath();
                        if (path.contains(type)) {
                            return path.split("/Android")[0] + "/" + split[1];
                        }
                    }
                }
            }
        }
        return null;
    }
}