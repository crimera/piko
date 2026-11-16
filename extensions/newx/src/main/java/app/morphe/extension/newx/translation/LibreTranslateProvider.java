package app.morphe.extension.newx.translation;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;

import app.morphe.extension.newx.translation.TranslatorConfig.CustomProvider;

public final class LibreTranslateProvider implements TranslationProvider {
    private final CustomProvider provider;

    public LibreTranslateProvider(CustomProvider provider) {
        this.provider = provider;
    }

    @Override
    public String id() {
        return provider.id;
    }

    @Override
    public String displayName() {
        return provider.name;
    }

    @Override
    public boolean supportsText() {
        return true;
    }

    @Override
    public TranslationResult translateText(
            String text,
            String sourceHint,
            String targetLanguage
    ) throws IOException {
        try {
            JSONObject body = new JSONObject()
                    .put("q", text)
                    .put("source", sourceHint == null ? "auto" : TranslatorConfig.primaryLanguage(sourceHint))
                    .put("target", TranslatorConfig.primaryLanguage(targetLanguage))
                    .put("format", "text");
            if (!provider.apiKey.isEmpty()) body.put("api_key", provider.apiKey);
            String response = TranslationHttp.postJson(
                    provider.url + "/translate",
                    Collections.emptyMap(),
                    body
            );
            return parseResponse(response);
        } catch (JSONException exception) {
            throw new IOException("Could not build the " + provider.name + " request", exception);
        }
    }

    @Override
    public TranslationResult translatePost(String postId, String postText, String targetLanguage) throws IOException {
        return translateText(postText, null, targetLanguage);
    }

    private TranslationResult parseResponse(String response) throws IOException {
        try {
            JSONObject json = new JSONObject(response);
            String text = json.optString("translatedText", "");
            if (text.isEmpty()) throw new IOException(provider.name + " returned no translation");
            JSONObject detected = json.optJSONObject("detectedLanguage");
            String source = detected == null ? null : detected.optString("language", null);
            return new TranslationResult(text, TranslatorConfig.normalizeLanguage(source));
        } catch (JSONException exception) {
            throw new IOException("Malformed " + provider.name + " response", exception);
        }
    }
}
