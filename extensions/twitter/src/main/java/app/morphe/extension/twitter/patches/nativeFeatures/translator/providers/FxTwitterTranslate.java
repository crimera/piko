package app.morphe.extension.twitter.patches.nativeFeatures.translator.providers;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;
import app.morphe.extension.shared.StringRef;

/*
* Credits:  https://github.com/FxEmbed/FxEmbed
*/
public class FxTwitterTranslate implements Translate {
    private static final String BASE_URL = "https://api.fxtwitter.com/piko/status/";
    private String PROVIDER_NAME = "FxTwitter";

    @Override
    public String getProviderName(){
        return this.PROVIDER_NAME;
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

                    // Construct the full URL
                    String fullUrl = BASE_URL+String.valueOf(tweetId)+"/"+toLang;

                    // Create connection
                    URL url = new URL(fullUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    // Set request headers
                    connection.setRequestMethod("GET");

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
     * Parse the JSON response
     * @param jsonResponse Raw JSON response from Google Translate
     * @return Translated text
     */
    private String parseTranslationResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (jsonObject.has("tweet")) {
                JSONObject tweetObj = jsonObject.getJSONObject("tweet");
                if (tweetObj.has("translation")) {
                    JSONObject translationObj = tweetObj.getJSONObject("translation");
                    this.PROVIDER_NAME = translationObj.getString("provider");
                    return translationObj.getString("text");
                }
            }
            throw new Exception("Translation failed.");
        } catch (Exception e) {
            return StringRef.str("translate_tweet_error") +": "+ e.getMessage();
        }
    }
}