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
import app.morphe.extension.instagram.constants.PostType;

import com.instagram.common.session.UserSession;

public class MediaData extends Entity {
    private final Object obj;
    private final UserSession userSession;

    public MediaData(Object obj) {
        super(obj);
        this.obj = obj;
        this.userSession = null;
    }

    public MediaData(Object obj, UserSession userSession) {
        super(obj);
        this.obj = obj;
        this.userSession = userSession;
    }

    private Class<?> getHelperClass() throws Exception {
        return Class.forName("className");
    }

    private Object getExtendedData() throws Exception {
        return super.getField("fieldName");
    }

    public String getShortcode() {
        long instaId = Long.valueOf(this.getPostID());

        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

        // Handle the edge case where the ID is 0
        if (instaId == 0) {
            return String.valueOf(alphabet.charAt(0));
        }

        StringBuilder shortCode = new StringBuilder();

        while (instaId > 0) {
            int remainder = (int) (instaId % 64);
            shortCode.append(alphabet.charAt(remainder));
            instaId = instaId / 64;
        }

        return shortCode.reverse().toString();
    }

    public String getPostID() {
        try {
            String postId_ts = (String) super.getMethod(this.getExtendedData(), "getId");
            return postId_ts.split("_")[0];
        } catch (Exception e) {
        }
        return "0";
    }

    public PostType getPostType() {
        try{
            String postType = this.getPostTypeKey().toLowerCase();
            //TODO: for some reason clips are not recogonised.
            // Need to fix it later.
            if(postType.equals("clips")){
                return PostType.REEL;
            }
            if(postType.equals("story")){
                return PostType.STORY;
            }
            if(postType.contains("carousel")){
                return PostType.CAROUSEL;
            }
        } catch (Exception e) {

        }
        return PostType.POST;
    }

    private String getPostTypeKey() throws Exception {
        return (String) super.getField(this.getMoreExtendedData(), "A7Q");
    }

    private List<MediaData> getCarouselMediaData() throws Exception {
        List<MediaData> carouselMediaData = new ArrayList<>();
        List<Object> mediaList = this.getMediaList();
        if (mediaList.isEmpty()){
            carouselMediaData.add(new MediaData(this.obj, this.userSession));
        } else {
            mediaList.forEach(item->{
                carouselMediaData.add(new MediaData(item, this.userSession));
            });
        }
        return carouselMediaData;
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

    public String getVariantFileName(MediaInterface mediaIntfData) throws Exception {
        String mediaPkId = this.getMediaPkId();
        String variantTag = mediaIntfData.getVariantTag();
        String extension = this.getMediaExtension(mediaIntfData.getMediaType());
        return mediaPkId + "_" +variantTag + extension;
    }

    public UserData getUserDataWithoutUserSession() throws Exception {
        Object userData = super.getMethod(this.getExtendedData(), "methodName");
        return new UserData(userData);
    }

    public UserData getUserDataWithUserSession() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "methodname", this.userSession, this.obj);
        return result != null ? new UserData(result) : null;
    }

    public UserData getUserData() throws Exception {
        UserSession userSession = this.userSession;
        if(userSession!=null){
            return this.getUserDataWithUserSession();
        }
        return this.getUserDataWithoutUserSession();
    }

    public HashSet<UserData> getMentionSet() throws Exception {
        Object result =  super.getMethod(this.getExtendedData(), "methodName");
        if (result != null) {
            List userInteractionList = (List) result;
            HashSet<UserData> userDataHashSet = new HashSet<>();
            for(Object data:userInteractionList){
                Object userData = super.getMethod(data, "methodName2");
                userDataHashSet.add(new UserData(userData));
            }
            return userDataHashSet;
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
        List<MediaData> mediaList = this.getCarouselMediaData();

        int safePosition = Math.max(0, Math.min(position, mediaList.size() - 1));
        return mediaList.get(safePosition);
    }

    private Object getMoreExtendedData() throws Exception {
        return super.getField(this.getExtendedData(), "fieldName");
    }

    private List getVideoVariantsV1() throws Exception {
        Object variantObject = super.getMethod(this.getExtendedData(), "methodName");
        if (variantObject != null){
            List variantList = (List) variantObject;

            List<VideoData> videoList = new ArrayList<>();
            variantList.forEach(item -> videoList.add(new VideoData(item)));
            return videoList;

        }
        return null;
    }

    private List getVideoVariantsV2() throws Exception {
        List variantList = (List) super.getField(this.getMoreExtendedData(), "fieldName");

        List<VideoData> videoList = new ArrayList<>();
        variantList.forEach(item -> videoList.add(new VideoData(item)));
        return videoList;
    }

    public List getVideoVariants() throws Exception {
        try {
            return this.getVideoVariantsV2();
        } catch (Exception e) {
            return this.getVideoVariantsV1();
        }
    }

    public List getImageVariants() throws Exception {
        Object imageInfoObject = (Object) super.getField(this.getMoreExtendedData(), "fieldName");
        List variantList = (List) super.getMethod(imageInfoObject, "methodName");

        List<ImageData> imageList = new ArrayList<>();
        variantList.forEach(item -> imageList.add(new ImageData(item)));
        return imageList;
    }

    public String getVideoLink() throws Exception {
        List<VideoData> videoDataList = this.getVideoVariants();
        if(videoDataList!=null){
            return videoDataList.get(0).getUrl();
        }
        return null;
    }
    
    public String getImageLink() throws Exception {
        List<ImageData> imageDataList = this.getImageVariants();
        if(imageDataList!=null){
            return imageDataList.get(0).getUrl();
        }
        return null;
    }


    public String getMediaLink() throws Exception {
        return this.isVideo() ? this.getVideoLink() : this.getImageLink();
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
