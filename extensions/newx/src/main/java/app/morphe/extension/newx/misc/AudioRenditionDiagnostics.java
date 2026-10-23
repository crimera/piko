package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Diagnostic only: logs the HLS playlist URI and how each rendition id matches the audio pattern
 * while the playlist is parsed. Nothing is changed, and nothing is logged unless piko logging is on.
 * Arguments are typed as Object or String so the patch can pass obfuscated values without binding to them.
 */
public final class AudioRenditionDiagnostics {
    private static final String LOG_PREFIX = "[PikoNewX][AudioDiag] ";

    private AudioRenditionDiagnostics() {
    }

    /** An actual DataSource.open attempt, not merely a rendition advertised by a master playlist. */
    public static void logRequest(Object dataSpec) {
        if (!NewXLogger.isLoggingEnabled()) {
            return;
        }
        String request = String.valueOf(dataSpec);
        if (!request.contains("video.twimg.com/")) {
            return;
        }
        NewXLogger.printInfo(() -> LOG_PREFIX + "open request=" + request);
    }

    /** Logs the playlist URI the parser was asked to load. */
    public static void logPlaylist(Object playlistUri) {
        if (!NewXLogger.isLoggingEnabled()) {
            return;
        }
        NewXLogger.printInfo(() -> LOG_PREFIX + "playlist uri=" + playlistUri);
    }

    /** Logs one rendition id and whether it matched the audio pattern (a non-null match result). */
    public static void logRendition(String renditionId, Object matchResult) {
        if (!NewXLogger.isLoggingEnabled()) {
            return;
        }
        boolean matchesAudioPattern = matchResult != null;
        NewXLogger.printInfo(() -> LOG_PREFIX + "rendition id=" + renditionId
                + " matchesAudioPattern=" + matchesAudioPattern);
    }
}
