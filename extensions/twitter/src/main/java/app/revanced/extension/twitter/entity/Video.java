package app.revanced.extension.twitter.entity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import app.revanced.extension.twitter.entity.Debug;
import app.revanced.extension.twitter.Utils;

// Lcom/twitter/media/av/model/b0;
public class Video extends Debug {
    private Object obj;

    public Video(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public Integer getBitrate()
            throws Exception {
        return (Integer) super.getField("a");
    }

    public String getMediaUrl()
            throws Exception {
        return (String) super.getField("b");
    }

    public String getCodec()
            throws Exception {
        return (String) super.getField("c");
    }

    public String getThumbnail()
            throws Exception {
        return (String) super.getField("d");
    }

    public String getExtension()
            throws Exception {
        String codec = this.getCodec();
        if (codec.equals("video/mp4")) {
            return "mp4";
        }
        if (codec.equals("video/webm")) {
            return "webm";
        }
        if (codec.equals("application/x-mpegURL")) {
            return "m3u8";
        }
        return "unknown";
    }

    @Override
    public String toString() {
        try {
            return "Video [getBitrate()=" + this.getBitrate() + ", getMediaUrl()=" + this.getMediaUrl() + ", getCodec()="
                    + this.getCodec()
                    + ", getThumbnail()=" + this.getThumbnail() + ", getExtension()=" + this.getExtension() + "]";

        } catch (Exception e) {
            Utils.logger(e);
            return e.getMessage();
        }

    }

}
