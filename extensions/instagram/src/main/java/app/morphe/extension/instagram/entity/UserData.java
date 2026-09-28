/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import com.instagram.common.typedurl.ImageUrl;

public class UserData extends Entity {
    private final Object obj;

    public UserData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Object getAdditionalUserInfo() throws Exception {
        // Not cached: a UserData can outlive a single call (e.g. ProfileMoreOption's dialog
        // captures the same instance across button clicks), and this field always exists, so
        // there's no exception overhead to save by caching it - only a staleness risk to add.
        return super.getField(this.obj, "fieldName");
    }

    public Boolean isVerified() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        return (Boolean) super.getMethod(additionalUserInfo, "isVerified");
    }

    public String getUsername() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        return (String) super.getMethod(additionalUserInfo, "methodName");
    }

    public String getFullName() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        String name = (String) super.getMethod(additionalUserInfo, "methodName");
        if(name!=null && !name.isEmpty() && name.length()>0){
            return name;
        }
        // Some users don't have fullname, but only username.
        return this.getUsername();
    }

    public String getBio() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        return (String) super.getMethod(additionalUserInfo, "BCu");
    }

    public String getProfilePictureUrl() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        Object profilePicObject =  super.getMethod(additionalUserInfo, "Bvt");
        if(profilePicObject!=null){
            Entity profilePicEntity = new Entity(profilePicObject);
            return (String) profilePicEntity.getMethod("getUrl");
        }
        return "";
    }

    public ImageUrl getLowResProfilePicture() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        Object imageUrlObject =  super.getMethod(additionalUserInfo, "mediaName");
        return (ImageUrl) imageUrlObject;
    }

    /** The public permalink of this profile: what "share" and "copy link" both hand out. */
    public String getProfileLink() throws Exception {
        return "https://www.instagram.com/" + getUsername() + "/";
    }

    public String getUserId() throws Exception {
        return (String) super.getMethod(this.obj, "getId");
    }

    public UserFriendshipStatus getUserFriendshipStatus() throws Exception {
        Object additionalUserInfo = getAdditionalUserInfo();
        Object friendshipStatusObject = super.getMethod(additionalUserInfo, "methodname");
        return new UserFriendshipStatus(friendshipStatusObject);
    }

}