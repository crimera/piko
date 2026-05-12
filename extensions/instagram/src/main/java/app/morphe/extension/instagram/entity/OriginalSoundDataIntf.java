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
        // Original audio is actually stored as mp4 but forcefully renaming it to mp3.
        return username + "_" + audioName + "_" + audioId + ".mp3";
    }
}
