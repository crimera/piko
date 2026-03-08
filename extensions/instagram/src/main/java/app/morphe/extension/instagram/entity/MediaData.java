package app.morphe.extension.instagram.entity;

import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import android.content.Context;


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

    public String getDownloadFilename() throws  Exception {
        String extension = this.isVideo() ? ".mp4" : ".jpg";
        String mediaPkId = this.getMediaPkId();
        return mediaPkId+extension;

    }

    public UserData getUserData() throws Exception {
        Object userData = super.getMethod(this.getExtendedData(),"methodName");
        return new UserData(userData);
    }

    public HashSet getMentionSet() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass,"methodName",this.obj);
        if (result!=null){
            return new HashSet<>((List)result);
        }
        return null;
    }

    public List<Object> getMediaList() throws Exception {
        List mediaList = (List) super.getMethod(this.getExtendedData(),"methodName");
        if(mediaList!=null){
            return mediaList;
        }
        return Arrays.asList(this.obj);
    }

    public int getCarouselSize()throws Exception {
        return this.getMediaList().size();
    }

    public MediaData getMediaAt(int position) throws Exception {
        List<Object> mediaList = this.getMediaList();
        if(position > mediaList.size()) return new MediaData(this.obj);
        return new MediaData(mediaList.get(position));
    }

    public String getPhotoLink() throws Exception {
        Object extendedImageUrl = super.getField("fieldName");
        if (extendedImageUrl == null) return null;
        Object photoLink = new Entity(extendedImageUrl).getMethod("getUrl");
        return photoLink!=null ? (String) photoLink:null;
    }

    public String getVideoLink() throws Exception {
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass, "methodName", this.obj);
        return result!=null ? (String) result:null;
    }

    public boolean isVideo() throws Exception {
        return (boolean)super.getMethod(this.obj, "methodName");
    }

    public String getMediaLink() throws Exception {
        return this.isVideo() ? this.getVideoLink() : this.getPhotoLink();
    }
}