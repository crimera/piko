/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class ReelResponseItem extends Entity {
    private final Object obj;

    public ReelResponseItem(Object obj) {

        super(obj);
        this.obj = obj;

    }

    public String getReelType() throws Exception {
        Enum reelTypeEnum = (Enum) super.getField("fieldName");
        return reelTypeEnum.toString();
    }

    public UserData getUserData() throws Exception {
        Object userDataObject = super.getField("fieldName");
        if(userDataObject!=null){
            return new UserData(userDataObject);
        }
        return null;
    }

    public int getMediaCount() throws Exception {
        return (Integer) super.getMethod("methodName");
    }


}