package app.morphe.extension.shared.requests;

import app.morphe.extension.shared.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Requester {
    private Requester() {
    }

    public static HttpURLConnection getConnectionFromRoute(String apiUrl, Route route, String... params) throws IOException {
        return getConnectionFromCompiledRoute(apiUrl, route.compile(params));
    }

    public static HttpURLConnection getConnectionFromCompiledRoute(String apiUrl, Route.CompiledRoute route) throws IOException {
        String url = apiUrl + route.getCompiledRoute();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        // Request data is in the URL parameters and no body is sent.
        // The calling code must set a length if using a request body.
        connection.setFixedLengthStreamingMode(0);
        connection.setRequestMethod(route.getMethod().name());
        String agentString = System.getProperty("http.agent")
                + "; Morphe/" + Utils.getAppVersionName()
                + " (" + Utils.getPatchesReleaseVersion() + ")";
        connection.setRequestProperty("User-Agent", agentString);

        return connection;
    }

    /**
     * Parse the {@link HttpURLConnection}, and closes the underlying InputStream.
     */
    private static String parseInputStreamAndClose(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
                jsonBuilder.append('\n');
            }
            return jsonBuilder.toString();
        }
    }

    /**
     * Parse the {@link HttpURLConnection} response as a String.
     * This does not close the url connection. If further requests to this host are unlikely
     * in the near future, then instead use {@link #parseStringAndDisconnect(HttpURLConnection)}.
     */
    public static String parseString(HttpURLConnection connection) throws IOException {
        return parseInputStreamAndClose(connection.getInputStream());
    }

    /**
     * Parse the {@link HttpURLConnection} response as a String, and disconnect.
     *
     * <b>Should only be used if other requests to the server in the near future are unlikely</b>
     *
     * @see #parseString(HttpURLConnection)
     */
    public static String parseStringAndDisconnect(HttpURLConnection connection) throws IOException {
        String result = parseString(connection);
        connection.disconnect();
        return result;
    }

    /**
     * Parse the {@link HttpURLConnection} error stream as a String.
     * If the server sent no error response data, this returns an empty string.
     */
    public static String parseErrorString(HttpURLConnection connection) throws IOException {
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            return "";
        }
        return parseInputStreamAndClose(errorStream);
    }

    /**
     * Parse the {@link HttpURLConnection} error stream as a String, and disconnect.
     * If the server sent no error response data, this returns an empty string.
     *
     * Should only be used if other requests to the server are unlikely in the near future.
     *
     * @see #parseErrorString(HttpURLConnection)
     */
    public static String parseErrorStringAndDisconnect(HttpURLConnection connection) throws IOException {
        String result = parseErrorString(connection);
        connection.disconnect();
        return result;
    }

    /**
     * Parse the {@link HttpURLConnection} response into a JSONObject.
     * This does not close the url connection. If further requests to this host are unlikely
     * in the near future, then instead use {@link #parseJSONObjectAndDisconnect(HttpURLConnection)}.
     */
    public static JSONObject parseJSONObject(HttpURLConnection connection) throws JSONException, IOException {
        return new JSONObject(parseString(connection));
    }

    /**
     * Parse the {@link HttpURLConnection}, close the underlying InputStream, and disconnect.
     *
     * <b>Should only be used if other requests to the server in the near future are unlikely</b>
     *
     * @see #parseJSONObject(HttpURLConnection)
     */
    public static JSONObject parseJSONObjectAndDisconnect(HttpURLConnection connection) throws JSONException, IOException {
        JSONObject object = parseJSONObject(connection);
        connection.disconnect();
        return object;
    }

    /**
     * Parse the {@link HttpURLConnection}, and closes the underlying InputStream.
     * This does not close the url connection. If further requests to this host are unlikely
     * in the near future, then instead use {@link #parseJSONArrayAndDisconnect(HttpURLConnection)}.
     */
    public static JSONArray parseJSONArray(HttpURLConnection connection) throws JSONException, IOException  {
        return new JSONArray(parseString(connection));
    }

    /**
     * Parse the {@link HttpURLConnection}, close the underlying InputStream, and disconnect.
     *
     * <b>Should only be used if other requests to the server in the near future are unlikely</b>
     *
     * @see #parseJSONArray(HttpURLConnection)
     */
    public static JSONArray parseJSONArrayAndDisconnect(HttpURLConnection connection) throws JSONException, IOException  {
        JSONArray array = parseJSONArray(connection);
        connection.disconnect();
        return array;
    }

}