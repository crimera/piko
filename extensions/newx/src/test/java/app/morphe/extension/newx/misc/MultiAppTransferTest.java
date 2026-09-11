package app.morphe.extension.newx.misc;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class MultiAppTransferTest {
    @Test public void unselectedPatchDoesNotEnterAndroidRouting() {
        // JVM Android methods throw: a false patch flag must short-circuit before
        // querying device identity, including when inline helpers are loaded.
        assertFalse(MultiAppDownloads.isPatchApplied());
        assertFalse(MultiAppDownloads.isNeeded());
    }
    @Test public void onlyCloneProfileUsesCompatibilityPath() {
        assertTrue(MultiAppTransfer.useAppProcess(99911898, 36, "OnePlus"));
        assertTrue(MultiAppTransfer.useAppProcess(99911898, 36, "OPPO"));
        assertFalse(MultiAppTransfer.useAppProcess(11898, 36, "OnePlus"));
        assertFalse(MultiAppTransfer.useAppProcess(1011898, 36, "OnePlus"));
        assertFalse(MultiAppTransfer.useAppProcess(99911898, 28, "OnePlus"));
        assertTrue(MultiAppTransfer.useAppProcess(99911000, 37, "Xiaomi"));
        assertFalse(MultiAppTransfer.useAppProcess(11000, 37, "Xiaomi"));
        assertFalse(MultiAppTransfer.useAppProcess(99911000, 37, "Google"));
    }
    @Test public void streamsCompleteBytesAndProgress() throws Exception {
        byte[] data = new byte[160000];
        for (int i=0;i<data.length;i++) data[i]=(byte)(i%251);
        var output = new ByteArrayOutputStream();
        var progress = new AtomicLong();
        assertEquals(data.length, MultiAppTransfer.copy(new ByteArrayInputStream(data), output,
                data.length, progress::set));
        assertArrayEquals(data, output.toByteArray());
        assertEquals(data.length, progress.get());
    }
    @Test public void truncatedResponseDoesNotReportSuccess() {
        assertThrows(IOException.class, () -> MultiAppTransfer.copy(
                new ByteArrayInputStream(new byte[7]), new ByteArrayOutputStream(), 10, n -> {}));
    }
    @Test public void unknownLengthStillRejectsEmptyMedia() {
        assertThrows(IOException.class, () -> MultiAppTransfer.copy(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), -1, n -> {}));
    }
    @Test public void namesCannotEscapeDestination() {
        assertEquals("clip.mp4", MultiAppTransfer.fileName("clip.mp4"));
        assertThrows(IllegalArgumentException.class, () -> MultiAppTransfer.fileName("../clip.mp4"));
        assertThrows(IllegalArgumentException.class, () -> MultiAppTransfer.fileName("a\\clip.mp4"));
        assertThrows(IllegalArgumentException.class, () -> MultiAppTransfer.fileName(".."));
    }
}
