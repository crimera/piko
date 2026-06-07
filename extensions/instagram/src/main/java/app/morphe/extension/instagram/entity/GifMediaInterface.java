/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public interface GifMediaInterface {
    public String getGifCreatorName() throws Exception;

    public String getGifTag() throws Exception;

    public String getGifUrl() throws Exception;

    public String getWebpUrl() throws Exception;

    public String getGifDownloadName() throws Exception;
}
