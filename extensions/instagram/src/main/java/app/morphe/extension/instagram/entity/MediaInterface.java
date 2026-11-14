/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import app.morphe.extension.crimera.legacydownloader.MediaType;

public interface MediaInterface {
    public Integer getHeight() throws Exception;

    public Integer getWidth() throws Exception;

    /** The resolution as commonly named, 1080 for 1080p, whichever way the media is turned. */
    default int shortSide() throws Exception {
        return Math.min(getWidth(), getHeight());
    }

    public String getVariantTag();

    /** What the variants dialog shows for it: the tag, unless the variant knows better. */
    default String getLabel() {
        return getVariantTag();
    }

    public String getUrl() throws Exception;

    /** A separate audio file to merge in on download, as DASH serves it; null for a complete file. */
    default String getAudioUrl() {
        return null;
    }

    public MediaType getMediaType();
}
