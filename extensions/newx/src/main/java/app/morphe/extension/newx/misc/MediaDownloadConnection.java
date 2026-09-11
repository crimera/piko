package app.morphe.extension.newx.misc;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;

/** HTTP media transport shared by native and inline clone downloads. */
final class MediaDownloadConnection {
    private MediaDownloadConnection() {}

    @FunctionalInterface
    interface ConnectionFactory {
        HttpURLConnection connect(URL url) throws IOException;
    }

    static HttpURLConnection open(String address, Map<String, String> headers, String mime,
            Consumer<String> diagnostic) throws IOException {
        return open(address, headers, mime, diagnostic, url -> (HttpURLConnection) url.openConnection());
    }

    static HttpURLConnection open(String address, Map<String, String> headers, String mime,
            Consumer<String> diagnostic, ConnectionFactory factory) throws IOException {
        URL original = new URL(address);
        try {
            return openFollowingRedirects(original, headers, factory);
        } catch (HttpStatusException exception) {
            URL fallback = exception.status == 404 ? imageFallback(original, mime) : null;
            if (fallback == null) throw exception;
            diagnostic.accept("image_fallback status=404 size=4096x4096");
            // Match the stock inline downloader's high-resolution fallback. Some images
            // have a usable size variant while their original-size endpoint returns 404.
            // Retry once, before creating any MediaStore item or reporting completion.
            return openFollowingRedirects(fallback, headers, factory);
        }
    }

    private static URL imageFallback(URL original, String mime) throws IOException {
        if (mime == null || !mime.startsWith("image/")
                || !original.getHost().equalsIgnoreCase("pbs.twimg.com")
                || !original.getPath().startsWith("/media/") || original.getQuery() == null) return null;
        String[] parameters = original.getQuery().split("&", -1);
        int sizeParameters = 0;
        int originalSize = -1;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].startsWith("name=")) sizeParameters++;
            if (parameters[i].equals("name=orig")) originalSize = i;
        }
        if (sizeParameters != 1 || originalSize < 0) return null;
        parameters[originalSize] = "name=4096x4096";
        String address = original.toExternalForm();
        return new URL(address.substring(0, address.indexOf('?') + 1)
                + String.join("&", parameters)
                + (original.getRef() == null ? "" : "#" + original.getRef()));
    }

    private static HttpURLConnection openFollowingRedirects(URL original, Map<String, String> headers,
            ConnectionFactory factory) throws IOException {
        URL current = original;
        for (int redirect = 0; redirect <= 5; redirect++) {
            HttpURLConnection connection = factory.connect(current);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (current.getHost().equalsIgnoreCase(original.getHost())
                    && current.getProtocol().equalsIgnoreCase(original.getProtocol())
                    && current.getPort() == original.getPort()) {
                for (var entry : headers.entrySet()) connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            int code;
            try { code = connection.getResponseCode(); }
            catch (IOException exception) { connection.disconnect(); throw exception; }
            if (code == 200) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (code < 300 || code > 399 || location == null) throw new HttpStatusException(code);
            URL next = new URL(current, location);
            if (!next.getProtocol().equals("https") && !next.getProtocol().equals("http")) {
                throw new IOException("Unsupported redirect protocol");
            }
            if (current.getProtocol().equals("https") && next.getProtocol().equals("http")) {
                throw new IOException("Insecure redirect");
            }
            current = next;
        }
        throw new IOException("Too many redirects");
    }

    private static final class HttpStatusException extends IOException {
        final int status;

        HttpStatusException(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }
}
