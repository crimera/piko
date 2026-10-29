/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import app.morphe.extension.crimera.downloader.MediaType;

/** One resolution in the chooser, retaining its other renditions for download retries. */
public final class VideoResolution implements MediaInterface {
    private final int resolution;
    private final List<VideoVariant> candidates;

    private VideoResolution(int resolution, List<VideoVariant> candidates) {
        this.resolution = resolution;
        this.candidates = Collections.unmodifiableList(candidates);
    }

    public static List<VideoResolution> group(List<? extends VideoVariant> variants) throws Exception {
        Map<Integer, List<VideoVariant>> groups = new TreeMap<>(Collections.reverseOrder());
        for (VideoVariant variant : variants) {
            if (variant == null) continue;
            try {
                String url = variant.getUrl();
                if (url == null || url.trim().isEmpty()) continue;
                Integer width = variant.getWidth();
                Integer height = variant.getHeight();
                int size = width == null || height == null ? 0 : Math.max(0, Math.min(width, height));
                groups.computeIfAbsent(size, ignored -> new ArrayList<>()).add(variant);
            } catch (Exception | LinkageError ignored) {
                // One unreadable rendition must not hide the remaining resolutions.
            }
        }
        List<VideoResolution> result = new ArrayList<>();
        for (Map.Entry<Integer, List<VideoVariant>> entry : groups.entrySet()) {
            result.add(new VideoResolution(entry.getKey(), rank(entry.getValue())));
        }
        return result;
    }

    /** Missing metadata makes this a partial comparison, unsuitable for a sorting comparator. */
    static <T extends VideoVariant> List<T> rank(List<T> variants) {
        List<T> remaining = new ArrayList<>(variants);
        List<T> result = new ArrayList<>(variants.size());
        while (!remaining.isEmpty()) {
            // Prefer the first candidate with no known better alternative. A single winner scan
            // can overlook an earlier candidate when some pairs have incomparable metadata.
            int best = 0;
            for (int i = 0; i < remaining.size(); i++) {
                boolean beaten = false;
                for (int j = 0; j < remaining.size(); j++) {
                    if (j != i && better(remaining.get(j), remaining.get(i))) {
                        beaten = true;
                        break;
                    }
                }
                if (!beaten) {
                    best = i;
                    break;
                }
            }
            // Conflicting evidence can form a cycle; preserve source order in that case too.
            result.add(remaining.remove(best));
        }
        return result;
    }

    private static boolean better(VideoVariant candidate, VideoVariant current) {
        Map<Integer, Double> scores = candidate.getQualityScores();
        Map<Integer, Double> currentScores = current.getQualityScores();
        int commonResolution = -1;
        for (int size : scores.keySet()) {
            if (size > commonResolution && currentScores.containsKey(size)) commonResolution = size;
        }
        if (commonResolution >= 0) {
            int quality = Double.compare(scores.get(commonResolution), currentScores.get(commonResolution));
            if (quality != 0) return quality > 0;
        }
        // A bitrate across different codecs is not a reliable measure of visual quality.
        String codec = candidate.getVideoCodec();
        return !codec.isEmpty() && codec.equals(current.getVideoCodec())
                && candidate.getBitrate() > 0 && current.getBitrate() > 0
                && candidate.getBitrate() > current.getBitrate();
    }

    public List<VideoVariant> getCandidates() {
        return candidates;
    }

    @Override
    public int shortSide() {
        return resolution;
    }

    @Override
    public String getLabel() {
        return resolution > 0 ? resolution + "p" : "?";
    }

    @Override
    public String getVariantTag() {
        // Retries may switch codecs, but every candidate belongs to this resolution.
        return resolution > 0 ? resolution + "p" : "unknown";
    }

    @Override
    public Integer getHeight() throws Exception {
        return candidates.get(0).getHeight();
    }

    @Override
    public Integer getWidth() throws Exception {
        return candidates.get(0).getWidth();
    }

    @Override
    public String getUrl() throws Exception {
        return candidates.get(0).getUrl();
    }

    @Override
    public String getAudioUrl() {
        return candidates.get(0).getAudioUrl();
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.VIDEO;
    }
}
