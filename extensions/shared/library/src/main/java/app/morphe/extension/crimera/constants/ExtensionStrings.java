/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.crimera.constants;


public class ExtensionStrings {
    public static final String PIKO_SETTINGS = "piko_settings";

    public static String DEFAULT_PIKO_FOLDER = "Piko";

    public static String DOWNLOAD_MEDIA_EXISTS = "Media exists";
    public static String DOWNLOAD_ONGOING = "Downloading: ";
    public static String DOWNLOAD_COMPLETED = "Downloaded: ";
    public static String DOWNLOAD_ERROR = "Download Error: ";

    public static String DOWNLOAD_SET_PATH_FAILED = "Failed to resolve folder path";
    public static String DOWNLOAD_SET_PATH_SUCCESS = "Download directory updated!";
    public static String DOWNLOAD_GRANT_PERMISSION = "Please grant storage access to continue downloads";
    public static String DOWNLOAD_GRANT_PERMISSION_FAILED = "Could not open settings. Please grant All Files Access manually";

    public static void setDefaultPikoFolder(String defaultPikoFolder) {
        DEFAULT_PIKO_FOLDER = defaultPikoFolder;
    }

    public static void setDownloadOngoing(String downloadOngoing) {
        DOWNLOAD_ONGOING = downloadOngoing;
    }

    public static void setDownloadCompleted(String downloadCompleted) {
        DOWNLOAD_COMPLETED = downloadCompleted;
    }

    public static void setDownloadError(String downloadError) {
        DOWNLOAD_ERROR = downloadError;
    }

    public static void setDownloadMediaExists(String downloadMediaExists) {
        DOWNLOAD_MEDIA_EXISTS = downloadMediaExists;
    }

    public static void setDownloadSetPathFailed(String downloadSetPathFailed) {
        DOWNLOAD_SET_PATH_FAILED = downloadSetPathFailed;
    }

    public static void setDownloadSetPathSuccess(String downloadSetPathSuccess) {
        DOWNLOAD_SET_PATH_SUCCESS = downloadSetPathSuccess;
    }

    public static void setDownloadGrantPermission(String downloadGrantPermission) {
        DOWNLOAD_GRANT_PERMISSION = downloadGrantPermission;
    }

    public static void setDownloadGrantPermissionFailed(String downloadGrantPermissionFailed) {
        DOWNLOAD_GRANT_PERMISSION_FAILED = downloadGrantPermissionFailed;
    }
}
