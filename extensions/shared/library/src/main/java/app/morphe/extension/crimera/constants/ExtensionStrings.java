/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.crimera.constants;


public class ExtensionStrings {
    public static final String PIKO_SETTINGS = "piko_settings";

    public static String DEFAULT_PIKO_FOLDER = "Piko";

    public static String DOWNLOAD_MEDIA_EXISTS = "Media exists";
    public static String DOWNLOAD_ONGOING = "Downloading: ";
    public static String DOWNLOAD_COMPLETED = "Downloaded: ";
    public static String DOWNLOAD_ERROR = "Download Error: ";

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
}
