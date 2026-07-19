/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.entity;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import app.morphe.extension.twitter.entity.Video;
import app.morphe.extension.twitter.entity.Media;
import app.morphe.extension.crimera.PikoUtils;

// Lcom/twitter/model/core/entity/b0;
public class ExtMediaEntities extends Debug{

    private final Object obj;

    public ExtMediaEntities(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public String getHighestResolution() {
        try {
            Debug fieldEntity = new Debug(super.getField("resolutionFieldName"));
            int width = (int) fieldEntity.getField("a");
            int height = (int) fieldEntity.getField("b");

            return width + "x" + height;
        } catch (Exception e) {
            PikoUtils.logger(e);
            return "";
        }
    }


    public String getImageUrl()
            throws Exception {
                // q:String
        return (String) super.getField("getThumbnailField");
    }

    public String getHighResImageUrl()
            throws Exception {
        return this.getImageUrl() + "?name=orig&format=jpg";
    }

    public ArrayList<Media> getVideos() throws Exception {
        ArrayList<Media> videoArrayList = new ArrayList<>();

        Object mediaVideoInfoEntityObject = super.getField("fieldname");
        if(mediaVideoInfoEntityObject==null) return videoArrayList;

        Debug mediaVideoInfoEntity = new Debug(mediaVideoInfoEntityObject);

        Object videoVariantObject = mediaVideoInfoEntity.getField("c");
        if(videoVariantObject==null) return videoArrayList;

        int type = 1;
        List videoVariant = (List) videoVariantObject;
        videoVariant.forEach(item->{
            try {
                Video videoData = new Video(item);
                String ext = videoData.getExtension();
                if(!ext.equals("m3u8")) {
                    String url = videoData.getMediaUrl();
                    String res = videoData.getResolution();
                    res = res != null ? res : this.getHighestResolution();

                    videoArrayList.add(new Media(type, url, ext, res));
                }
            } catch (Exception e) {
                PikoUtils.logger(e);
            }
        });
        // We want highest resolution first.
        Collections.reverse(videoArrayList);
        return videoArrayList;
    }

    public ArrayList<Media> getMediaList() {
        try {
            ArrayList<Media> videoList = this.getVideos();
            if (!videoList.isEmpty()) {
                return videoList;
            } else {
                ArrayList<Media> imageList = new ArrayList<>();

                int type = 0;
                String url = this.getHighResImageUrl();
                String ext = "jpg";
                String res = this.getHighestResolution();
                imageList.add(new Media(type, url, ext, res));

                return imageList;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            return new ArrayList<>();
        }
    }

    @Override
    public String toString(){
        try{
        return "ExtMediaEntities [getImageUrl()=" + this.getImageUrl() + ", getHighResImageUrl()="
                + this.getHighResImageUrl() + ", getMediaList()=" + this.getMediaList() + "]";
        }catch(Exception e){
            PikoUtils.logger(e);
            return e.getMessage();
        }
    }

}