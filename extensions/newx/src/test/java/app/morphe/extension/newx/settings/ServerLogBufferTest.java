package app.morphe.extension.newx.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class ServerLogBufferTest {
    @Test
    public void snapshotPreservesInsertionOrder() {
        ServerLogBuffer buffer = new ServerLogBuffer();

        buffer.add("first");
        buffer.add("second");

        assertEquals(List.of("first", "second"), buffer.snapshot());
    }

    @Test
    public void oldestEntriesAreEvictedAtCapacity() {
        ServerLogBuffer buffer = new ServerLogBuffer();
        for (int index = 0; index < ServerLogBuffer.MAX_ENTRIES + 1; index++) {
            buffer.add("entry-" + index);
        }

        List<String> snapshot = buffer.snapshot();
        assertEquals(ServerLogBuffer.MAX_ENTRIES, snapshot.size());
        assertFalse(snapshot.contains("entry-0"));
        assertTrue(snapshot.contains("entry-" + ServerLogBuffer.MAX_ENTRIES));
    }

    @Test
    public void totalCharacterLimitEvictsOldEntries() {
        ServerLogBuffer buffer = new ServerLogBuffer();
        String entry = "x".repeat(ServerLogBuffer.MAX_ENTRY_CHARS);
        int entryCount = ServerLogBuffer.MAX_TOTAL_CHARS / ServerLogBuffer.MAX_ENTRY_CHARS + 1;

        for (int index = 0; index < entryCount; index++) {
            buffer.add(entry);
        }

        List<String> snapshot = buffer.snapshot();
        int totalChars = snapshot.stream().mapToInt(String::length).sum();
        assertTrue(totalChars <= ServerLogBuffer.MAX_TOTAL_CHARS);
        assertEquals(ServerLogBuffer.MAX_TOTAL_CHARS / ServerLogBuffer.MAX_ENTRY_CHARS, snapshot.size());
    }

    @Test
    public void oversizedEntriesAreTruncated() {
        ServerLogBuffer buffer = new ServerLogBuffer();
        String oversized = "x".repeat(ServerLogBuffer.MAX_ENTRY_CHARS + 100);

        buffer.add(oversized);

        String stored = buffer.snapshot().get(0);
        assertEquals(ServerLogBuffer.MAX_ENTRY_CHARS, stored.length());
        assertTrue(stored.endsWith("[entry truncated]"));
    }
}
