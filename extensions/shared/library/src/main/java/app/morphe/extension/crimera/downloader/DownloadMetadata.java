/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera.downloader;

import androidx.annotation.Nullable;

public final class DownloadMetadata {
    @Nullable public final String caption;
    @Nullable public final String postUrl;
    @Nullable public final String performer;
    @Nullable public final Long uploadTimestampMillis;

    public DownloadMetadata(
            @Nullable String caption,
            @Nullable String postUrl,
            @Nullable String performer,
            @Nullable Long uploadTimestampMillis
    ) {
        this.caption = caption;
        this.postUrl = postUrl;
        this.performer = performer;
        this.uploadTimestampMillis = uploadTimestampMillis;
    }
}
