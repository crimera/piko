/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import app.morphe.extension.crimera.downloader.MediaType;

public class VideoData extends Entity implements MediaInterface {
    public VideoData(Object obj) {
        super(obj);
    }

    public Integer getHeight() throws Exception {
        Integer height = (Integer) super.getMethod("methodname");
        return height != null ? height : 0;
    }

    public Integer getWidth() throws Exception {
        Integer width = (Integer) super.getMethod("methodname");
        return width != null ? width : 0;
    }

    private Integer getCodec() throws Exception {
        Integer codec = (Integer) super.getMethod("methodname");
        return codec != null ? codec : 0;
    }

    public String getVariantTag() {
        try{
            return this.getHeight()+"x"+this.getWidth()+"-"+this.getCodec();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getUrl() throws Exception {
        return (String) super.getMethod("methodname");
    }

    public MediaType getMediaType(){
        return MediaType.VIDEO;
    }

}
