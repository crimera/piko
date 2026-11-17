package app.morphe.extension.newx.translation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.newx.translation.TranslatorConfig.CustomProvider;

public final class DeepLProvider implements TranslationProvider {
    private static final String FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate";
    private static final String PRO_ENDPOINT = "https://api.deepl.com/v2/translate";

    private final CustomProvider provider;

    public DeepLProvider(CustomProvider provider) {
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
        // Free-plan keys end in ":fx" and only work on the free host.
        String endpoint = provider.apiKey.endsWith(":fx") ? FREE_ENDPOINT : PRO_ENDPOINT;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("text", text);
        form.put("target_lang", deeplTarget(targetLanguage));
        if (sourceHint != null) {
            form.put("source_lang", TranslatorConfig.primaryLanguage(sourceHint).toUpperCase(Locale.ROOT));
        }
        String response = TranslationHttp.postForm(
                endpoint,
                Collections.singletonMap("Authorization", "DeepL-Auth-Key " + provider.apiKey),
                form
        );
        return parseResponse(response);
    }

    @Override
    public TranslationResult translatePost(String postId, String postText, String targetLanguage) throws IOException {
        return translateText(postText, null, targetLanguage);
    }

    private static String deeplTarget(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage();
        String country = locale.getCountry();
        String script = locale.getScript();
        if ("en".equals(language)) return "GB".equals(country) ? "EN-GB" : "EN-US";
        if ("pt".equals(language)) return "PT".equals(country) ? "PT-PT" : "PT-BR";
        if ("zh".equals(language)) {
            boolean traditional = "Hant".equals(script) ||
                    (script.isEmpty() && ("TW".equals(country) || "HK".equals(country) || "MO".equals(country)));
            return traditional ? "ZH-HANT" : "ZH-HANS";
        }
        return language.toUpperCase(Locale.ROOT);
    }

    private TranslationResult parseResponse(String response) throws IOException {
        try {
            JSONArray translations = new JSONObject(response).optJSONArray("translations");
            JSONObject first = translations == null ? null : translations.optJSONObject(0);
            String text = first == null ? "" : first.optString("text", "");
            if (text.isEmpty()) throw new IOException(provider.name + " returned no translation");
            return new TranslationResult(
                    text,
                    TranslatorConfig.normalizeLanguage(first.optString("detected_source_language", null))
            );
        } catch (JSONException exception) {
            throw new IOException("Malformed " + provider.name + " response", exception);
        }
    }
}
