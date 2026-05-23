/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import com.instagram.model.mediasize.VideoVersion;

public class VideoData extends Entity {
    private final VideoVersion obj;

    public VideoData(Object obj) {
        super(obj);
        this.obj = (VideoVersion) obj;
    }

    private Integer getHeight() throws Exception {
        return this.obj.A01;
    }

    private Integer getWidth() throws Exception {
        return this.obj.A03;
    }

    private Integer getCodec() throws Exception {
        return this.obj.A02;
    }

    public String getVideoVariantTag() {
        try{
            return this.getHeight()+"x"+this.getWidth()+"-"+this.getCodec();
        } catch (java.lang.Exception e) {
            return "unknown";
        }
    }

    public String getUrl() throws Exception {
        return this.obj.getUrl();
    }

}