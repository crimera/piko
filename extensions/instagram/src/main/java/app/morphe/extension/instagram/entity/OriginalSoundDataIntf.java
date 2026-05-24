/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class OriginalSoundDataIntf extends Entity implements AudioMediaInterface {
    private final Object obj;

    public OriginalSoundDataIntf(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public UserData getUserData() throws Exception {
        Object userObject = super.getMethod("Bym");
        return new UserData(userObject);
    }

    public String getAudioId() throws Exception {
        return (String) super.getMethod("B8y");
    }

    public String getAudioName() throws Exception {
        return (String) super.getMethod("CPx");
    }

    @Override
    public String getAudioUrl() throws Exception {
        return (String) super.getMethod("Cay");
    }

    @Override
    public String getDownloadName() throws Exception {
        UserData userData = this.getUserData();
        String username = userData.getUsername();
        String audioName = this.getAudioName();
        String audioId = this.getAudioId();

        return username + "_" + audioName + "_" + audioId;
    }
}
