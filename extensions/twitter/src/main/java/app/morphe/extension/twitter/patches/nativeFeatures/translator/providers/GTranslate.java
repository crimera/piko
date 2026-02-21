package app.morphe.extension.twitter.patches.nativeFeatures.translator.providers;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import app.morphe.extension.shared.StringRef;

public class GTranslate implements Translate {
    private static final String BASE_URL = "https://translate.googleapis.com/translate_a/single";
    private static final String HOST = "translate.google.com";
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:56.0) Gecko/20100101 Firefox/56.0";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Override
    public String getProviderName(){
        return "Google translator";
    }
    /**
     * Translate the given query to the specified language
     * @param tweetId Tweet Id in long
     * @param query The text to translate
     * @param toLang Target language code
     * @param callback Callback to handle translation result
     */
    @Override
    public void translate(final Long tweetId, final String query, final String toLang, final TranslationCallback callback) {
        new AsyncTask<Void, Void, String>() {
            private Exception error = null;

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    // Encode the query for URL
                    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());

                    // Construct the full URL
                    String fullUrl = String.format("%s?client=gtx&sl=auto&tl=%s&dj=1&dt=t&dt=bd&source=popup5&q=%s",
                            BASE_URL, toLang, encodedQuery);

                    // Create connection
                    URL url = new URL(fullUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    // Set request headers
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Host", HOST);
                    connection.setRequestProperty("User-Agent", USER_AGENT);
                    connection.setRequestProperty("Accept", ACCEPT);

                    // Read the response
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse the JSON response
                    return parseTranslationResponse(response.toString());
                } catch (Exception e) {
                    error = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (error != null) {
                    callback.onError(error);
                } else {
                    callback.onTranslationComplete(result);
                }
            }
        }.execute();
    }

    /**
     * Parse the Google Translate JSON response
     * @param jsonResponse Raw JSON response from Google Translate
     * @return Translated text
     */
    private String parseTranslationResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray sentences = jsonObject.getJSONArray("sentences");

            StringBuilder translatedText = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONObject sentence = sentences.getJSONObject(i);
                if (sentence.has("trans")) {
                    translatedText.append(sentence.getString("trans"));
                }
            }

            return translatedText.toString();
        } catch (Exception e) {
            return StringRef.str("translate_tweet_error") +": "+ e.getMessage();
        }
    }
}