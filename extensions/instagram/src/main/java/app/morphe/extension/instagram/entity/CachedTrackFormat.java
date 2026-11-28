/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.io.InputStream;

final class CachedTrackFormat {
    MediaFormat video;
    MediaFormat audio;

    static CachedTrackFormat read(InputStream cached) {
        CachedTrackFormat tracks = new CachedTrackFormat();
        if (cached == null) return tracks;
        // Fast-start MP4s keep track formats in their header. Leave quality unknown when
        // it cannot be read within this bound, without retaining or copying the whole video.
        byte[] header = new byte[256 * 1024];
        MediaExtractor extractor = new MediaExtractor();
        try {
            int end = 0;
            while (end < header.length) {
                int count = cached.read(header, end, header.length - end);
                if (count <= 0) break;
                end += count;
            }
            final int length = end;
            extractor.setDataSource(new MediaDataSource() {
                @Override
                public int readAt(long position, byte[] dest, int offset, int size) {
                    if (size == 0) return 0;
                    if (position < 0 || position >= length) return -1;
                    int count = Math.min(size, length - (int) position);
                    System.arraycopy(header, (int) position, dest, offset, count);
                    return count;
                }

                @Override public long getSize() { return -1; }
                @Override public void close() {}
            });
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (tracks.video == null && mime.startsWith("video/")) tracks.video = format;
                if (tracks.audio == null && mime.startsWith("audio/")) tracks.audio = format;
            }
        } catch (IOException | RuntimeException ignored) {
            // Cache eviction and incomplete headers do not make the original URL unusable.
        } finally {
            extractor.release();
        }
        return tracks;
    }
}
