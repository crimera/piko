/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


import com.instagram.common.session.UserSession;

public class ProfileInfo extends Entity {
    protected final Object obj;

    public ProfileInfo(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Entity getProfileRelatedDetails() throws Exception {
        return super.getFieldAsEntity("fieldName");
    }

    private Entity getUserDetailViewModel() throws Exception {
        return super.getFieldAsEntity("fieldName");
    }

    public boolean isSelfProfile() throws Exception {
        Entity profileRelatedDetails = getProfileRelatedDetails();
        return (Boolean) profileRelatedDetails.getField("fieldName");
    }

    public UserSession getUserSession() throws Exception {
        return (UserSession) getUserDetailViewModel().getField("sessionField");
    }

    public UserData getUserData() throws Exception {
        return new UserData(getProfileUser(getUserDetailViewModel().getObject()));
    }

    public static Object getProfileUser(Object viewModel) {
        // Replaced with the active profile state read from Instagram's view model.
        return null;
    }

}
