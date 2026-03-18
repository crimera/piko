/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.entity;

import app.morphe.extension.twitter.entity.Debug;
import app.morphe.extension.twitter.Utils;

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