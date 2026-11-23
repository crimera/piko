/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import android.media.MediaFormat;

import java.io.InputStream;

import app.morphe.extension.crimera.legacydownloader.MediaType;

public class VideoData extends Entity implements VideoVariant {
    private CachedTrackFormat cachedFormat;

    public void readCachedQuality(InputStream input) {
        if (input != null) cachedFormat = CachedTrackFormat.read(input);
    }

    @Override
    public String getVideoCodec() {
        if (cachedFormat == null || cachedFormat.video == null) return "";
        String mime = cachedFormat.video.getString(MediaFormat.KEY_MIME);
        if ("video/avc".equals(mime)) return "H.264";
        if ("video/hevc".equals(mime)) return "H.265";
        if ("video/av01".equals(mime)) return "AV1";
        if ("video/x-vnd.on2.vp9".equals(mime)) return "VP9";
        return "";
    }

    @Override
    public int getBitrate() {
        return bitrate(cachedFormat == null ? null : cachedFormat.video);
    }

    @Override
    public String getAudioCodec() {
        return cachedFormat != null && cachedFormat.audio != null
                ? cachedFormat.audio.getString(MediaFormat.KEY_MIME) : "";
    }

    @Override
    public int getAudioBitrate() {
        return bitrate(cachedFormat == null ? null : cachedFormat.audio);
    }

    private static int bitrate(MediaFormat format) {
        return format != null && format.containsKey(MediaFormat.KEY_BIT_RATE) ? format.getInteger(MediaFormat.KEY_BIT_RATE) : 0;
    }

    public VideoData(Object obj) {
        super(obj);
    }

    public Integer getHeight() throws Exception {
        Integer height = (Integer) super.getMethod("methodname");
        return height != null ? height : 0;
    }

    public Integer getWidth() throws Exception {
        Integer width = (Integer) super.getMethod("methodname");
        return width != null ? width : 0;
    }

    private Integer getCodec() throws Exception {
        Integer codec = (Integer) super.getMethod("methodname");
        return codec != null ? codec : 0;
    }

    public String getVariantTag() {
        try{
            return this.getHeight()+"x"+this.getWidth()+"-"+this.getCodec();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String getUrl() throws Exception {
        return (String) super.getMethod("methodname");
    }

    public MediaType getMediaType(){
        return MediaType.VIDEO;
    }

}
