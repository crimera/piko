/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.entity;

import java.util.List;
import java.util.Arrays;
import java.util.HashSet;

import android.content.Context;

import app.morphe.extension.shared.Utils;


public class MediaData extends Entity {
    private final Object obj;

    public MediaData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Class<?> getHelperClass() throws Exception {
        return Class.forName("className");
    }

    private Object getExtendedData() throws Exception {
        return super.getField("fieldName");
    }

    public String getMediaPkId() throws Exception {
        return (String) super.getMethod("methodName");
    }

    // Sometimes I want to forcefully generate file name as an image while saving the video/media as an image file.
    public String getDownloadFilename(boolean forceAsImage) throws Exception {
        String extension = this.isVideo() ? ".mp4" : ".jpg";
        extension = forceAsImage ? ".jpg" : extension;
        String mediaPkId = this.getMediaPkId();
        return mediaPkId + extension;

    }

    public UserData getUserData() throws Exception {
        Object userData = super.getMethod(this.getExtendedData(), "methodName");
        return new UserData(userData);
    }

    public HashSet getMentionSet() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "methodName", this.obj);
        if (result != null) {
            return new HashSet<>((List) result);
        }
        return null;
    }

    public List<Object> getMediaList() throws Exception {
        List mediaList = (List) super.getMethod(this.getExtendedData(), "methodName");
        if (mediaList != null) {
            return mediaList;
        }
        return Arrays.asList(this.obj);
    }

    public int getCarouselSize() throws Exception {
        return this.getMediaList().size();
    }

    public MediaData getMediaAt(int position) throws Exception {
        List<Object> mediaList = this.getMediaList();
        if (mediaList.isEmpty()) return new MediaData(this.obj);

        int safePosition = Math.max(0, Math.min(position, mediaList.size() - 1));
        return new MediaData(mediaList.get(safePosition));
    }

    public String getPhotoLink() throws Exception {
        Context context = Utils.getContext();

        Class<?> helperClass = this.getHelperClass();
        Object photoLink = super.getMethod(helperClass, "methodName", new Class[]{Context.class, this.obj.getClass()}, context, this.obj);
        return photoLink != null ? (String) photoLink : null;
    }

    public String getVideoLink() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "methodName", this.obj);
        return result != null ? (String) result : null;
    }

    public boolean isVideo() throws Exception {
        return (boolean) super.getMethod(this.obj, "methodName");
    }

    public String getMediaLink() throws Exception {
        return this.isVideo() ? this.getVideoLink() : this.getPhotoLink();
    }
}
