/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import com.instagram.model.mediasize.ExtendedImageUrl;
import app.morphe.extension.crimera.downloader.MediaType;

public class ImageData implements MediaInterface {
    private final ExtendedImageUrl obj;

    public ImageData(Object obj) {
        this.obj = (ExtendedImageUrl) obj;
    }

    public Integer getHeight() throws Exception {
        return Integer.valueOf(this.obj.getHeight());
    }

    public Integer getWidth() throws Exception {
        return Integer.valueOf(this.obj.getWidth());
    }

    public String getVariantTag() {
        try{
            return this.getHeight()+"x"+this.getWidth();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getUrl() throws Exception {
        return this.obj.getUrl();
    }

    public MediaType getMediaType(){
        return MediaType.IMAGE;
    }

}