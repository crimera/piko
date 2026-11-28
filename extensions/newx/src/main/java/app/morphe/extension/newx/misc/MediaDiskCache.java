package app.morphe.extension.newx.misc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded extension-owned disk cache for profile Photos thumbnails.
 *
 * <p>Pure {@code java.io} implementation with no Android dependencies so the eviction,
 * atomic-commit, and cleanup policy can be unit-tested on the JVM. All filesystem
 * bookkeeping (timestamp touch, rename, trim) shares one process-local lock. Network
 * I/O and bitmap decode always happen outside that lock in the caller.
 *
 * <p>Policy (see {@code docs/newx-profile-photos-cache-spec.md}):
 * <ul>
 *   <li>64 MiB total for committed entries, 4 MiB per entry.</li>
 *   <li>Network URL is the canonical write key; reads check network URL then cache URL.</li>
 *   <li>MD5 filenames for compatibility; readers never match {@code *.tmp}.</li>
 *   <li>LRU by last-modified with filename tie-break; tmp files excluded from totals.</li>
 * </ul>
 */
public final class MediaDiskCache {
    public static final String CACHE_DIR_NAME = "piko_media_cache";
    public static final long MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_DISK_ENTRY_BYTES = 4 * 1024 * 1024;
    static final int MAX_READ_BYTES = 8 * 1024 * 1024;
    private static final String TMP_SUFFIX = ".tmp";
    // Tmp files older than this are stale: the writer crashed or was killed.
    // Fresh tmp files may belong to a concurrent writer, so trims only reap stale ones
    // while startup cleanup reaps all (no writers are running yet).
    private static final long STALE_TMP_AGE_MILLIS = 60_000L;

    private static final AtomicLong EVICTED_ENTRIES = new AtomicLong();
    private static final AtomicLong EVICTED_BYTES = new AtomicLong();

    private static final Object LOCK = new Object();

    private MediaDiskCache() {
    }

    public static File resolveDir(File cacheRoot) {
        if (cacheRoot == null) return null;
        return new File(cacheRoot, CACHE_DIR_NAME);
    }

    /** MD5 hex for compatibility with existing entries; never returns the URL itself. */
    public static String fileNameForKey(String key) {
        if (key == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(key.hashCode());
        }
    }

    public static File fileForKey(File dir, String key) {
        if (dir == null || key == null) return null;
        String name = fileNameForKey(key);
        if (name == null) return null;
        return new File(dir, name);
    }

    /**
     * Reads one key. On hit the recency timestamp is touched under the shared lock;
     * content is read outside the lock so trims never block on I/O. Zero-byte or
     * oversized files are deleted and treated as misses. Never throws.
     */
    public static byte[] readEntry(File dir, String key) {
        if (dir == null || key == null) return null;
        File file = fileForKey(dir, key);
        if (file == null) return null;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            try {
                if (!file.isFile() || !file.exists()) return null;
                long length = file.length();
                if (length <= 0 || length > MAX_READ_BYTES) {
                    tryDelete(file);
                    return null;
                }
                try {
                    file.setLastModified(now);
                } catch (RuntimeException ignored) {
                }
            } catch (SecurityException e) {
                return null;
            }
        }
        try {
            return readFully(file);
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Canonical read: network URL first, then cache URL (deduped). */
    public static byte[] read(File dir, String networkUrl, String cacheUrl) {
        if (dir == null) return null;
        if (networkUrl != null) {
            byte[] data = readEntry(dir, networkUrl);
            if (data != null) return data;
        }
        if (cacheUrl != null && !cacheUrl.equals(networkUrl)) {
            return readEntry(dir, cacheUrl);
        }
        return null;
    }

    /**
     * Atomic commit under the canonical key. Rejects entries over
     * {@link #MAX_DISK_ENTRY_BYTES} without touching disk. Content is written to a
     * unique {@code *.tmp} sibling outside the lock, then renamed and trimmed under
     * the lock. Returns false (never throws) when persistence is skipped or fails;
     * the caller still displays the decoded bitmap.
     */
    public static boolean write(File dir, String canonicalKey, byte[] data) {
        if (dir == null || canonicalKey == null || data == null || data.length == 0) return false;
        if (data.length > MAX_DISK_ENTRY_BYTES) return false;
        String base = fileNameForKey(canonicalKey);
        if (base == null) return false;
        try {
            if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) return false;
            if (!dir.isDirectory()) return false;
        } catch (SecurityException e) {
            return false;
        }
        File tmp;
        try {
            String tmpName = base + "-" + Thread.currentThread().getId()
                    + "-" + System.nanoTime() + TMP_SUFFIX;
            tmp = new File(dir, tmpName);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.flush();
                try {
                    out.getFD().sync();
                } catch (IOException ignored) {
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        synchronized (LOCK) {
            try {
                File dst = new File(dir, base);
                if (dst.exists()) {
                    if (!tmp.renameTo(dst)) {
                        tryDelete(dst);
                        if (!tmp.renameTo(dst)) {
                            tryDelete(tmp);
                            return false;
                        }
                    }
                } else if (!tmp.renameTo(dst)) {
                    tryDelete(tmp);
                    return false;
                }
                try {
                    dst.setLastModified(System.currentTimeMillis());
                } catch (RuntimeException ignored) {
                }
                trimLocked(dir);
                return true;
            } catch (RuntimeException e) {
                tryDelete(tmp);
                return false;
            }
        }
    }

    /** Deletes one canonical entry; used when a hit cannot be decoded. Never throws. */
    public static void deleteEntry(File dir, String key) {
        if (dir == null || key == null) return;
        File file = fileForKey(dir, key);
        if (file == null) return;
        synchronized (LOCK) {
            tryDelete(file);
        }
    }

    /**
     * One-shot startup cleanup: create the directory, drop stale {@code *.tmp} and
     * zero-byte files, then trim pre-existing unbounded entries to the limit.
     * Callers must invoke this off the UI thread. Never throws.
     */
    public static void cleanup(File dir) {
        if (dir == null) return;
        try {
            if (!dir.exists()) {
                try {
                    dir.mkdirs();
                } catch (RuntimeException ignored) {
                }
                return;
            }
            if (!dir.isDirectory()) return;
        } catch (SecurityException e) {
            return;
        }
        synchronized (LOCK) {
            try {
                deleteTmpFilesLocked(dir);
                deleteZeroByteLocked(dir);
                trimLocked(dir);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Trims committed entries to {@link #MAX_DISK_CACHE_BYTES}. Never throws. */
    public static void trimToLimit(File dir) {
        if (dir == null) return;
        synchronized (LOCK) {
            try {
                trimLocked(dir);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Process-lifetime LRU evictions (over-limit trims only, not tmp/zero-byte cleanup). */
    public static long evictedEntries() {
        return EVICTED_ENTRIES.get();
    }

    /** Bytes reclaimed by over-limit evictions. */
    public static long evictedBytes() {
        return EVICTED_BYTES.get();
    }

    /** Point-in-time disk usage snapshot for the Developer tools stats screen. Never throws. */
    public static final class Stats {
        public final int entryCount;
        public final long totalBytes;
        public final int tmpCount;
        public final long largestBytes;
        public final long oldestModified;
        public final long newestModified;

        Stats(
                int entryCount,
                long totalBytes,
                int tmpCount,
                long largestBytes,
                long oldestModified,
                long newestModified
        ) {
            this.entryCount = entryCount;
            this.totalBytes = totalBytes;
            this.tmpCount = tmpCount;
            this.largestBytes = largestBytes;
            this.oldestModified = oldestModified;
            this.newestModified = newestModified;
        }
    }

    public static Stats stats(File dir) {
        int entries = 0;
        long total = 0L;
        int tmp = 0;
        long largest = 0L;
        long oldest = 0L;
        long newest = 0L;
        if (dir == null) return new Stats(0, 0L, 0, 0L, 0L, 0L);
        synchronized (LOCK) {
            try {
                File[] files = dir.listFiles();
                if (files == null) return new Stats(0, 0L, 0, 0L, 0L, 0L);
                for (File file : files) {
                    if (file == null) continue;
                    try {
                        if (isTmpFile(file)) {
                            if (file.isFile()) tmp++;
                            continue;
                        }
                        if (!file.isFile() || file.length() <= 0) continue;
                        long length = file.length();
                        long modified = file.lastModified();
                        entries++;
                        total += length;
                        if (length > largest) largest = length;
                        if (oldest == 0L || modified < oldest) oldest = modified;
                        if (modified > newest) newest = modified;
                    } catch (RuntimeException ignored) {
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return new Stats(entries, total, tmp, largest, oldest, newest);
    }

    /**
     * Deletes all committed entries and tmp files (e.g. from the stats screen).
     * Returns the deleted file count. Never throws.
     */
    public static int clear(File dir) {
        if (dir == null) return 0;
        int deleted = 0;
        synchronized (LOCK) {
            try {
                File[] files = dir.listFiles();
                if (files == null) return 0;
                for (File file : files) {
                    if (file == null) continue;
                    try {
                        if (file.isFile() && tryDelete(file)) deleted++;
                    } catch (RuntimeException ignored) {
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return deleted;
    }

    /** Committed total in bytes, excluding {@code *.tmp}. Never throws. */
    public static long committedBytes(File dir) {
        if (dir == null) return 0L;
        synchronized (LOCK) {
            try {
                long total = 0L;
                File[] files = dir.listFiles();
                if (files == null) return 0L;
                for (File file : files) {
                    if (file == null || isTmpFile(file)) continue;
                    try {
                        if (file.isFile()) total += Math.max(0L, file.length());
                    } catch (SecurityException ignored) {
                    }
                }
                return total;
            } catch (SecurityException e) {
                return 0L;
            }
        }
    }

    /** Committed files oldest-first (last-modified, filename tie-break). Never throws. */
    public static List<File> committedFilesOldestFirst(File dir) {
        List<File> result = new ArrayList<>();
        if (dir == null) return result;
        synchronized (LOCK) {
            try {
                File[] files = dir.listFiles();
                if (files == null) return result;
                for (File file : files) {
                    if (file == null || isTmpFile(file)) continue;
                    try {
                        if (file.isFile()) result.add(file);
                    } catch (SecurityException ignored) {
                    }
                }
                sortOldestFirst(result);
            } catch (SecurityException ignored) {
            }
        }
        return result;
    }

    static boolean isTmpFile(File file) {
        String name;
        try {
            name = file.getName();
        } catch (SecurityException e) {
            return false;
        }
        return name != null && name.endsWith(TMP_SUFFIX);
    }

    private static void trimLocked(File dir) {
        deleteStaleTmpFilesLocked(dir);
        File[] files = dir.listFiles();
        if (files == null) return;
        List<File> committed = new ArrayList<>();
        long total = 0L;
        for (File file : files) {
            if (file == null || isTmpFile(file)) continue;
            try {
                if (!file.isFile()) continue;
                long length = file.length();
                if (length <= 0) {
                    tryDelete(file);
                    continue;
                }
                committed.add(file);
                total += length;
            } catch (SecurityException ignored) {
            }
        }
        if (total <= MAX_DISK_CACHE_BYTES) return;
        sortOldestFirst(committed);
        for (File victim : committed) {
            if (total <= MAX_DISK_CACHE_BYTES) break;
            try {
                long length = victim.length();
                if (tryDelete(victim)) {
                    total -= Math.max(0L, length);
                    EVICTED_ENTRIES.incrementAndGet();
                    EVICTED_BYTES.addAndGet(Math.max(0L, length));
                }
            } catch (SecurityException ignored) {
            }
        }
    }

    private static void sortOldestFirst(List<File> files) {
        try {
            files.sort(new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    long diff = a.lastModified() - b.lastModified();
                    if (diff < 0) return -1;
                    if (diff > 0) return 1;
                    return a.getName().compareTo(b.getName());
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    private static void deleteStaleTmpFilesLocked(File dir) {
        long now = System.currentTimeMillis();
        File[] files;
        try {
            files = dir.listFiles();
        } catch (SecurityException e) {
            return;
        }
        if (files == null) return;
        for (File file : files) {
            if (file == null || !isTmpFile(file)) continue;
            try {
                long age = now - file.lastModified();
                if (age >= STALE_TMP_AGE_MILLIS) tryDelete(file);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static void deleteTmpFilesLocked(File dir) {
        File[] files;
        try {
            files = dir.listFiles();
        } catch (SecurityException e) {
            return;
        }
        if (files == null) return;
        for (File file : files) {
            if (file == null || !isTmpFile(file)) continue;
            tryDelete(file);
        }
    }

    private static void deleteZeroByteLocked(File dir) {
        File[] files;
        try {
            files = dir.listFiles();
        } catch (SecurityException e) {
            return;
        }
        if (files == null) return;
        for (File file : files) {
            if (file == null || isTmpFile(file)) continue;
            try {
                if (file.isFile() && file.length() <= 0) tryDelete(file);
            } catch (SecurityException ignored) {
            }
        }
    }

    private static boolean tryDelete(File file) {
        try {
            return file.delete();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] readFully(File file) throws IOException {
        long length = file.length();
        int initial = length > 0 && length < 64 * 1024 ? (int) length : 16 * 1024;
        ByteArrayOutputStream out = new ByteArrayOutputStream(initial);
        byte[] buffer = new byte[16 * 1024];
        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                if (total > MAX_READ_BYTES - read) throw new IOException("entry too large");
                out.write(buffer, 0, read);
                total += read;
            }
        }
        return out.toByteArray();
    }
}
