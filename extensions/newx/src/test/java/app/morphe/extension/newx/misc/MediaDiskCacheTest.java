package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Focused checks for {@code docs/newx-profile-photos-cache-spec.md}.
 *
 * <p>Each test guards a real shipped failure: unbounded disk growth, duplicate
 * network/cache URL files, corrupt entries blocking the gallery, partial tmp
 * files published under concurrency, and oversized entries persisted to disk.
 * Old code had no size limit, wrote two files per image, and never cleaned tmp.
 */
public final class MediaDiskCacheTest {
    private static final long MIB = 1024L * 1024L;

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private File cacheDir() {
        File root = temporary.getRoot();
        File dir = MediaDiskCache.resolveDir(root);
        assertNotNull(dir);
        return dir;
    }

    private static byte[] bytes(int size, byte seed) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) (seed + i);
        return data;
    }

    private static void sparse(File file, long length) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.setLength(length);
        }
    }

    @Test
    public void hitReturnsBytesAndUpdatesRecency() {
        File dir = cacheDir();
        byte[] data = bytes(1024, (byte) 7);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/a.jpg", data));

        File file = MediaDiskCache.fileForKey(dir, "https://example.com/a.jpg");
        assertNotNull(file);
        assertTrue(file.setLastModified(System.currentTimeMillis() - 60_000L));
        long before = file.lastModified();

        assertArrayEquals(data, MediaDiskCache.read(dir, "https://example.com/a.jpg", null));
        assertTrue(MediaDiskCache.fileForKey(dir, "https://example.com/a.jpg").lastModified() >= before);
    }

    @Test
    public void missReturnsNull() {
        File dir = cacheDir();
        assertNull(MediaDiskCache.read(dir, "https://example.com/missing.jpg", "https://example.com/missing-small.jpg"));
        assertNull(MediaDiskCache.readEntry(dir, "https://example.com/missing.jpg"));
    }

    @Test
    public void oversizedEntryDisplayedButNotPersisted() {
        File dir = cacheDir();
        byte[] exact = bytes(MediaDiskCache.MAX_DISK_ENTRY_BYTES, (byte) 1);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/exact.jpg", exact));
        assertArrayEquals(exact, MediaDiskCache.readEntry(dir, "https://example.com/exact.jpg"));

        byte[] over = bytes(MediaDiskCache.MAX_DISK_ENTRY_BYTES + 1, (byte) 2);
        assertFalse(MediaDiskCache.write(dir, "https://example.com/big.jpg", over));
        assertNull(MediaDiskCache.readEntry(dir, "https://example.com/big.jpg"));
    }

    @Test
    public void totalBoundedAfterTrim() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        // Pre-existing unbounded entries (old code capped only per-response at 8 MiB).
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/old1")), 30 * MIB);
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/old2")), 30 * MIB);
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/old3")), 30 * MIB);

        MediaDiskCache.trimToLimit(dir);
        assertTrue(MediaDiskCache.committedBytes(dir) <= MediaDiskCache.MAX_DISK_CACHE_BYTES);
    }

    @Test
    public void oldestEvictedFirstWithFilenameTieBreak() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        File f1 = new File(dir, MediaDiskCache.fileNameForKey("https://example.com/evict-a"));
        File f2 = new File(dir, MediaDiskCache.fileNameForKey("https://example.com/evict-b"));
        File f3 = new File(dir, MediaDiskCache.fileNameForKey("https://example.com/evict-c"));
        sparse(f1, 30 * MIB);
        sparse(f2, 30 * MIB);
        sparse(f3, 30 * MIB);
        long now = System.currentTimeMillis();
        assertTrue(f1.setLastModified(now - 3000L));
        assertTrue(f2.setLastModified(now - 2000L));
        assertTrue(f3.setLastModified(now - 1000L));

        MediaDiskCache.trimToLimit(dir);

        // 90 MiB total must drop to <= 64 MiB by deleting oldest first: f1 goes, f2/f3 stay.
        assertFalse(f1.exists());
        assertTrue(f2.exists());
        assertTrue(f3.exists());
        assertTrue(MediaDiskCache.committedBytes(dir) <= MediaDiskCache.MAX_DISK_CACHE_BYTES);
    }

    @Test
    public void duplicateUrlsProduceOneFile() {
        File dir = cacheDir();
        String networkUrl = "https://pbs.twimg.com/media/photo.jpg";
        String cacheUrl = "https://pbs.twimg.com/media/photo.jpg?format=jpg&name=small";
        byte[] data = bytes(2048, (byte) 9);

        assertTrue(MediaDiskCache.write(dir, networkUrl, data));
        // New writes use the canonical network key only.
        assertNull(MediaDiskCache.readEntry(dir, "https://example.com/unrelated"));
        assertArrayEquals(data, MediaDiskCache.read(dir, networkUrl, cacheUrl));

        List<File> committed = MediaDiskCache.committedFilesOldestFirst(dir);
        assertEquals(1, committed.size());

        // Legacy duplicate on disk stays readable through either key.
        assertTrue(MediaDiskCache.write(dir, cacheUrl, data));
        assertArrayEquals(data, MediaDiskCache.read(dir, networkUrl, cacheUrl));
        assertEquals(2, MediaDiskCache.committedFilesOldestFirst(dir).size());
    }

    @Test
    public void staleTmpRemoved() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        File stale = new File(dir, "abc123.tmp");
        File staleUnique = new File(dir, "abc123-1-2.tmp");
        try (FileOutputStream out = new FileOutputStream(stale)) {
            out.write(new byte[]{1, 2, 3});
        }
        try (FileOutputStream out = new FileOutputStream(staleUnique)) {
            out.write(new byte[]{4, 5});
        }
        byte[] data = bytes(512, (byte) 3);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/keep.jpg", data));

        MediaDiskCache.cleanup(dir);
        assertFalse(stale.exists());
        assertFalse(staleUnique.exists());
        assertArrayEquals(data, MediaDiskCache.readEntry(dir, "https://example.com/keep.jpg"));
    }

    @Test
    public void corruptEntriesDeleted() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        // Zero-byte entries are corrupt: reads treat them as misses and remove them.
        File zero = MediaDiskCache.fileForKey(dir, "https://example.com/zero.jpg");
        assertNotNull(zero);
        assertTrue(zero.createNewFile());
        assertNull(MediaDiskCache.readEntry(dir, "https://example.com/zero.jpg"));
        assertFalse(zero.exists());

        // Undecodable bytes are removed by the loader via deleteEntry, then refetched.
        byte[] corrupt = bytes(256, (byte) 11);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/corrupt.jpg", corrupt));
        MediaDiskCache.deleteEntry(dir, "https://example.com/corrupt.jpg");
        assertNull(MediaDiskCache.readEntry(dir, "https://example.com/corrupt.jpg"));

        byte[] fresh = bytes(256, (byte) 12);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/corrupt.jpg", fresh));
        assertArrayEquals(fresh, MediaDiskCache.readEntry(dir, "https://example.com/corrupt.jpg"));
    }

    @Test
    public void concurrentWritesLeaveCompleteFiles() throws Exception {
        File dir = cacheDir();
        int keys = 24;
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < keys; i++) urls.add("https://example.com/c" + i + ".jpg");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(keys);
        for (int i = 0; i < keys; i++) {
            final int index = i;
            pool.execute(() -> {
                try {
                    start.await();
                    MediaDiskCache.write(dir, urls.get(index), bytes(4096 + index, (byte) index));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        Set<String> names = new HashSet<>();
        for (int i = 0; i < keys; i++) {
            byte[] expected = bytes(4096 + i, (byte) i);
            byte[] actual = MediaDiskCache.readEntry(dir, urls.get(i));
            assertNotNull("missing key " + urls.get(i), actual);
            assertArrayEquals(expected, actual);
            File file = MediaDiskCache.fileForKey(dir, urls.get(i));
            assertNotNull(file);
            assertEquals((long) expected.length, file.length());
            assertTrue(names.add(file.getName()));
        }
        File[] leftovers = dir.listFiles((d, name) -> name.endsWith(".tmp"));
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }

    @Test
    public void failuresAreRecoverable() {
        assertNull(MediaDiskCache.read(null, "https://example.com/a", null));
        assertNull(MediaDiskCache.readEntry(null, "https://example.com/a"));
        assertNull(MediaDiskCache.read(cacheDir(), null, null));
        assertFalse(MediaDiskCache.write(null, "https://example.com/a", new byte[]{1}));
        assertFalse(MediaDiskCache.write(cacheDir(), null, new byte[]{1}));
        assertFalse(MediaDiskCache.write(cacheDir(), "https://example.com/a", null));
        assertFalse(MediaDiskCache.write(cacheDir(), "https://example.com/a", new byte[0]));
        MediaDiskCache.deleteEntry(null, "https://example.com/a");
        MediaDiskCache.deleteEntry(cacheDir(), null);
        MediaDiskCache.cleanup(null);
        MediaDiskCache.trimToLimit(null);
        MediaDiskCache.cleanup(cacheDir());
        assertEquals(0L, MediaDiskCache.committedBytes(cacheDir()));
    }

    @Test
    public void keysNeverBecomeFilenames() {
        String url = "https://pbs.twimg.com/media/EV/ery+large?name=small&format=jpg";
        String name = MediaDiskCache.fileNameForKey(url);
        assertNotNull(name);
        assertEquals(32, name.length());
        assertFalse(name.contains("/"));
        assertFalse(name.contains(":"));
        assertFalse(name.contains("?"));
        assertEquals(name, MediaDiskCache.fileNameForKey(url));
    }

    @Test
    public void statsReflectsWrites() {
        File dir = cacheDir();
        MediaDiskCache.Stats empty = MediaDiskCache.stats(dir);
        assertEquals(0, empty.entryCount);
        assertEquals(0L, empty.totalBytes);

        byte[] small = bytes(1024, (byte) 1);
        byte[] large = bytes(4096, (byte) 2);
        assertTrue(MediaDiskCache.write(dir, "https://example.com/s1.jpg", small));
        assertTrue(MediaDiskCache.write(dir, "https://example.com/s2.jpg", large));

        MediaDiskCache.Stats stats = MediaDiskCache.stats(dir);
        assertEquals(2, stats.entryCount);
        assertEquals(1024L + 4096L, stats.totalBytes);
        assertEquals(4096L, stats.largestBytes);
        assertTrue(stats.oldestModified > 0L);
        assertTrue(stats.newestModified >= stats.oldestModified);
        assertEquals(0, stats.tmpCount);
    }

    @Test
    public void clearRemovesAllEntries() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        assertTrue(MediaDiskCache.write(dir, "https://example.com/c1.jpg", bytes(512, (byte) 1)));
        new File(dir, "orphan.tmp").createNewFile();
        assertEquals(2, MediaDiskCache.clear(dir));
        assertEquals(0, MediaDiskCache.committedFilesOldestFirst(dir).size());
        assertEquals(0L, MediaDiskCache.committedBytes(dir));
        MediaDiskCache.Stats stats = MediaDiskCache.stats(dir);
        assertEquals(0, stats.entryCount);
        assertEquals(0, stats.tmpCount);
    }

    @Test
    public void overLimitTrimCountsEvictions() throws Exception {
        File dir = cacheDir();
        dir.mkdirs();
        long evictedBefore = MediaDiskCache.evictedEntries();
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/e1")), 30 * MIB);
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/e2")), 30 * MIB);
        sparse(new File(dir, MediaDiskCache.fileNameForKey("https://example.com/e3")), 30 * MIB);
        MediaDiskCache.trimToLimit(dir);
        assertTrue(MediaDiskCache.evictedEntries() > evictedBefore);
        assertTrue(MediaDiskCache.evictedBytes() > 0L);
        assertTrue(MediaDiskCache.committedBytes(dir) <= MediaDiskCache.MAX_DISK_CACHE_BYTES);
    }
}
