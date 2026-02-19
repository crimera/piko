package app.morphe.extension.twitter.entity;

import app.morphe.extension.twitter.Utils;

public class Media {
    // 0-img, 1-video
    public final int type;
    public final String url;
    public final String ext;

    public Media(int type, String url, String ext) {
        this.type = type;
        this.url = url;
        this.ext = ext;
    }

    @Override
    public String toString() {

        try {
            return "Media [type=" + this.type + ", url=" + this.url + ", ext=" + this.ext + "]";
        } catch (Exception e) {
            Utils.logger(e);
            return e.getMessage();
        }
    }

}
