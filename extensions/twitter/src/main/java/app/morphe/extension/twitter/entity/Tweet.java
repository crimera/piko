package app.morphe.extension.twitter.entity;

import app.morphe.extension.twitter.entity.ExtMediaEntities;
import app.morphe.extension.twitter.entity.TweetInfo;
import app.morphe.extension.twitter.entity.Debug;

import java.util.*;
import app.morphe.extension.twitter.Utils;

// All comments based of 11.14.beta-0
// Lcom/twitter/model/core/entity/e;
public class Tweet extends Debug {
    private final Object obj;

    public Tweet(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public Long getTweetId() throws Exception {
        return (Long) super.getMethod("getId");
    }

    public String getTweetUsername() throws Exception {
        return (String) super.getMethod("userNameMethod");
    }

    public String getTweetProfileName() throws Exception {
        return (String) super.getMethod("profileNameMethod");
    }

    public Long getTweetUserId() throws Exception {

        return (Long) super.getMethod("userIdMethod");
    }

    public ArrayList<Media> getMedias() throws Exception {
        ArrayList<Media> mediaData = new ArrayList();

        // c()Lcom/twitter/model/core/entity/c0;
        Object mediaRootObject = super.getMethod("mediaMethod");
        Class<?> mediaRootObjectClass = mediaRootObject.getClass();

        // Lcom/twitter/model/core/entity/s;
        Class<?> superClass = mediaRootObjectClass.getSuperclass();
        Object superClassInstance = superClass.cast(mediaRootObject);

        // a:List
        List<?> list = (List<?>) super.getField(superClass, superClassInstance, "extMediaList");

        assert list != null;
        if (list.isEmpty()) {
            return mediaData;
        }

        for (Object item : list) {
            ExtMediaEntities mediaObj = new ExtMediaEntities(item);
            Media media = mediaObj.getMedia();
            mediaData.add(media);
        }
        return mediaData;
    }

    public TweetInfo getTweetInfo() throws Exception {
        Object data = super.getField("tweetInfo");
        return new TweetInfo(data);
    }

    public String getTweetLang() throws Exception {
        TweetInfo tweetInfo = this.getTweetInfo();
        return tweetInfo.getLang();
    }

    public String getLongText() throws Exception {
        // j()Lcom/twitter/model/notetweet/b;
        Object noteTweetObj = super.getMethod("noteTweetMethod");
        String data = noteTweetObj != null ? (String) super.getField(noteTweetObj, "longTextField") : null;
        return data;
    }

    public String getShortText() throws Exception {
        // y()Lcom/twitter/model/core/entity/c1;
        Object tweetObj = super.getMethod("tweetEntityClass");
        // getText()
        Object data = super.getMethod(tweetObj, "getText");
        return (String) data;
    }

    public String getText() throws Exception {
        String text = "";
        try {
            text = this.getLongText();
            if (text == null) {
                text = this.getShortText();
            }
            if (text.length() > 0) {
                int mediaIndex = text.indexOf("pic.x.com");
                if (mediaIndex > 0)
                    text = text.substring(0, mediaIndex);
            }
        } catch (Exception e) {
            Utils.logger(e);
            text = e.getMessage();
        }
        return text;

    }

    @Override
    public String toString() {
        try {
            return "Tweet [getTweetId()=" + this.getTweetId() + ", getTweetUsername()=" + this.getTweetUsername()
                    + ", getTweetProfileName()=" + this.getTweetProfileName() + ", getTweetUserId()=" + this.getTweetUserId()
                    + ", getMedias()=" + this.getMedias() + ", getTweetInfo()=" + this.getTweetInfo() + ", getTweetLang()="
                    + this.getTweetLang() + ", getLongText()=" + this.getLongText() + ", getShortText()=" + this.getShortText() + "]";

        } catch (Exception e) {
            Utils.logger(e);
            return e.getMessage();
        }

    }
}
