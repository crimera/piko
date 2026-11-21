package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Guards crash report assembly: a share/copy payload that exceeds the Binder
 * transaction limit fails silently from the notification, and a report missing
 * its device/app sections is useless for debugging.
 */
public final class NewXCrashHandlerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportContainsAllSections() {
        String report = NewXCrashHandler.formatReport(
                "Brand: Test", "Package Name: com.test", "main", "java.lang.RuntimeException: boom");

        assertTrue(report.contains("--- Device Info ---"));
        assertTrue(report.contains("Brand: Test"));
        assertTrue(report.contains("--- App Info ---"));
        assertTrue(report.contains("Package Name: com.test"));
        assertTrue(report.contains("Thread: main"));
        assertTrue(report.contains("--- Stack Trace ---"));
        assertTrue(report.contains("java.lang.RuntimeException: boom"));
    }

    @Test
    public void oversizedStackTraceIsBounded() {
        String oversized = "x".repeat(NewXCrashHandler.MAX_REPORT_CHARS + 1000);

        String report = NewXCrashHandler.formatReport("device", "app", "main", oversized);

        assertTrue(report.length() <= NewXCrashHandler.MAX_REPORT_CHARS + 100);
        assertTrue(report.endsWith("[text truncated]"));
    }

    @Test
    public void boundTextKeepsShortValuesIntact() {
        assertEquals("", NewXCrashHandler.boundText(null, 10));
        assertEquals("short", NewXCrashHandler.boundText("short", 10));
        assertEquals("exact", NewXCrashHandler.boundText("exact", 5));
    }

    @Test(expected = AssertionError.class)
    public void testCrashThrows() {
        NewXCrashHandler.testCrash("test");
    }

    @Test
    public void testCrashEscapesExceptionCatch() {
        try {
            NewXCrashHandler.testCrash("test");
        } catch (Exception exception) {
            throw new AssertionError("testCrash must escape catch (Exception): " + exception);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError("testCrash did not throw");
    }

    @Test
    public void shareTextReadsReportFile() throws Exception {
        File report = temporaryFolder.newFile("piko-crash-test.txt");
        Files.write(report.toPath(), "crash body".getBytes(StandardCharsets.UTF_8));

        assertEquals("crash body", NewXCrashHandler.shareText(report));
    }

    @Test
    public void shareTextIsBounded() throws Exception {
        File report = temporaryFolder.newFile("piko-crash-big.txt");
        byte[] oversized = new byte[NewXCrashHandler.MAX_SHARE_CHARS + 1000];
        java.util.Arrays.fill(oversized, (byte) 'x');
        Files.write(report.toPath(), oversized);

        String text = NewXCrashHandler.shareText(report);

        assertTrue(text.endsWith("[text truncated]"));
        assertTrue(text.length() <= NewXCrashHandler.MAX_SHARE_CHARS + 100);
    }

    @Test
    public void shareTextMissingFileIsNull() {
        assertNull(NewXCrashHandler.shareText(new File("does-not-exist.txt")));
    }

    @Test
    public void nullInputsFallBackToUnavailable() {
        String report = NewXCrashHandler.formatReport(null, null, null, null);

        assertTrue(report.contains("Unavailable"));
        assertTrue(report.contains("Thread: unknown"));
    }
}
