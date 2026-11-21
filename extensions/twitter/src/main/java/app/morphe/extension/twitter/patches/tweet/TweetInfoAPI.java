/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.tweet;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import app.morphe.extension.crimera.PikoUtils;

public class TweetInfoAPI {

    private static JSONObject fetchStatusById(String id) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        // empty object returned on error, because null would be indistinguishable from no response at all
        JSONObject empty = new JSONObject();

        if(!app.morphe.extension.shared.Utils.isNetworkConnected()){
            return empty;
        }
        try {
            String apiUrl = "https://api.fxtwitter.com/x/status/" + id;
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 seconds
            connection.setReadTimeout(5000);    // 5 seconds

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                PikoUtils.logger("HTTP error code when fetching tweet source: " + responseCode);
                return empty;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            return new JSONObject(responseBuilder.toString());

        } catch (Exception e) {
            PikoUtils.logger(e);
            return empty;

        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    /*
     * One request per post, kept by post id: the fetch runs off the lock (it used to hold the lock the UI waits on for
     * up to 10 s), and a post never shows another post's source (one shared result used to be reused). The UI waits
     * at most WAIT_MS for a fetch it started; a late answer is used the next time the post is shown.
     */
    private static final long WAIT_MS = 1_500;
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>> REQUESTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService FETCHER = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "piko-tweet-source");
        t.setDaemon(true);
        return t;
    });

    public static void sendRequest(long id) {
        String tweetId = String.valueOf(id);
        REQUESTS.computeIfAbsent(tweetId, key -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            JSONObject response = fetchStatusById(key);
            JSONObject tweet = response.optJSONObject("tweet");
            String src = tweet == null ? null : tweet.optString("source", null);
            if (src == null) REQUESTS.remove(key);   // failed: asked again next time
            return src;
        }, FETCHER));
        if (REQUESTS.size() > 2000) REQUESTS.clear();
    }

    public static String getTweetSource(long id) {
        String tweetId = String.valueOf(id);
        try {
            java.util.concurrent.CompletableFuture<String> f = REQUESTS.get(tweetId);
            if (f == null) {
                if (!app.morphe.extension.shared.Utils.isNetworkConnected()) return null;
                sendRequest(id);
                f = REQUESTS.get(tweetId);
                if (f == null) return null;
            }
            return f.get(WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        } catch (Exception e) {
            PikoUtils.logger(e);
            return null;
        }
    }
}
