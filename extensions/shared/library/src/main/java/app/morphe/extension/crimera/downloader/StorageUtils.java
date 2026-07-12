/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.UriPermission;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;

public class StorageUtils {
    private static final String KEY_BASE_PATH = "custom_download_path";
    private static final String KEY_TREE_URI = "custom_download_tree_uri";

    public static void saveCustomPath(String path) {
        SharedPref.setStringPref(KEY_BASE_PATH, path);
    }

    public static void saveCustomTreeUri(Uri treeUri) {
        SharedPref.setStringPref(KEY_TREE_URI, treeUri.toString());
    }

    public static boolean checkStoragePermissions() {
        return getDownloadTreeUri() != null;
    }

    public static Uri getDownloadTreeUri() {
        Context context = PikoUtils.getContext();
        if (context == null) {
            return null;
        }

        String treeUriString = SharedPref.getStringPref(KEY_TREE_URI, "");
        if (treeUriString == null || treeUriString.isBlank()) {
            return null;
        }

        Uri treeUri = Uri.parse(treeUriString);
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(treeUri) && permission.isWritePermission()) {
                return treeUri;
            }
        }

        return null;
    }

    public static void allowStorageAccess() {
        try {
            Context context = PikoUtils.getContext();
            Intent intent = new Intent(context, FolderPickerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            PikoUtils.toast(ExtensionStrings.DOWNLOAD_GRANT_PERMISSION);
        } catch (Exception e) {
            PikoUtils.toast(ExtensionStrings.DOWNLOAD_GRANT_PERMISSION_FAILED);
        }
    }
}
