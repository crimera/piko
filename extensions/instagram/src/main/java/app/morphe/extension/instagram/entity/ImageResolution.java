/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.crimera.downloader.MediaType;

/** One image size, with URLs captured before the native candidates can change or become unreadable. */
public final class ImageResolution implements MediaInterface {
    private final int width;
    private final int height;
    private final List<String> candidateUrls = new ArrayList<>();

    private ImageResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public static List<ImageResolution> group(List<? extends ImageData> variants) {
        Map<String, ImageResolution> groups = new LinkedHashMap<>();
        if (variants == null) return new ArrayList<>();
        for (ImageData variant : variants) {
            if (variant == null) continue;
            try {
                String url = variant.getUrl();
                if (url == null) continue;
                URI uri = new URI(url);
                if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) continue;
                Integer width = variant.getWidth();
                Integer height = variant.getHeight();
                if (width == null || height == null || width <= 0 || height <= 0) continue;
                ImageResolution group = groups.computeIfAbsent(width + "x" + height,
                        ignored -> new ImageResolution(width, height));
                // Image candidates expose no comparable quality score; keep their original order.
                if (!group.candidateUrls.contains(url)) {
                    group.candidateUrls.add(url);
                }
            } catch (Exception | LinkageError ignored) {
                // An unreadable candidate must not hide the other images.
            }
        }
        List<ImageResolution> result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparingLong((ImageResolution image) -> (long) image.width * image.height)
                .reversed());
        return result;
    }

    public List<String> getCandidateUrls() {
        return Collections.unmodifiableList(candidateUrls);
    }

    @Override
    public Integer getWidth() {
        return width;
    }

    @Override
    public Integer getHeight() {
        return height;
    }

    @Override
    public String getLabel() {
        return width + "\u00d7" + height;
    }

    @Override
    public String getVariantTag() {
        // Preserve ImageData's existing filename suffix independently of the chooser label.
        return height + "x" + width;
    }

    @Override
    public String getUrl() {
        return candidateUrls.get(0);
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.IMAGE;
    }
}
