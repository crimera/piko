/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import app.morphe.extension.crimera.downloader.MediaType;

public interface MediaInterface {
    public Integer getHeight() throws Exception;

    public Integer getWidth() throws Exception;

    public String getVariantTag();

    public String getUrl() throws Exception;

    public MediaType getMediaType();
}
