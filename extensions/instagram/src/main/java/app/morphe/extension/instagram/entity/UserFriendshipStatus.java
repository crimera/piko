/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.instagram.entity;

import java.util.Map;
import java.util.HashMap;
import com.instagram.user.model.FriendshipStatus;
import app.morphe.extension.crimera.PikoUtils;

public class UserFriendshipStatus extends Entity {
    private final Object obj;

    public UserFriendshipStatus(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public Map<String, Boolean> getMappings(){
        try {
            Class<?> helperClass = Class.forName("classname");
            return (Map) super.getMethod(helperClass, "methodname", new Class[]{FriendshipStatus.class}, this.obj);
        } catch (Exception e) {
            PikoUtils.logger(e);
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