package app.morphe.extension.newx.translation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import app.morphe.extension.newx.translation.TranslatorConfig.CustomProvider;

public final class OpenAiCompatibleProvider implements TranslationProvider {
    private final CustomProvider provider;

    public OpenAiCompatibleProvider(CustomProvider provider) {
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
        String languageName = Locale.forLanguageTag(targetLanguage).getDisplayName(Locale.ENGLISH);
        String system = "You are a translation engine. Translate the user's message into " + languageName +
                ". Output only the translation. Preserve line breaks, emoji, URLs, @mentions and #hashtags " +
                "unchanged. If it is already in " + languageName + ", return it unchanged.";
        try {
            JSONObject body = new JSONObject()
                    .put("model", provider.model)
                    .put("temperature", 0)
                    .put("messages", new JSONArray()
                            .put(new JSONObject().put("role", "system").put("content", system))
                            .put(new JSONObject().put("role", "user").put("content", text)));
            String response = TranslationHttp.postJson(
                    provider.url + "/chat/completions",
                    Collections.singletonMap("Authorization", "Bearer " + provider.apiKey),
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
            JSONArray choices = new JSONObject(response).optJSONArray("choices");
            JSONObject first = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = first == null ? null : first.optJSONObject("message");
            String content = message == null ? "" : message.optString("content", "").trim();
            if (content.isEmpty()) throw new IOException(provider.name + " returned no translation");
            return new TranslationResult(content, null);
        } catch (JSONException exception) {
            throw new IOException("Malformed " + provider.name + " response", exception);
        }
    }
}
