/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.legacydownloader.DownloadRequest.DashIndex;

/** Reads only the static, single-period SegmentBase form for which a track index can prove completeness. */
final class DashSegmentManifest {
    private static final Pattern RANGE = Pattern.compile("([0-9]+)-([0-9]+)");
    private static final Pattern DURATION = Pattern.compile("P(?:[0-9]+D)?(?:T(?:[0-9]+H)?(?:[0-9]+M)?(?:[0-9]+(?:\\.[0-9]+)?S)?)?");
    private static final String DASH = "urn:mpeg:dash:schema:mpd:2011";

    private static final class Group {
        boolean supported = true;
        String mimeType;
        String contentType;
    }

    private static final class Representation {
        final Group group;
        boolean supported = true;
        boolean audio;
        int indexCount;
        int urlCount;
        String url;
        long start = -1;
        long end = -1;

        Representation(Group group) {
            this.group = group;
        }
    }

    static Map<String, DashIndex> read(String manifest) throws Exception {
        if (manifest == null || manifest.isEmpty()) return Collections.emptyMap();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(manifest));
        boolean staticPresentation = false;
        int periods = 0;
        long presentationUs = -1, periodUs = -1;
        String[] path = new String[7];
        List<Representation> tracks = new ArrayList<>();
        Set<String> usedUrls = new HashSet<>();
        Set<String> ambiguousUrls = new HashSet<>();
        Group group = null;
        Representation track = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event != XmlPullParser.START_TAG) continue;
            int depth = parser.getDepth();
            String ns = parser.getNamespace();
            String name = ns == null || ns.isEmpty() || DASH.equals(ns) ? parser.getName() : "";
            if (depth < path.length) path[depth] = name;
            boolean adaptation = "Period".equals(path[2]) && "AdaptationSet".equals(path[3]);
            boolean representation = adaptation && "Representation".equals(path[4]);
            boolean linked = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href") != null;
            if (depth == 1) {
                String type = attr(parser, "type");
                staticPresentation = "MPD".equals(name) && (type == null || "static".equals(type));
                presentationUs = duration(attr(parser, "mediaPresentationDuration"));
            } else if (depth == 2 && "Period".equals(name)) {
                periods++;
                String start = attr(parser, "start");
                if (linked || (start != null && duration(start) != 0)) staticPresentation = false;
                periodUs = duration(attr(parser, "duration"));
            } else if (depth == 3 && "Period".equals(path[2]) && "AdaptationSet".equals(name)) {
                group = new Group();
                group.supported = !linked;
                group.mimeType = attr(parser, "mimeType");
                group.contentType = attr(parser, "contentType");
            } else if (depth == 4 && representation) {
                track = new Representation(group);
                track.supported = !linked;
                String mime = attr(parser, "mimeType");
                track.audio = "audio/mp4".equals(mime == null ? group.mimeType : mime)
                        && (group.contentType == null || "audio".equals(group.contentType));
                tracks.add(track);
            } else if (depth >= 2 && depth <= 5 && ("SegmentBase".equals(name)
                    || "SegmentList".equals(name) || "SegmentTemplate".equals(name)
                    || "ContentProtection".equals(name))) {
                if (depth == 5 && representation && track != null) {
                    if ("SegmentBase".equals(name)) {
                        track.indexCount++;
                        String offset = attr(parser, "presentationTimeOffset");
                        if (linked || (offset != null && !offset.matches("0+"))) track.supported = false;
                        String range = attr(parser, "indexRange");
                        Matcher bytes = RANGE.matcher(range == null ? "" : range);
                        if (bytes.matches()) {
                            try {
                                track.start = Long.parseLong(bytes.group(1));
                                track.end = Long.parseLong(bytes.group(2));
                            } catch (NumberFormatException e) {
                                track.supported = false;
                            }
                        }
                    } else track.supported = false;
                } else if (depth == 4 && adaptation && group != null) group.supported = false;
                else if (depth <= 3) staticPresentation = false;
            } else if (depth == 5 && representation && "BaseURL".equals(name) && track != null) {
                track.urlCount++;
                if (linked || attr(parser, "byteRange") != null) track.supported = false;
                track.url = parser.nextText().trim();
                if (!usedUrls.add(track.url)) ambiguousUrls.add(track.url);
            } else if (depth == 6 && representation && "SegmentBase".equals(path[5]) && track != null) {
                // An external initialization or index need not describe the bytes in this BaseURL.
                if (linked || !"Initialization".equals(name) || attr(parser, "sourceURL") != null) track.supported = false;
            }
        }
        if (!staticPresentation || periods != 1 || presentationUs == -2 || periodUs == -2
                || (presentationUs >= 0 && periodUs >= 0 && presentationUs != periodUs)) return Collections.emptyMap();
        long durationUs = periodUs >= 0 ? periodUs : presentationUs;
        if (durationUs <= 0) return Collections.emptyMap();
        Map<String, DashIndex> result = new HashMap<>();
        for (Representation item : tracks) {
            if (!item.supported || !item.group.supported || item.urlCount != 1 || item.indexCount != 1
                    || item.start < 0 || item.end < item.start || item.end == Long.MAX_VALUE
                    || ambiguousUrls.contains(item.url)) continue;
            try {
                URI url = URI.create(item.url);
                if ("https".equals(url.getScheme()) && url.getHost() != null && url.getUserInfo() == null
                        && url.getFragment() == null) {
                    result.put(item.url, new DashIndex(item.start, item.end, durationUs, item.audio));
                }
            } catch (IllegalArgumentException ignored) {
                // An invalid optional candidate does not affect the other renditions.
            }
        }
        return result;
    }

    // Missing (-1) may inherit the other duration. A malformed value (-2) cannot supply evidence.
    private static long duration(String value) {
        if (value == null) return -1;
        if (!DURATION.matcher(value).matches()) return -2;
        try {
            Duration duration = Duration.parse(value);
            return Math.addExact(Math.multiplyExact(duration.getSeconds(), 1_000_000L),
                    (duration.getNano() + 999L) / 1000L);
        } catch (RuntimeException e) {
            return -2;
        }
    }

    private static String attr(XmlPullParser parser, String name) {
        return parser.getAttributeValue(null, name);
    }
}
