/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera.downloader;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Set;

final class DownloadFileNames {
    private DownloadFileNames() {
    }

    static String findAvailable(String requestedName, Set<String> existingNames) {
        if (!existingNames.contains(requestedName)) return requestedName;

        int dot = requestedName.lastIndexOf('.');
        String stem = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String extension = dot > 0 ? requestedName.substring(dot) : "";
        for (int index = 1; index > 0; index++) {
            String suffix = " (" + index + ")" + extension;
            int availableBytes = 255 - suffix.getBytes(StandardCharsets.UTF_8).length;
            if (availableBytes <= 0) throw new IllegalArgumentException("Filename extension is too long");
            String candidate = truncate(stem, availableBytes) + suffix;
            if (!existingNames.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("No available download filename");
    }

    private static String truncate(String value, int maxBytes) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int end = iterator.first();
        int bytes = 0;
        for (int next = iterator.next(); next != BreakIterator.DONE; next = iterator.next()) {
            bytes += value.substring(end, next).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) break;
            end = next;
        }
        return value.substring(0, end);
    }
}
