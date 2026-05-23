/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import android.content.Context;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.downloader.MediaType;


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

    public boolean isVideo() throws Exception {
        return (boolean) super.getMethod(this.obj, "methodName");
    }

    public boolean hasAudio() throws Exception {
        return this.getAudioMedia() != null;
    }

    private String getMediaExtension(MediaType mediaType) throws Exception {
        String imageExtension = ".jpg";
        String videoExtension = ".mp4";
        String audioExtension = ".mp3";

        if (mediaType.equals(MediaType.ANY)) {
            if (this.isVideo()) {
                return videoExtension;
            }
            return imageExtension;
        }

        if (mediaType.equals(MediaType.IMAGE)) return imageExtension;
        if (mediaType.equals(MediaType.VIDEO)) return videoExtension;
        if (mediaType.equals(MediaType.AUDIO)) return audioExtension;

        // Default fallback just in case.
        return imageExtension;
    }


    public String getDownloadFilename(MediaType mediaType) throws Exception {
        String mediaPkId = this.getMediaPkId();
        String extension = this.getMediaExtension(mediaType);
        return mediaPkId + extension;
    }

    public String getVideoVariantFileName(VideoData videoData) throws Exception {
        String mediaPkId = this.getMediaPkId();
        String variantTag = videoData.getVideoVariantTag();
        String extension = this.getMediaExtension(MediaType.VIDEO);
        return mediaPkId + "_" +variantTag +extension;
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

    public List getVideoVariants() throws Exception {
        Object variantObject = super.getMethod(this.getExtendedData(), "methodName");
        if (variantObject != null){
            List variantList = (List) variantObject;
            List<VideoData> videoList = new ArrayList<>();

            variantList.forEach(item -> videoList.add(new VideoData(item)));

            return videoList;

        }
        return null;
    }

    public String getVideoLink() throws Exception {
        List<VideoData> videoDataList = this.getVideoVariants();
        if(videoDataList!=null){
            return videoDataList.get(0).getUrl();
        }
        return null;
    }

    public String getMediaLink() throws Exception {
        return this.isVideo() ? this.getVideoLink() : this.getPhotoLink();
    }

    private OriginalSoundDataIntf getOriginalSoundDataIntf() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "A06", this.obj);
        if (result != null) {
            return new OriginalSoundDataIntf(result);
        }
        return null;
    }

    private TrackDataIntf getTrackDataIntf() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "A0F", this.obj);
        if (result != null) {
            return new TrackDataIntf(result);
        }
        return null;
    }

    public AudioMediaInterface getAudioMedia() throws Exception {
        AudioMediaInterface originalSoundDataIntf = this.getOriginalSoundDataIntf();
        if (originalSoundDataIntf != null) {
            return originalSoundDataIntf;
        }

        AudioMediaInterface TrackDataIntf = this.getTrackDataIntf();
        if (TrackDataIntf != null) {
            return TrackDataIntf;
        }
        return null;
    }

    public String getDescriptionText() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "A0J", this.obj);
        return result != null ? (String) super.getField(result, "A0Z") : null;
    }

    public String getMessageAudioUrl() throws Exception {
        Object audioIntfObject = super.getMethod(this.getExtendedData(), "methodName");
        return audioIntfObject != null ? (String) super.getMethod(audioIntfObject, "BAj") : null;
    }

}
