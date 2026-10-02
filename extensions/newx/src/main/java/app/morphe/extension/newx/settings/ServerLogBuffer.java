package app.morphe.extension.newx.settings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded, process-local storage for opt-in NewX server diagnostics. */
final class ServerLogBuffer {
    static final int MAX_ENTRIES = 128;
    static final int MAX_ENTRY_CHARS = 16 * 1024;
    static final int MAX_TOTAL_CHARS = 256 * 1024;
    private static final String TRUNCATION_SUFFIX = "\n[entry truncated]";

    private final Object lock = new Object();
    private final ArrayDeque<String> entries = new ArrayDeque<>();
    private int totalChars;

    void add(String entry) {
        if (entry == null || entry.isEmpty()) return;

        String boundedEntry = truncate(entry);
        synchronized (lock) {
            while (!entries.isEmpty()
                    && (entries.size() >= MAX_ENTRIES
                    || totalChars + boundedEntry.length() > MAX_TOTAL_CHARS)) {
                totalChars -= entries.removeFirst().length();
            }
            entries.addLast(boundedEntry);
            totalChars += boundedEntry.length();
        }
    }

    List<String> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    private static String truncate(String entry) {
        if (entry.length() <= MAX_ENTRY_CHARS) return entry;
        int contentLength = MAX_ENTRY_CHARS - TRUNCATION_SUFFIX.length();
        return entry.substring(0, contentLength) + TRUNCATION_SUFFIX;
    }
}
