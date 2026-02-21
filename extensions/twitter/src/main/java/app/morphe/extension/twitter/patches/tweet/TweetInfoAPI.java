package app.morphe.extension.twitter.patches.tweet;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import app.morphe.extension.twitter.Utils;

public class TweetInfoAPI {
    private static JSONObject responseObj = null;
    private static final JSONObject cache = new JSONObject();
    // Lock is used so that getTweetSource waits till request is fetched.
    private static final Object lock = new Object();

    private static JSONObject fetchStatusById(String id) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        if(!app.morphe.extension.shared.Utils.isNetworkConnected()){
            return null;
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
                throw new Exception("HTTP error code: " + responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            return new JSONObject(responseBuilder.toString());

        } catch (Exception e) {
            Utils.logger(e);
            return null;

        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public static void sendRequest(long id) {
        String tweetId = String.valueOf(id);

        synchronized (lock) {
            if (!cache.has(tweetId)) {
                new Thread(() -> {
                    synchronized (lock) {
                        responseObj = fetchStatusById(tweetId);
                        lock.notifyAll();
                    }
                }).start();
            }
        }
    }

    public static String getTweetSource(long id) {
        String src = null;
        String tweetId = String.valueOf(id);
        try {
            if(app.morphe.extension.shared.Utils.isNetworkConnected()){
            synchronized (lock) {
                if (cache.has(tweetId)) {
                    src = cache.optString(tweetId);
                } else {
                    while (responseObj == null) {
                        try {
                            lock.wait();
                        }catch (InterruptedException e){
                            Thread.currentThread().interrupt();
                            Utils.logger("Interrupted while waiting: " + e.getMessage());
                        }
                    }

                    if (responseObj != null && responseObj.has("tweet")) {
                        src = responseObj.optJSONObject("tweet").optString("source");
                        if(src!=null) cache.put(tweetId, src);
                    }
                }
            }
            }
        }catch(Exception e){
            Utils.logger(e);
        }
        return src;
    }
}
