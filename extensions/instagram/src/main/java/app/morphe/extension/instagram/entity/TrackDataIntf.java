/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import java.util.Map;
import com.instagram.api.schemas.MusicInfo;

public class TrackDataIntf extends Entity implements AudioMediaInterface {
    private final Object obj;

    public TrackDataIntf(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Object getMusicInfo() throws Exception {
        Object mediatorObject = super.getField("A02");
        return (MusicInfo) super.getMethod(mediatorObject,"getValue");
    }

    private Object getTrackData() throws Exception {
        return super.getMethod(this.getMusicInfo(), "CJN");
    }

    public Map getMappings() throws Exception {
        Object trackData = this.getTrackData();
        Map trackMap = (Map) super.getMethod(trackData, "Gkh");
        return trackMap;
    }

    private Object getValue(String key) throws Exception {
        Map mappings = getMappings();
        return mappings.getOrDefault(key, null);
    }

    public String getSongId() throws Exception {
        return String.valueOf(this.getValue("audio_asset_id"));
    }

    public String getSongName() throws Exception {
        return (String) this.getValue("title");
    }

    public String getSongArtistName() throws Exception {
        return (String) this.getValue("display_artist");
    }

    public String getSongArtistUsername() throws Exception {
        return (String) this.getValue("ig_username");
    }

    @Override
    public String getAudioUrl() throws Exception {
        return (String) this.getValue("progressive_download_url");
    }

    public String getSongThumbnailUrl() throws Exception {
        return (String) this.getValue("cover_artwork_thumbnail_uri");
    }

    @Override
    public String getDownloadName() throws Exception {
        String artistName = this.getSongArtistName();
        String songName = this.getSongName();
        return artistName + "_" + songName;
    }
}
