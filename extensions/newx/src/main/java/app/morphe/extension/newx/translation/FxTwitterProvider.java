package app.morphe.extension.newx.translation;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;

public final class FxTwitterProvider implements TranslationProvider {
    private static final String STATUS_ENDPOINT = "https://api.fxtwitter.com/i/status/";

    @Override
    public String id() {
        return TranslatorConfig.FXTWITTER_ID;
    }

    @Override
    public String displayName() {
        return "FxTwitter";
    }

    @Override
    public boolean supportsText() {
        return false;
    }

    @Override
    public TranslationResult translateText(
            String text,
            String sourceHint,
            String targetLanguage
    ) throws IOException {
        throw new IOException("FxTwitter can only translate whole posts");
    }

    @Override
    public TranslationResult translatePost(String postId, String postText, String targetLanguage) throws IOException {
        if (postId == null || postId.trim().isEmpty()) throw new IOException("FxTwitter needs the post ID");
        String language = TranslatorConfig.primaryLanguage(targetLanguage);
        String response = TranslationHttp.get(
                STATUS_ENDPOINT + postId.trim() + "/" + language,
                Collections.emptyMap()
        );
        return parseResponse(response);
    }

    private static TranslationResult parseResponse(String response) throws IOException {
        try {
            JSONObject tweet = new JSONObject(response).optJSONObject("tweet");
            JSONObject translation = tweet == null ? null : tweet.optJSONObject("translation");
            String text = translation == null ? "" : translation.optString("text", "");
            if (text.isEmpty()) throw new IOException("FxTwitter returned no translation");
            return new TranslationResult(
                    text,
                    TranslatorConfig.normalizeLanguage(translation.optString("source_lang", null))
            );
        } catch (JSONException exception) {
            throw new IOException("Malformed FxTwitter response", exception);
        }
    }
}
