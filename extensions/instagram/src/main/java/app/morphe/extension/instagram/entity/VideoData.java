/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import com.instagram.model.mediasize.VideoVersion;
import app.morphe.extension.crimera.downloader.MediaType;

public class VideoData implements MediaInterface {
    private final VideoVersion obj;

    public VideoData(Object obj) {
        this.obj = (VideoVersion) obj;
    }

    public Integer getHeight() throws Exception {
        return this.obj.A01;
    }

    public Integer getWidth() throws Exception {
        return this.obj.A03;
    }

    private Integer getCodec() throws Exception {
        return this.obj.A02;
    }

    public String getVariantTag() {
        try{
            return this.getHeight()+"x"+this.getWidth()+"-"+this.getCodec();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getUrl() throws Exception {
        return this.obj.getUrl();
    }

    public MediaType getMediaType(){
        return MediaType.VIDEO;
    }

}