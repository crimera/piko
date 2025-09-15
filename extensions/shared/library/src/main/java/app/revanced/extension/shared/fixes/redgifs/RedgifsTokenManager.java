package app.revanced.extension.shared.fixes.redgifs;

import static app.revanced.extension.shared.requests.Route.Method.GET;

import androidx.annotation.GuardedBy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import app.revanced.extension.shared.requests.Requester;


/**
 * Manages Redgifs token lifecycle.
 */
public class RedgifsTokenManager {
    public static class RedgifsToken {
        // Expire after 23 hours to provide some breathing room
        private static final long EXPIRY_SECONDS = 23 * 60 * 60;

        private final String accessToken;
        private final long refreshTimeInSeconds;

        public RedgifsToken(String accessToken, long refreshTime) {
            this.accessToken = accessToken;
            this.refreshTimeInSeconds = refreshTime;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public long getExpiryTimeInSeconds() {
            return refreshTimeInSeconds + EXPIRY_SECONDS;
        }

        public boolean isValid() {
            if (accessToken == null) return false;
            return getExpiryTimeInSeconds() >= System.currentTimeMillis() / 1000;
        }
    }
    public static final String REDGIFS_API_HOST = "https://api.redgifs.com";
    private static final String GET_TEMPORARY_TOKEN = REDGIFS_API_HOST + "/v2/auth/temporary";
    @GuardedBy("itself")
    private static final Map<String, RedgifsToken> tokenMap = new HashMap<>();

    private static String getToken(String userAgent) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(GET_TEMPORARY_TOKEN).openConnection();
        connection.setFixedLengthStreamingMode(0);
        connection.setRequestMethod(GET.name());
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setUseCaches(false);

        JSONObject responseObject = Requester.parseJSONObject(connection);
        return responseObject.getString("token");
    }

    public static RedgifsToken refreshToken(String userAgent) throws IOException, JSONException {
        synchronized(tokenMap) {
            // Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67
            RedgifsToken token = tokenMap.get(userAgent);
            if (token != null && token.isValid()) {
                return token;
            }

            // Copy user agent from original request if present because Redgifs verifies
            // that the user agent in subsequent requests matches the one in the OAuth token.
            String accessToken = getToken(userAgent);
            long refreshTime = System.currentTimeMillis() / 1000;
            token = new RedgifsToken(accessToken, refreshTime);
            tokenMap.put(userAgent, token);
            return token;
        }
    }

    public static String getEmulatedOAuthResponseBody(RedgifsToken token) throws JSONException {
        // Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67
        JSONObject responseObject = new JSONObject();
        responseObject.put("access_token", token.accessToken);
        responseObject.put("expiry_time", token.getExpiryTimeInSeconds() - (System.currentTimeMillis() / 1000));
        responseObject.put("scope", "read");
        responseObject.put("token_type", "Bearer");
        return responseObject.toString();
    }
}
