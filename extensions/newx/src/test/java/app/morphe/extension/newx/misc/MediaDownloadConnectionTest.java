package app.morphe.extension.newx.misc;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MediaDownloadConnectionTest {
    private static final String IMAGE = "https://pbs.twimg.com/media/fixture.jpg?format=jpg&name=orig";

    @Test public void missingOriginalRetriesLargeImageAndReturnsItsBytes() throws Exception {
        Script script = new Script(404, 200);
        List<String> events = new ArrayList<>();
        HttpURLConnection result = MediaDownloadConnection.open(
                IMAGE, Map.of(), "image/jpeg", events::add, script::connect);
        assertEquals(List.of(IMAGE, IMAGE.replace("name=orig", "name=4096x4096")), script.requests);
        assertTrue(script.connections.get(0).disconnected);
        assertFalse(script.connections.get(1).disconnected);
        assertArrayEquals(new byte[]{1, 2, 3}, result.getInputStream().readAllBytes());
        assertEquals(List.of("image_fallback status=404 size=4096x4096"), events);
    }

    @Test public void availableOriginalRemainsOriginalQuality() throws Exception {
        Script script = new Script(200);
        List<String> events = new ArrayList<>();
        MediaDownloadConnection.open(IMAGE, Map.of(), "image/jpeg", events::add, script::connect);
        assertEquals(List.of(IMAGE), script.requests);
        assertTrue(events.isEmpty());
    }

    @Test public void missingFallbackFailsAfterOnlyOneRetry() {
        Script script = new Script(404, 404);
        IOException error = assertThrows(IOException.class, () -> MediaDownloadConnection.open(
                IMAGE, Map.of(), "image/jpeg", event -> {}, script::connect));
        assertEquals("HTTP 404", error.getMessage());
        assertEquals(2, script.requests.size());
        assertTrue(script.connections.stream().allMatch(connection -> connection.disconnected));
    }

    @Test public void authorizationAndServerErrorsAreNotRetriedAsMissingImages() {
        for (int status : new int[]{401, 403, 429, 500}) {
            Script script = new Script(status);
            assertThrows(IOException.class, () -> MediaDownloadConnection.open(
                    IMAGE, Map.of(), "image/jpeg", event -> {}, script::connect));
            assertEquals(1, script.requests.size());
        }
    }

    @Test public void unrelatedHostsPathsAndSizesKeepTheirOriginalError() {
        for (String address : new String[]{
                "https://example.test/media/fixture.jpg?format=jpg&name=orig",
                "https://pbs.twimg.com.example.test/media/fixture.jpg?name=orig",
                "https://pbs.twimg.com/profile_images/fixture.jpg?name=orig",
                "https://pbs.twimg.com/media/fixture.jpg?name=small",
                "https://pbs.twimg.com/media/fixture.jpg?name=original",
                "https://pbs.twimg.com/media/fixture.jpg?name=orig&name=small"}) {
            Script script = new Script(404);
            assertThrows(IOException.class, () -> MediaDownloadConnection.open(
                    address, Map.of(), "image/jpeg", event -> {}, script::connect));
            assertEquals(address, 1, script.requests.size());
        }
    }

    @Test public void videoAndUnknownMimeDoNotUseImageFallback() {
        for (String mime : new String[]{"video/mp4", "application/octet-stream", null}) {
            Script script = new Script(404);
            assertThrows(IOException.class, () -> MediaDownloadConnection.open(
                    IMAGE, Map.of(), mime, event -> {}, script::connect));
            assertEquals(1, script.requests.size());
        }
    }

    @Test public void fallbackPreservesUnrelatedEncodedQueryParameters() throws Exception {
        String address = IMAGE + "&test=a%2Fb%26c#fragment";
        Script script = new Script(404, 200);
        MediaDownloadConnection.open(address, Map.of(), "image/jpeg", event -> {}, script::connect);
        assertEquals(address.replace("name=orig", "name=4096x4096"), script.requests.get(1));
    }

    @Test public void crossOriginRedirectDoesNotReceiveOriginalHeaders() throws Exception {
        Script script = new Script(302, 200);
        script.location = "https://other.example.test/fixture.jpg";
        MediaDownloadConnection.open(IMAGE, Map.of("Authorization", "fixture"), "image/jpeg",
                event -> {}, script::connect);
        assertEquals("fixture", script.connections.get(0).getRequestProperty("Authorization"));
        assertNull(script.connections.get(1).getRequestProperty("Authorization"));
    }

    @Test public void httpsDowngradeRemainsRejected() {
        Script script = new Script(302);
        script.location = "http://pbs.twimg.com/media/fixture.jpg";
        IOException error = assertThrows(IOException.class, () -> MediaDownloadConnection.open(
                IMAGE, Map.of(), "image/jpeg", event -> {}, script::connect));
        assertEquals("Insecure redirect", error.getMessage());
        assertEquals(1, script.requests.size());
    }

    private static final class Script {
        final int[] responses;
        final List<String> requests = new ArrayList<>();
        final List<Response> connections = new ArrayList<>();
        String location;

        Script(int... responses) { this.responses = responses; }

        HttpURLConnection connect(URL url) throws IOException {
            if (requests.size() >= responses.length) throw new IOException("Unexpected extra request");
            Response response = new Response(url, responses[requests.size()], location);
            requests.add(url.toExternalForm());
            connections.add(response);
            return response;
        }
    }

    private static final class Response extends HttpURLConnection {
        final int status;
        final String location;
        boolean disconnected;

        Response(URL url, int status, String location) {
            super(url);
            this.status = status;
            this.location = location;
        }

        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[]{1, 2, 3}); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
}
