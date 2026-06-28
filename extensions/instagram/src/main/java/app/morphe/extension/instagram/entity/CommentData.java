/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;


public class CommentData extends Entity implements GifMediaInterface {
    private final Object obj;

    public CommentData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    public boolean hasText() throws Exception {
        String text = this.getText();
        return text!=null && text.length()>0;
    }

    public String getText() throws Exception {
        return (String) super.getField("A0N");
    }

    public UserData getCommentUserData() throws Exception {
        Object userObj = super.getField("fieldName");
        if(userObj!=null){
            return new UserData(userObj);
        }
        return null;
    }

    public boolean hasMedia() throws Exception {
        return this.hasImageMedia() || this.hasGifMedia();
    }

    public boolean hasImageMedia() throws Exception {
        return this.getImageMedia()!=null;
    }

    public MediaData getImageMedia() throws Exception {
        Entity imageDataEntity = super.getFieldAsEntity("fieldName");
        Object mediaObj = imageDataEntity.getField("fieldName2");
        if(mediaObj!=null){
            return new MediaData(mediaObj);
        }
        return null;
    }

    private Object getGifMedia() throws Exception {
        return super.getField("fieldName");
    }

    public boolean hasGifMedia() throws Exception {
        return this.getGifMedia()!=null;
    }

    private String getGifObjectField(String fieldName) throws Exception {
        return (String) super.getField(this.getGifMedia(), fieldName);
    }

    @Override
    public String getGifUrl() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getWebpUrl() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifTag() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifCreatorName() throws Exception {
        return this.getGifObjectField("fieldName");
    }

    @Override
    public String getGifDownloadName() throws Exception {
        String creatorName = this.getGifCreatorName();
        String gifTag = this.getGifTag();
        return creatorName+"_"+gifTag+".gif";
    }
}
