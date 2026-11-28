/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import android.media.MediaFormat;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.crimera.legacydownloader.DownloadRequest;
import app.morphe.extension.crimera.legacydownloader.DownloadRequest.DashIndex;

public interface AudioMediaInterface {
    public String getAudioUrl() throws Exception;

    public String getDownloadName() throws Exception;

    String getAudioDashManifest() throws Exception;

    Integer getDurationMs() throws Exception;

    default String getFastStartUrl() throws Exception {
        return null;
    }

    default void prepareDownload(DownloadRequest request) {
        try {
            String manifest = getAudioDashManifest();
            Integer durationMs = getDurationMs();
            String fastStart = getFastStartUrl();
            request.prepareQualitySources = () ->
                    prepareAudio(request, manifest, durationMs, fastStart);
        } catch (Exception | LinkageError ignored) {
            // Older or incomplete asset metadata can still provide the original download URL.
        }
    }

    private static void prepareAudio(DownloadRequest request, String manifest, Integer durationMs, String fastStart) {
        Map<String, Long> bitrates = new LinkedHashMap<>();
        try {
            // The reel's playback manifest can describe only an excerpt of the selected sound.
            Map<String, DashIndex> selected = DashSegmentManifest.read(manifest, durationMs, bitrates);
            if (!selected.isEmpty()) {
                DashIndex playbackIndex = request.dashIndex(request.url);
                if (playbackIndex != null) selected.putIfAbsent(request.url, playbackIndex);
                request.readDashIndexes = () -> selected;
            }
        } catch (Exception ignored) {
            // A malformed optional manifest leaves the asset's progressive URLs available.
        }
        // Insert the progressive URL first so it wins bitrate ties, including when all bitrates are unknown.
        Map<String, Long> candidates = new LinkedHashMap<>();
        if (request.url != null && !request.url.isEmpty()) candidates.put(request.url, 0L);
        candidates.putAll(bitrates);
        if (fastStart != null && !fastStart.isEmpty()) candidates.putIfAbsent(fastStart, 0L);
        if (candidates.size() > 1 && request.localSource != null) {
            candidates.replaceAll((url, bitrate) -> bitrate > 0 ? bitrate : cachedAudioBitrate(request, url));
        }
        long highestBitrate = candidates.values().stream().mapToLong(Long::longValue).max().orElse(0);
        candidates.forEach((url, bitrate) -> {
            if (highestBitrate <= 0 || bitrate == highestBitrate) {
                request.qualitySources.add(new DownloadRequest.Source(url, null));
            }
        });
    }

    private static long cachedAudioBitrate(DownloadRequest request, String url) {
        try (InputStream input = request.localSource.open(url)) {
            if (input == null) return 0;
            MediaFormat audio = CachedTrackFormat.read(input).audio;
            if (audio != null && audio.containsKey(MediaFormat.KEY_BIT_RATE)) {
                return audio.getInteger(MediaFormat.KEY_BIT_RATE);
            }
        } catch (IOException | RuntimeException ignored) {
            // A missing bitrate preserves the original order among unranked audio files.
        }
        return 0;
    }
}
