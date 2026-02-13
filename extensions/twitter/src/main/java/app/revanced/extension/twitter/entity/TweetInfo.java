package app.revanced.extension.twitter.entity;

import app.revanced.extension.twitter.entity.Debug;
import app.revanced.extension.twitter.Utils;

// Lcom/twitter/model/core/entity/d;
public class TweetInfo extends Debug {

    private final Object obj;

    public TweetInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public String getLang() throws Exception {
        // y:String
        return (String) super.getField("tweetLang");
    }

    @Override
    public String toString() {
        try {
            return "TweetInfo [getLang()=" + this.getLang() + "]";

        } catch (Exception e) {
            Utils.logger(e);
            return e.getMessage();
        }

    }

}