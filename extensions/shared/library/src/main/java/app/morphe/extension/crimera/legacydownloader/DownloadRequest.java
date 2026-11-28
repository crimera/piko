/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.legacydownloader;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/** One file to download; the optional fields are set after construction. */
public class DownloadRequest {
    /** Inclusive byte range of the representation's index and duration of its whole static presentation. */
    public static final class DashIndex {
        public final long indexStart;
        public final long indexEnd;
        public final long durationUs;
        public final boolean audioRepresentation;

        public DashIndex(long indexStart, long indexEnd, long durationUs) {
            this(indexStart, indexEnd, durationUs, false);
        }

        public DashIndex(long indexStart, long indexEnd, long durationUs, boolean audioRepresentation) {
            this.indexStart = indexStart;
            this.indexEnd = indexEnd;
            this.durationUs = durationUs;
            this.audioRepresentation = audioRepresentation;
        }
    }

    public Supplier<Map<String, DashIndex>> readDashIndexes;
    private Map<String, DashIndex> dashIndexes = Collections.emptyMap();

    public synchronized DashIndex dashIndex(String url) {
        if (readDashIndexes != null) {
            dashIndexes = readDashIndexes.get();
            readDashIndexes = null;
        }
        return dashIndexes.get(url);
    }

    /** One rendition, including the audio it needs; a null audio URL means a complete file. */
    public static final class Source {
        public final String url;
        public final String audioUrl;
        public final boolean cacheOnly;

        public Source(String url, String audioUrl) {
            this(url, audioUrl, false);
        }

        public Source(String url, String audioUrl, boolean cacheOnly) {
            this.url = url;
            this.audioUrl = audioUrl;
            this.cacheOnly = cacheOnly;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Source)) return false;
            Source source = (Source) other;
            return Objects.equals(url, source.url) && Objects.equals(audioUrl, source.audioUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, audioUrl);
        }

        boolean uses(String address) {
            return address.equals(url) || address.equals(audioUrl);
        }
    }

    @FunctionalInterface
    public interface LocalSource {
        InputStream open(String url) throws IOException;
    }

    /** Checks each source URL for a complete local copy before downloading it. */
    public LocalSource localSource;
    /** Same-content candidates in preferred quality order. Only eligible formats may use the network. */
    public final List<Source> qualitySources = new ArrayList<>();
    public Runnable prepareQualitySources;

    public List<Source> sourcesByNetworkCost() {
        return sourcesByNetworkCost(null, Collections.emptySet());
    }

    List<Source> sourcesByNetworkCost(Set<Source> remaining, Set<String> stagedTracks) {
        Map<Source, Source> candidates = new LinkedHashMap<>();
        for (Source source : qualitySources) {
            if (source.url == null || source.url.isEmpty()) continue;
            if (remaining != null && !remaining.contains(source)) continue;
            // A cache-only alias must not disable an explicitly permitted source.
            if (!source.cacheOnly || !candidates.containsKey(source)) candidates.put(source, source);
        }
        if (candidates.size() == 1 && !candidates.values().iterator().next().cacheOnly) {
            return new ArrayList<>(candidates.values());
        }
        Map<String, Integer> trackCosts = new HashMap<>();
        for (String url : stagedTracks) trackCosts.put(url, 0);
        Map<Source, Integer> costs = new HashMap<>();
        List<Source> eligible = new ArrayList<>();
        for (Source source : candidates.values()) {
            int cost = trackCosts.computeIfAbsent(source.url, this::networkCost);
            if (source.audioUrl != null) cost += trackCosts.computeIfAbsent(source.audioUrl, this::networkCost);
            if (source.cacheOnly && cost != 0) continue;
            eligible.add(source);
            costs.put(source, cost);
        }
        // Stable sorting retains quality order within each request count.
        eligible.sort(Comparator.comparingInt(costs::get));
        return eligible;
    }

    private int networkCost(String url) {
        if (localSource != null) {
            try (InputStream stream = localSource.open(url)) {
                if (stream != null) return 0;
            } catch (IOException ignored) {
                // The local entry can be evicted while selecting a rendition.
            }
        }
        return 1;
    }

    public Function<Context, String> userAgent;
    /** Streams for a fully cached rendition, including separate audio when present. */
    public Source cachedSource;
    public InputStream cachedMedia;
    public InputStream cachedAudio;
    /** Leading fallback sources at the requested resolution. */
    public int sameResolutionFallbacks;

    public enum ConflictPolicy { RENAME, SKIP, OVERWRITE }

    public ConflictPolicy conflictPolicy = ConflictPolicy.RENAME;
    public String url;
    public String subFolder;
    public String fileName;
    /** Embedded into the MP4 on the way; null leaves the file as served. */
    public DownloadMetadata metadata;
    /** A separate audio file to merge into the video at {@link #url}, as DASH serves it; null for a complete file. */
    public String audioUrl;
    /** Re-encodes xHE-AAC audio at {@link #audioUrl} as AAC-LC, which every player decodes; false merges it as served. */
    public boolean audioToAacLc;
    /** A complete file to try after the primary and alternate sources fail; null omits this final fallback. */
    public String fallbackUrl;
    /** Alternate renditions in retry order, before the legacy complete-file fallback. */
    public final List<Source> fallbackSources = new ArrayList<>();
    /** Opens a complete local video for a sharper first-frame cover, or returns null to save the image. */
    public Callable<InputStream> firstFrameVideo;
    public long firstFrameMinPixels;

    public DownloadRequest(String url, String subFolder, String fileName) {
        this.url = url;
        this.subFolder = subFolder;
        this.fileName = fileName;
    }
}
