/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.legacydownloader;

/** One file to download; the optional fields are set after construction. */
public class DownloadRequest {
    public String url;
    public String subFolder;
    public String fileName;
    /** Embedded into the MP4 on the way; null leaves the file as served. */
    public DownloadMetadata metadata;
    /** A separate audio file to merge into the video at {@link #url}, as DASH serves it; null for a complete file. */
    public String audioUrl;
    /** A complete file to download if the primary download or audio merge fails; null fails the download. */
    public String fallbackUrl;
    /** Shown once the file is saved, instead of the usual completion toast; null for that one. */
    public String completedToast;

    public DownloadRequest(String url, String subFolder, String fileName) {
        this.url = url;
        this.subFolder = subFolder;
        this.fileName = fileName;
    }
}
