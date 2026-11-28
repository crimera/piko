/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.os.Build;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.crimera.legacydownloader.MediaType;

/**
 * A video rendition of a media's DASH manifest, served as a file of its own, with the manifest's
 * matching audio to merge in on download. Instagram lists a fresh video in H.264 at 720p at most and
 * adds AV1 (sometimes VP9) up to the upload's size later, which the progressive video_versions
 * never carry. Doubles as a variant in the download dialog.
 */
public final class DashVideoData implements VideoVariant {
    /**
     * The codecs the remuxer handles, from the Android version whose MediaExtractor
     * reads them from MP4: AV1 since 10, VP9 since 12. Any other codec is never remuxed.
     */
    private enum Codec {
        AV1("AV1", Build.VERSION_CODES.Q),
        VP9("VP9", Build.VERSION_CODES.S),
        H265("H.265", 0),
        H264("H.264", 0),
        OTHER("", Integer.MAX_VALUE);

        final String label;
        final int minSdk;

        Codec(String label, int minSdk) {
            this.label = label;
            this.minSdk = minSdk;
        }

        /** By the first part of an RFC 6381 codecs string, such as av01.0.08M.08 or avc1.4D401F. */
        static Codec of(String codecs) {
            switch (codecs.contains(".") ? codecs.substring(0, codecs.indexOf('.')) : codecs) {
                case "av01": return AV1;
                case "vp09": return VP9;
                case "hev1":
                case "hvc1": return H265;
                case "avc1":
                case "avc3": return H264;
                default: return OTHER;
            }
        }
    }

    private static final class Track {
        final boolean video;
        final Codec codec;
        final String codecs;
        final int adaptationSet;
        final int width;
        final int height;
        final int bandwidth;
        final Map<Integer, Double> quality;
        String url;

        Track(boolean video, String codecs, int adaptationSet, int width, int height, int bandwidth, Map<Integer, Double> quality) {
            this.video = video;
            this.codec = Codec.of(codecs);
            this.codecs = codecs;
            this.adaptationSet = adaptationSet;
            this.width = width;
            this.height = height;
            this.bandwidth = bandwidth;
            this.quality = quality;
        }
    }

    private final Track track;
    private final Track audio;

    private DashVideoData(Track track, Track audio) {
        this.track = track;
        this.audio = audio;
    }

    /**
     * All video renditions this device can remux, sharpest first, retaining alternate files.
     * Audio alternatives retain the selected mix, best known quality first. Throws on an unparsable manifest.
     */
    public static List<DashVideoData> parse(String manifest) throws Exception {
        List<Track> videos = new ArrayList<>();
        List<Track> audioTracks = new ArrayList<>();
        if (manifest != null && !manifest.isEmpty()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(manifest));
            // Representations inherit what their AdaptationSet declares.
            String setMimeType = null;
            String setCodecs = null;
            int adaptationSet = 0;
            boolean periodSeen = false;
            Track pending = null;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event != XmlPullParser.START_TAG && event != XmlPullParser.END_TAG) continue;
                String name = parser.getName();
                if (event == XmlPullParser.END_TAG) {
                    if ("Representation".equals(name)) {
                        if (pending != null && pending.url != null && !pending.url.isEmpty()) {
                            if (pending.video) {
                                videos.add(pending);
                            } else {
                                audioTracks.add(pending);
                            }
                        }
                        pending = null;
                    } else if ("AdaptationSet".equals(name)) {
                        setMimeType = null;
                        setCodecs = null;
                    }
                    continue;
                }
                switch (name) {
                    case "Period":
                        // Each URL describes only its own period in a multi-period presentation.
                        if (periodSeen) return Collections.emptyList();
                        periodSeen = true;
                        break;
                    case "AdaptationSet":
                        adaptationSet++;
                        setMimeType = parser.getAttributeValue(null, "mimeType");
                        setCodecs = parser.getAttributeValue(null, "codecs");
                        break;
                    case "Representation":
                        String mimeType = attribute(parser, "mimeType", setMimeType);
                        String codecs = attribute(parser, "codecs", setCodecs);
                        boolean video = mimeType != null && mimeType.startsWith("video/");
                        pending = video || (mimeType != null && mimeType.startsWith("audio/"))
                                ? new Track(
                                        video,
                                        codecs == null ? "" : codecs,
                                        adaptationSet,
                                        parseInt(parser.getAttributeValue(null, "width")),
                                        parseInt(parser.getAttributeValue(null, "height")),
                                        parseInt(parser.getAttributeValue(null, "bandwidth")),
                                        parseQuality(parser.getAttributeValue(null, "FBPlaybackResolutionMos")))
                                : null;
                        break;
                    case "BaseURL":
                        if (pending != null) pending.url = parser.nextText().trim();
                        break;
                }
            }
        }

        audioTracks.sort(Comparator.comparingInt((Track audio) -> audio.bandwidth).reversed());
        if (!audioTracks.isEmpty()) {
            Track preferred = audioTracks.get(0);
            // Other adaptation sets can be a different language or mix. Keep the same audio encoding.
            audioTracks.removeIf(audio -> audio.adaptationSet != preferred.adaptationSet
                    || !audio.codecs.equals(preferred.codecs));
        }
        List<DashVideoData> renditions = new ArrayList<>();
        for (Track video : videos) {
            if (Build.VERSION.SDK_INT < video.codec.minSdk) continue;
            if (audioTracks.isEmpty()) renditions.add(new DashVideoData(video, null));
            for (Track audio : audioTracks) renditions.add(new DashVideoData(video, audio));
        }
        List<DashVideoData> ordered = new ArrayList<>();
        for (VideoResolution resolution : VideoResolution.group(renditions)) {
            for (VideoVariant candidate : resolution.getCandidates()) ordered.add((DashVideoData) candidate);
        }
        return ordered;
    }

    @Override
    public int shortSide() {
        return Math.min(track.width, track.height);
    }

    /** Such as "1080p"; quality metadata is kept for selection, not shown as a label. */
    @Override
    public String getLabel() {
        return shortSide() + "p";
    }

    @Override
    public String getVideoCodec() {
        return track.codec.label;
    }

    @Override
    public int getBitrate() {
        return track.bandwidth;
    }

    @Override
    public Map<Integer, Double> getQualityScores() {
        return track.quality;
    }

    /** File name safe, such as "1080p-av1". */
    @Override
    public String getVariantTag() {
        return shortSide() + "p-" + track.codec.label.toLowerCase(Locale.ROOT).replace(".", "");
    }

    @Override
    public Integer getHeight() {
        return track.height;
    }

    @Override
    public Integer getWidth() {
        return track.width;
    }

    @Override
    public String getUrl() {
        return track.url;
    }

    @Override
    public String getAudioUrl() {
        return audio == null ? null : audio.url;
    }

    @Override
    public String getAudioCodec() {
        if (audio == null) return "";
        if (audio.codecs.startsWith("mp4a.")) return "audio/mp4a-latm";
        if (audio.codecs.equalsIgnoreCase("opus")) return "audio/opus";
        return "";
    }

    @Override
    public int getAudioBitrate() {
        return audio == null ? 0 : audio.bandwidth;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.VIDEO;
    }

    private static String attribute(XmlPullParser parser, String name, String inherited) {
        String value = parser.getAttributeValue(null, name);
        return value != null ? value : inherited;
    }

    private static int parseInt(String value) {
        return value == null ? 0 : Integer.parseInt(value.trim());
    }

    /**
     * FBPlaybackResolutionMos pairs a playback resolution with the score the rendition gets when
     * viewed at it, as "0:100,360:86.7,720:73.6,1080:66.9". Preserve the evaluation size;
     * scores at different sizes cannot establish which rendition looks better.
     */
    private static Map<Integer, Double> parseQuality(String value) {
        if (value == null) return Collections.emptyMap();
        Map<Integer, Double> scores = new HashMap<>();
        for (String pair : value.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            try {
                int size = Integer.parseInt(parts[0].trim());
                double score = Double.parseDouble(parts[1].trim());
                if (size > 0 && Double.isFinite(score) && score > 0) scores.put(size, score);
            } catch (NumberFormatException ignored) {
                // Optional quality metadata must not discard an otherwise usable download.
            }
        }
        return Collections.unmodifiableMap(scores);
    }
}
