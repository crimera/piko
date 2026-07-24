/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.content.UriPermission;
import android.os.Build;
import android.os.LocaleList;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import app.morphe.extension.crimera.SharedPref;
import app.morphe.extension.crimera.constants.ExtensionStrings;
import app.morphe.extension.crimera.PikoUtils;

public class StorageUtils {
    private static final String KEY_BASE_PATH = "custom_download_path";
    private static final String KEY_TREE_URI = "custom_download_tree_uri";

    public static void saveCustomPath(String path) {
        SharedPref.setStringPref(KEY_BASE_PATH, path);
    }

    public static String getCustomPathForDisplay() {
        String storedPath = SharedPref.getStringPref(KEY_BASE_PATH, "");
        int separatorIndex = storedPath.indexOf(':');
        if (separatorIndex < 0) {
            return storedPath;
        }

        String storageId = storedPath.substring(0, separatorIndex);
        return formatCustomPathForDisplay(
                storedPath,
                resolveStorageLabel(storageId)
        );
    }

    static String formatCustomPathForDisplay(
            String storedPath,
            String resolvedStorageLabel
    ) {
        int separatorIndex = storedPath.indexOf(':');
        if (separatorIndex < 0) {
            return storedPath;
        }

        String storageId = storedPath.substring(0, separatorIndex);
        String relativePath = storedPath.substring(separatorIndex + 1);
        String displayStorageLabel = resolvedStorageLabel;
        if (displayStorageLabel == null || displayStorageLabel.trim().isEmpty()) {
            displayStorageLabel = isPrimaryStorageId(storageId) ? "" : storageId;
        }

        if (displayStorageLabel.isEmpty()) {
            return relativePath;
        }
        return relativePath.isEmpty()
                ? displayStorageLabel
                : displayStorageLabel + "/" + relativePath;
    }

    private static boolean isPrimaryStorageId(String storageId) {
        return "primary".equals(storageId);
    }

    private static boolean storageUuidMatches(String storageId, String volumeUuid) {
        return storageId != null
                && volumeUuid != null
                && storageId.equalsIgnoreCase(volumeUuid);
    }

    private static StorageVolume findStorageVolume(
            StorageManager storageManager,
            String storageId
    ) {
        if (isPrimaryStorageId(storageId)) {
            return storageManager.getPrimaryStorageVolume();
        }

        for (StorageVolume storageVolume : storageManager.getStorageVolumes()) {
            if (storageUuidMatches(storageId, storageVolume.getUuid())) {
                return storageVolume;
            }
        }
        return null;
    }

    private static Context createSystemLocaleContext(Context context) {
        LocaleList systemLocales = Resources.getSystem()
                .getConfiguration()
                .getLocales();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                LocaleList localeManagerLocales = localeManager.getSystemLocales();
                if (!localeManagerLocales.isEmpty()) {
                    systemLocales = localeManagerLocales;
                }
            }
        }

        if (systemLocales.isEmpty()) {
            return null;
        }

        Configuration configuration = new Configuration(
                context.getResources().getConfiguration()
        );
        configuration.setLocales(systemLocales);
        return context.createConfigurationContext(configuration);
    }

    private static String resolveStorageLabel(String storageId) {
        try {
            Context context = PikoUtils.getContext();
            if (context == null) {
                return null;
            }

            StorageManager storageManager = context.getSystemService(StorageManager.class);
            if (storageManager == null) {
                return null;
            }

            StorageVolume storageVolume = findStorageVolume(storageManager, storageId);
            if (storageVolume == null) {
                return null;
            }

            Context systemLocaleContext = createSystemLocaleContext(context);
            if (systemLocaleContext == null) {
                return null;
            }

            String description = storageVolume.getDescription(systemLocaleContext);
            return description == null || description.trim().isEmpty()
                    ? null
                    : description;
        } catch (Exception ignored) {
            return null;
        }
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
