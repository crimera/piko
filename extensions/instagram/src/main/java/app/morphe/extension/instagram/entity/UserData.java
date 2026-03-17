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


public class UserData extends Entity {
    private final Object obj;

    public UserData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Object getAdditionalUserInfo() throws Exception {
        return super.getField(this.obj,"fieldName");
    }

    public String getUsername() {
        try{
            Object additionalUserInfo = getAdditionalUserInfo();
            return (String) super.getMethod(additionalUserInfo,"methodName");
        }catch (Exception ex){
            return null;
        }
    }

    public String getFullname() {
        try{
            Object additionalUserInfo = getAdditionalUserInfo();
            return (String) super.getMethod(additionalUserInfo,"methodName");
        }catch (Exception ex){
            return null;
        }
    }

    public String getUserId() throws Exception {
        return (String)super.getMethod(this.obj,"getId");
    }

    public UserFriendshipStatus getUserFriendshipStatus() throws Exception {
        return new UserFriendshipStatus(this.obj);
    }

}