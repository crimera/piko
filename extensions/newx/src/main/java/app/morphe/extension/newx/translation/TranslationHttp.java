package app.morphe.extension.newx.translation;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP for the translation providers. Failures name only the host, so request URLs and
 * credentials never reach exception messages.
 */
final class TranslationHttp {
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    // LLM-backed endpoints answer much more slowly than the translate services.
    private static final int POST_READ_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;
    private static final int ERROR_READ_CHARS = 1024;
    private static final int MAX_ERROR_CHARS = 200;

    private TranslationHttp() {
    }

    static String get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open("GET", url, headers, READ_TIMEOUT_MILLIS);
        try {
            return read(connection, headers);
        } finally {
            connection.disconnect();
        }
    }

    static String postJson(String url, Map<String, String> headers, JSONObject body) throws IOException {
        return post(url, headers, "application/json", body.toString(), POST_READ_TIMEOUT_MILLIS);
    }

    static String postForm(String url, Map<String, String> headers, Map<String, String> form) throws IOException {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> field : form.entrySet()) {
            if (encoded.length() > 0) encoded.append('&');
            encoded.append(URLEncoder.encode(field.getKey(), UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(field.getValue(), UTF_8));
        }
        return post(url, headers, "application/x-www-form-urlencoded", encoded.toString(), READ_TIMEOUT_MILLIS);
    }

    private static String post(
            String url,
            Map<String, String> headers,
            String contentType,
            String body,
            int readTimeoutMillis
    ) throws IOException {
        HttpURLConnection connection = open("POST", url, headers, readTimeoutMillis);
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            return read(connection, headers);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(
            String method,
            String url,
            Map<String, String> headers,
            int readTimeoutMillis
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(readTimeoutMillis);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        return connection;
    }

    private static String read(HttpURLConnection connection, Map<String, String> headers) throws IOException {
        int status = connection.getResponseCode();
        if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
            throw new IOException(connection.getURL().getHost() + " returned HTTP " + status
                    + ": " + errorBody(connection, headers));
        }
        return readBody(connection.getInputStream());
    }

    private static String errorBody(HttpURLConnection connection, Map<String, String> headers) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[ERROR_READ_CHARS];
            int count = reader.read(buffer);
            if (count < 0) return "";
            // Redact before truncating so a credential cannot survive by straddling the cut.
            String body = redactCredential(new String(buffer, 0, count), headers);
            return body.length() > MAX_ERROR_CHARS ? body.substring(0, MAX_ERROR_CHARS) : body;
        } catch (IOException exception) {
            return "";
        }
    }

    private static String redactCredential(String text, Map<String, String> headers) {
        String authorization = headers.get("Authorization");
        if (authorization == null) return text;
        String credential = authorization.substring(authorization.lastIndexOf(' ') + 1);
        return credential.isEmpty() ? text : text.replace(credential, "[redacted]");
    }

    private static String readBody(InputStream stream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (response.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Translation response exceeded the size limit");
                }
                response.append(buffer, 0, count);
            }
        }
        return response.toString();
    }
}
