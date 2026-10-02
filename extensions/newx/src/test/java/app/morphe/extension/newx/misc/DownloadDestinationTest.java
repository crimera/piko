package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Guards the retry classifier that the 50-300MB video failures exposed: a reset
 * mid-stream ({@link SocketException}) must resume, while dead links and lost
 * folders must fail closed without burning retries or clearing the folder.
 */
public final class DownloadDestinationTest {
    @Test
    public void connectionAbortRetries() {
        assertTrue(DownloadDestination.isRetryable(
                new SocketException("Software caused connection abort")));
    }

    @Test
    public void transientDnsFlapRetries() {
        assertTrue(DownloadDestination.isRetryable(
                new UnknownHostException("Unable to resolve host \"video.twimg.com\"")));
    }

    @Test
    public void stallAndShortReadRetry() {
        assertTrue(DownloadDestination.isRetryable(new SocketTimeoutException()));
        assertTrue(DownloadDestination.isRetryable(new IOException("Short read")));
        assertTrue(DownloadDestination.isRetryable(new IOException("unexpected end of stream")));
    }

    @Test
    public void sickServersRetryDeadLinksDoNot() {
        assertTrue(DownloadDestination.isRetryable(
                new DownloadDestination.HttpStatusException(500, "https://video.twimg.com/x.mp4")));
        assertTrue(DownloadDestination.isRetryable(
                new DownloadDestination.HttpStatusException(429, "https://video.twimg.com/x.mp4")));
        assertFalse(DownloadDestination.isRetryable(
                new DownloadDestination.HttpStatusException(403, "https://video.twimg.com/x.mp4")));
        assertFalse(DownloadDestination.isRetryable(
                new DownloadDestination.HttpStatusException(404, "https://video.twimg.com/x.mp4")));
    }

    @Test
    public void destinationLossNeverRetries() {
        assertFalse(DownloadDestination.isRetryable(new FileNotFoundException()));
        assertFalse(DownloadDestination.isRetryable(new SecurityException()));
        assertFalse(DownloadDestination.isRetryable(
                new IOException(new FileNotFoundException())));
        assertFalse(DownloadDestination.isRetryable(null));
    }

    @Test
    public void unexpectedRuntimeFailuresDoNotRetry() {
        assertFalse(DownloadDestination.isRetryable(new IllegalStateException()));
    }
}
