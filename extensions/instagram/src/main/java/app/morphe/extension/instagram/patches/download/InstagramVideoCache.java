/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.download;

import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.util.LruCache;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;

import app.morphe.extension.crimera.legacydownloader.DownloadRequest.DashIndex;

public final class InstagramVideoCache {
    private static final class Entry {
        final WeakReference<Object> manager;
        final String key;

        Entry(Object manager, String key) {
            this.manager = new WeakReference<>(manager);
            this.key = key;
        }
    }

    // A URL alone cannot reproduce the key: the player also supplies the track and video identifiers.
    private static final LruCache<String, Entry> observed = new LruCache<>(512);

    private static final LruCache<String, String> keyOwners = new LruCache<>(512);

    public static synchronized void remember(Object manager, Uri uri, String key) {
        if (manager != null && uri != null && key != null) {
            String url = mediaUrl(uri);
            if (url == null) return;
            String previous = keyOwners.get(key);
            if (previous == null) keyOwners.put(key, url);
            else if (!url.equals(previous)) {
                // A shared key cannot identify the selected rendition unambiguously.
                keyOwners.put(key, "");
                observed.remove(previous);
                observed.remove(url);
                return;
            }
            Entry entry = observed.get(url);
            if (entry == null || entry.manager.get() != manager || !entry.key.equals(key)) {
                observed.put(url, new Entry(manager, key));
            }
        }
    }

    private static String mediaUrl(Uri uri) {
        // The player builds its cache key from the remote URI when opening its loopback proxy.
        if ("127.0.0.1".equals(uri.getHost())) {
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getUserInfo() != null) return null;
            if (uri.getQueryParameters("remote-uri").size() != 1) return null;
            String remote = uri.getQueryParameter("remote-uri");
            if (remote == null || remote.isEmpty()) return null;
            uri = Uri.parse(remote);
            if ("127.0.0.1".equals(uri.getHost())) return null;
        }
        return "https".equals(uri.getScheme()) && uri.getHost() != null
                && !uri.getHost().isEmpty() && uri.getUserInfo() == null ? uri.toString() : null;
    }

    public static synchronized void clear() {
        observed.evictAll();
        keyOwners.evictAll();
    }

    private static synchronized Entry entryFor(String url) {
        Entry entry = observed.get(url);
        return entry != null && url.equals(keyOwners.get(entry.key)) ? entry : null;
    }

    static InputStream open(String url) throws IOException {
        return openTrack(url, null);
    }

    static InputStream openTrack(String url, DashIndex index) throws IOException {
        Entry entry = entryFor(url);
        Object manager = entry == null ? null : entry.manager.get();
        if (manager == null) return null;
        try {
            Object cache = manager.getClass().getField("videoCacheField").get(manager);
            if (cache == null) return null;
            Class<?> type = cache.getClass();
            long total = (Long) type.getMethod("videoContentLengthMethod", String.class).invoke(cache, entry.key);
            if (total <= 0 && index == null) return null;

            NavigableSet<?> spans = (NavigableSet<?>) type.getMethod("videoSpansMethod", String.class)
                    .invoke(cache, entry.key);
            List<Part> parts = new ArrayList<>();
            long covered = 0;
            for (Object span : spans) {
                Class<?> spanType = span.getClass();
                long position = spanType.getField("videoSpanPosition").getLong(span);
                long length = spanType.getField("videoSpanLength").getLong(span);
                File file = (File) spanType.getField("videoSpanFile").get(span);
                if (position != covered || length <= 0 || length > Long.MAX_VALUE - covered || file == null
                        || (total > 0 && length > total - covered)) break;
                StructStat stat = Os.stat(file.getPath());
                if (stat.st_size != length) break;
                parts.add(new Part(file, stat));
                covered += length;
            }
            if (total <= 0) {
                try (InputStream prefix = new SpanStream(url, entry, parts, covered)) {
                    total = DashSegmentIndex.mediaEnd(prefix, covered, index.indexStart, index.indexEnd,
                            index.durationUs, index.audioRepresentation && parts.size() == spans.size());
                }
                if (total <= 0 || total > covered) return null;
            } else if (covered != total || parts.size() != spans.size()) {
                return null;
            }
            if (entryFor(url) != entry) return null;
            return new SpanStream(url, entry, parts, total);
        } catch (ReflectiveOperationException | ErrnoException | RuntimeException ignored) {
            return null;
        }
    }

    private static final class Part {
        final File file;
        final StructStat stat;

        Part(File file, StructStat stat) {
            this.file = file;
            this.stat = stat;
        }
    }

    private static final class SpanStream extends InputStream {
        private final String url;
        private final Entry entry;
        private final List<Part> parts;
        private int next;
        private FileInputStream current;
        private long remaining;
        private long bytesLeft;
        private boolean closed;

        SpanStream(String url, Entry entry, List<Part> parts, long limit) {
            this.bytesLeft = limit;
            this.url = url;
            this.entry = entry;
            this.parts = parts;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (closed) throw new IOException("Stream is closed");
            if (length == 0) return 0;
            if (bytesLeft == 0) return -1;
            if (remaining == 0) {
                if (current != null) {
                    current.close();
                    current = null;
                }
                if (entryFor(url) != entry) throw new IOException("Video cache key changed");
                if (next == parts.size()) return -1;
                Part part = parts.get(next++);
                current = new FileInputStream(part.file);
                try {
                    StructStat stat = Os.fstat(current.getFD());
                    if (stat.st_dev != part.stat.st_dev || stat.st_ino != part.stat.st_ino
                            || stat.st_size != part.stat.st_size || stat.st_mtime != part.stat.st_mtime) {
                        throw new IOException("Cache span changed");
                    }
                    remaining = Math.min(bytesLeft, stat.st_size);
                } catch (ErrnoException e) {
                    throw new IOException("Cannot inspect cache span", e);
                }
            }
            int count = current.read(buffer, offset, (int) Math.min(remaining, length));
            if (count < 0) throw new EOFException("Incomplete cache span");
            remaining -= count;
            bytesLeft -= count;
            return count;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (current != null) current.close();
        }
    }
}
