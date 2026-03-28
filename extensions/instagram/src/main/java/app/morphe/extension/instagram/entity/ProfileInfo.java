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


public class ProfileInfo extends Entity {
    protected final Object obj;

    public ProfileInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }
    private Entity getFieldAsEntity(String fieldName) throws Exception {
        Object object = this.getField(fieldName);
        return new Entity(object);
    }
    private Entity getProfileRelatedDetails() throws Exception {
        return getFieldAsEntity("fieldName");
    }

    private Entity getUserDetailViewModel() throws Exception {
        return getFieldAsEntity("fieldName");
    }

    public boolean isSelfProfile() throws Exception {
        Entity profileRelatedDetails = getProfileRelatedDetails();
        return (Boolean) profileRelatedDetails.getField("fieldName");
    }

    public UserData getUserData() throws Exception {
        Entity userDetailViewModel = getUserDetailViewModel();
        Object userDataObject = userDetailViewModel.getField("fieldName");
        return new UserData(userDataObject);
    }

}
