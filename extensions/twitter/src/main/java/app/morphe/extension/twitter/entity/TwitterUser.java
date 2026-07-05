/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.entity;

import app.morphe.extension.twitter.entity.ExtMediaEntities;
import app.morphe.extension.twitter.entity.TweetInfo;
import app.morphe.extension.twitter.entity.Debug;

import java.util.*;
import app.morphe.extension.crimera.PikoUtils;

public class TwitterUser extends Debug {
    private final Object obj;

    public TwitterUser(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public <T> T fieldNullCheck(String fieldName, Class<T> type) throws Exception {
        Object value = this.getField(fieldName);

        if (value != null) {
            // If the value exists, cast it to the requested type
            return type.cast(value);
        }

        // If the value is null, determine the default based on the requested type
        if (type == Integer.class) {
            return type.cast(0);
        } else if (type == Long.class) {
            return type.cast(0L);
        }

        // Default for String and all other Object types
        return null;
    }

    public int getStatusCount() throws Exception {
        return fieldNullCheck("getStatusCount", Integer.class);
    }

    public int getMediaCount() throws Exception {
        return fieldNullCheck("getMediaCount", Integer.class);
    }

    public int getLikesCount() throws Exception {
        return fieldNullCheck("getLikesCount", Integer.class);
    }

    public Integer getArticleCount() throws Exception {
        return fieldNullCheck("getArticleCount", Integer.class);
    }

    public int getFastFollowersCount() throws Exception {
        return fieldNullCheck("getFastFollowersCount", Integer.class);
    }

    public long getLastUpdatedAt() throws Exception {
        return fieldNullCheck("getLastUpdatedAt", Long.class);
    }

    public long getId() throws Exception {
        return (long) this.getMethod("getId");
    }

}
