/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.entity;

import java.util.List;

import app.morphe.extension.twitter.entity.Video;
import app.morphe.extension.twitter.entity.Media;
import app.morphe.extension.twitter.Utils;

// Lcom/twitter/model/core/entity/b0;
public class ExtMediaEntities extends Debug{

    private final Object obj;

    public ExtMediaEntities(Object obj) {
        super(obj);
        this.obj = obj;
    }


    public String getImageUrl()
            throws Exception {
                // q:String
        return (String) super.getField("getThumbnailField");
    }

    public String getHighResImageUrl()
            throws Exception {
        return this.getImageUrl() + "?name=4096x4096&format=jpg";
    }

    public Video getHighResVideo() throws Exception {
        Object mediaVideoInfoEntityObject = super.getField("fieldname");
        if(mediaVideoInfoEntityObject==null) return null;

        Debug mediaVideoInfoEntity = new Debug(mediaVideoInfoEntityObject);

        Object videoVariantObject = mediaVideoInfoEntity.getField("c");
        if(videoVariantObject==null) return null;

        List videoVariant = (List) videoVariantObject;
        int maxBitrate = 0;
        Video maxBitrateVideo = null;
        for(Object videoObject : videoVariant){
            Video video = new Video(videoObject);
            int bitrate = video.getBitrate();
            if(bitrate > maxBitrate){
                maxBitrateVideo = video;
            }
        }

        return maxBitrateVideo!=null ?maxBitrateVideo:null;
    }

    public Media getMedia() throws Exception {
        int type = 0;
        String url = "";
        String ext = "jpg";

        Video video = this.getHighResVideo();

        if(video!=null){
            type = 1;
            url = video.getMediaUrl();
            ext = video.getExtension();
        }else{
            url = this.getHighResImageUrl();
        }
        return new Media(type,url,ext);
    }

    @Override
    public String toString(){
        try{
        return "ExtMediaEntities [getImageUrl()=" + this.getImageUrl() + ", getHighResImageUrl()="
                + this.getHighResImageUrl() + ", getHighResVideo()=" + this.getHighResVideo() + "]";
        }catch(Exception e){
            Utils.logger(e);
            return e.getMessage();
        }
    }

}