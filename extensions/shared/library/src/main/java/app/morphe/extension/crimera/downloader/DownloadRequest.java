/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import java.util.ArrayList;
import java.util.List;

/** One file to download; the optional fields are set after construction. */
public class DownloadRequest {
    /** One rendition, including the audio it needs; a null audio URL means a complete file. */
    public static final class Source {
        public final String url;
        public final String audioUrl;

        public Source(String url, String audioUrl) {
            this.url = url;
            this.audioUrl = audioUrl;
        }
    }

    public String url;
    public String subFolder;
    public String fileName;
    /** Embedded into the MP4 on the way; null leaves the file as served. */
    public DownloadMetadata metadata;
    /** A separate audio file to merge into the video at {@link #url}, as DASH serves it; null for a complete file. */
    public String audioUrl;
    /** Re-encodes xHE-AAC audio at {@link #audioUrl} as AAC-LC, which every player decodes; false merges it as served. */
    public boolean audioToAacLc;
    /** A complete file to try after the primary and alternate sources fail; null omits this final fallback. */
    public String fallbackUrl;
    /** Alternate renditions in retry order, before the legacy complete-file fallback. */
    public final List<Source> fallbackSources = new ArrayList<>();
    /** Shown once the file is saved, instead of the usual completion toast; null for that one. */
    public String completedToast;

    public DownloadRequest(String url, String subFolder, String fileName) {
        this.url = url;
        this.subFolder = subFolder;
        this.fileName = fileName;
    }
}
