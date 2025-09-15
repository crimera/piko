package app.revanced.extension.twitter.model;
import app.revanced.extension.twitter.Utils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import app.revanced.extension.twitter.model.Video;
import app.revanced.extension.twitter.model.Media;
import app.revanced.extension.twitter.Utils;

// Lcom/twitter/model/core/entity/b0;
public class ExtMediaEntities extends Debug{

    private Object obj;

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
        // d() Lcom/twitter/model/core/entity/b0;
        Object data = super.getMethod("highResVideoMethod");
        return data!=null ?new Video(data):null;
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