/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/

package app.morphe.extension.instagram.entity;

import java.util.Map;
import java.util.HashMap;
import com.instagram.user.model.FriendshipStatus;
import app.morphe.extension.crimera.Utils;

public class UserFriendshipStatus extends Entity {
    private final Object obj;

    public UserFriendshipStatus(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Map<String, Boolean> getMappings(){
        try {
            Class<?> helperClass = Class.forName("classname");
            return (Map) super.getMethod(helperClass, "methodname", new Class[]{FriendshipStatus.class}, this.obj);
        } catch (Exception e) {
            Utils.logger(e);
        }
        return new HashMap();
    }

    private Boolean getValue(String key) throws Exception {
        Map<String, Boolean> mappings = getMappings();
        return mappings.getOrDefault(key,false);
    }

    public Boolean getFollowBackStatus() throws Exception {
        return getValue("followed_by");
    }
}