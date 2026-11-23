/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.graphics.ImageDecoder;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import app.morphe.extension.shared.Utils;

public final class InstagramImageCache {
    private static final Map<String, Entry> entries = new LinkedHashMap<String, Entry>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, InstagramImageCache.Entry> eldest) {
            return size() > 256;
        }
    };

    private static final class Entry {
        final WeakReference<Object> cache;
        final String url;
        final String contentId;
        final String key;

        Entry(Object cache, String key, String url, String contentId) {
            this.cache = new WeakReference<>(cache);
            this.key = key;
            this.url = url;
            this.contentId = contentId;
        }
    }

    // Called at Instagram's disk lookup with the exact signed URL and the cache selected by the app.
    public static synchronized void observe(Object image, Object cache, String key) {
        if (image == null) return;
        String url = address(image);
        if (url == null || cache == null || key == null) return;
        Entry old = entries.get(key);
        if (old != null && old.cache.get() == cache) {
            if (old.url == null || url.equals(old.url)) return;
            // This host key denotes multiple URLs. Its current bytes cannot identify the requested variant.
            entries.put(key, new Entry(cache, key, null, null));
            return;
        }
        entries.put(key, new Entry(cache, key, url, contentId(image)));
    }

    private static String address(Object image) {
        return null;
    }

    private static String contentId(Object image) {
        return null;
    }

    public static synchronized void clear() {
        entries.clear();
    }

    static InputStream open(String url) throws IOException {
        if (url == null) return null;
        Entry entry = null;
        synchronized (InstagramImageCache.class) {
            for (Entry candidate : entries.values()) {
                if (url.equals(candidate.url)) {
                    entry = candidate;
                    break;
                }
            }
        }
        if (entry == null) return null;
        Object cache = entry.cache.get();
        if (cache == null) return null;
        try {
            InputStream result = readEntry(cache, entry.key, new HashMap<>(), entry.contentId);
            synchronized (InstagramImageCache.class) {
                if (entries.get(entry.key) == entry) return result;
            }
            if (result != null) result.close();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // The patch supplies Instagram's metadata-aware, disk-only reader for the selected version.
    private static InputStream readEntry(Object cache, String key, Map<?, ?> diagnostics, String expectedId) throws IOException {
        return null;
    }

    private static InputStream complete(InputStream input, JSONObject metadata, String expectedId) throws IOException {
        if (input == null) return null;
        if (expectedId != null && !expectedId.isEmpty() && metadata != null
                && !metadata.isNull("content_id") && !expectedId.equals(metadata.optString("content_id"))) {
            input.close();
            return null;
        }
        if (metadata != null && metadata.has("scan")) {
            if (metadata.optInt("scan", 0) == -1) return input;
            input.close();
            return null;
        }

        // Some cache configurations omit the scan number even for progressive JPEGs.
        // Validate a snapshot so eviction or replacement cannot change the bytes after validation.
        File snapshot = null;
        try (InputStream source = input) {
            snapshot = File.createTempFile("piko-image-", ".tmp", Utils.getContext().getCacheDir());
            try (FileOutputStream output = new FileOutputStream(snapshot)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            if (Build.VERSION.SDK_INT < 28 || !hasImageEnd(snapshot)) return null;
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(snapshot), (decoder, info, sourceInfo) -> {
                decoder.setTargetSize(1, 1);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            }).recycle();
            return new FileInputStream(snapshot);
        } finally {
            // Android keeps a successfully opened descriptor readable after unlink.
            if (snapshot != null) snapshot.delete();
        }
    }

    private static boolean hasImageEnd(File file) throws IOException {
        try (RandomAccessFile data = new RandomAccessFile(file, "r")) {
            long length = data.length();
            if (length < 12) return false;
            int header = data.readInt();
            if ((header >>> 16) == 0xffd8) {
                data.seek(length - 2);
                return data.readUnsignedShort() == 0xffd9;
            }
            if (header == 0x89504e47) {
                data.seek(length - 12);
                return data.readInt() == 0 && data.readLong() == 0x49454e44ae426082L;
            }
            if (header == 0x52494646) {
                long size = Integer.toUnsignedLong(Integer.reverseBytes(data.readInt()));
                return data.readInt() == 0x57454250 && size + 8 == length;
            }
            if (header == 0x47494638) {
                data.seek(length - 1);
                return data.readUnsignedByte() == 0x3b;
            }
            // AVIF and HEIF encode the complete length of each ISO media box.
            data.seek(4);
            if (data.readInt() != 0x66747970) return false;
            long offset = 0;
            while (length - offset >= 8) {
                data.seek(offset);
                long size = Integer.toUnsignedLong(data.readInt());
                data.readInt();
                int headerSize = 8;
                if (size == 1) {
                    if (length - offset < 16) return false;
                    size = data.readLong();
                    headerSize = 16;
                }
                if (size < headerSize || size > length - offset) return false;
                offset += size;
            }
            return offset == length;
        }
    }
}
