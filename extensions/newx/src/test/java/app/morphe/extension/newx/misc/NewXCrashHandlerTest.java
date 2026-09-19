package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards crash report assembly: a share/copy payload that exceeds the Binder
 * transaction limit fails silently from the notification, and a report missing
 * its device/app sections is useless for debugging.
 */
public final class NewXCrashHandlerTest {
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

    @Test
    public void nullInputsFallBackToUnavailable() {
        String report = NewXCrashHandler.formatReport(null, null, null, null);

        assertTrue(report.contains("Unavailable"));
        assertTrue(report.contains("Thread: unknown"));
    }
}
