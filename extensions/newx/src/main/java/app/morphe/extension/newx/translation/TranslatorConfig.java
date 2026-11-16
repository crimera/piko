package app.morphe.extension.newx.translation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.settings.StringSetting;

public final class TranslatorConfig {
    public static final String GOOGLE_ID = "google";
    public static final String FXTWITTER_ID = "fxtwitter";

    private static final String PROVIDER_KEY = "newx.translation.provider";
    private static final String TARGET_LANGUAGE_KEY = "newx.translation.target_language";
    private static final String CUSTOM_PROVIDERS_KEY = "newx.translation.custom_providers";
    // Kept out of settings export so API keys never end up in a backup file.
    private static final String CUSTOM_PROVIDER_KEYS_KEY = "newx.translation.custom_provider_keys";
    private static final String[][] TARGET_LANGUAGES = {
            {"", "Device language"},
            {"en", "English"},
            {"es", "Spanish"},
            {"pt-BR", "Portuguese (Brazil)"},
            {"fr", "French"},
            {"de", "German"},
            {"it", "Italian"},
            {"ja", "Japanese"},
            {"ko", "Korean"},
            {"zh-CN", "Chinese (Simplified)"},
            {"zh-TW", "Chinese (Traditional)"},
            {"ru", "Russian"},
            {"ar", "Arabic"},
            {"hi", "Hindi"},
            {"id", "Indonesian"},
            {"tr", "Turkish"},
            {"vi", "Vietnamese"},
            {"th", "Thai"},
            {"tl", "Filipino"},
            {"pl", "Polish"},
            {"nl", "Dutch"},
            {"uk", "Ukrainian"},
    };

    public enum CustomType {
        OPENAI("OpenAI-compatible"), DEEPL("DeepL"), LIBRETRANSLATE("LibreTranslate");

        public final String label;

        CustomType(String label) {
            this.label = label;
        }
    }

    public static final class CustomProvider {
        public final String id;
        public final CustomType type;
        public final String name;
        public final String url;
        public final String model;
        public final String apiKey;

        public CustomProvider(String id, CustomType type, String name, String url, String model, String apiKey) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.url = url;
            this.model = model;
            this.apiKey = apiKey;
        }
    }

    private final StringSetting providerSetting = new StringSetting(PROVIDER_KEY, GOOGLE_ID);
    private final StringSetting targetLanguage = new StringSetting(TARGET_LANGUAGE_KEY, "");
    private final StringSetting customProvidersSetting = new StringSetting(CUSTOM_PROVIDERS_KEY, "");
    private final StringSetting customProviderKeysSetting =
            new StringSetting(CUSTOM_PROVIDER_KEYS_KEY, "", false, false);
    private final TranslationProvider google = new GoogleProvider();
    private final TranslationProvider fxTwitter = new FxTwitterProvider();

    private TranslatorConfig() {
    }

    public static TranslatorConfig shared() {
        return Holder.INSTANCE;
    }

    public List<TranslationProvider> providers() {
        List<TranslationProvider> providers = new ArrayList<>();
        providers.add(google);
        providers.add(fxTwitter);
        for (CustomProvider custom : customProviders()) {
            providers.add(providerFor(custom));
        }
        return providers;
    }

    public TranslationProvider selected() {
        String id = selectedId();
        if (FXTWITTER_ID.equals(id)) return fxTwitter;
        CustomProvider custom = customProvider(id);
        return custom == null ? google : providerFor(custom);
    }

    public String selectedId() {
        return providerSetting.get();
    }

    public void select(String providerId) {
        providerSetting.save(providerId);
    }

    public List<CustomProvider> customProviders() {
        List<CustomProvider> providers = new ArrayList<>();
        String serialized = customProvidersSetting.get();
        if (serialized.isEmpty()) return providers;

        Map<String, String> keys = readKeys();
        try {
            JSONArray array = new JSONArray(serialized);
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.getJSONObject(index);
                String id = value.getString("id");
                providers.add(new CustomProvider(
                        id,
                        CustomType.valueOf(value.getString("type")),
                        value.getString("name"),
                        value.optString("url", ""),
                        value.optString("model", ""),
                        keys.getOrDefault(id, "")
                ));
            }
        } catch (JSONException | IllegalArgumentException exception) {
            NewXLogger.printException(() -> "Failed to read NewX translation providers", exception);
            return new ArrayList<>();
        }
        return providers;
    }

    public CustomProvider customProvider(String id) {
        for (CustomProvider provider : customProviders()) {
            if (provider.id.equals(id)) return provider;
        }
        return null;
    }

    public CustomProvider saveCustom(
            String idOrNull,
            CustomType type,
            String name,
            String url,
            String model,
            String apiKey
    ) {
        String cleanName = clean(name);
        String cleanUrl = clean(url).replaceAll("/+$", "");
        String cleanModel = clean(model);
        String cleanKey = clean(apiKey);
        if (cleanName.isEmpty()) throw new IllegalArgumentException("Enter a name");
        if (type != CustomType.DEEPL && !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Enter a valid URL");
        }
        if (type == CustomType.OPENAI && cleanModel.isEmpty()) throw new IllegalArgumentException("Enter a model");
        if ((type == CustomType.OPENAI || type == CustomType.DEEPL) && cleanKey.isEmpty()) {
            throw new IllegalArgumentException("Enter an API key");
        }

        CustomProvider saved = new CustomProvider(
                idOrNull == null ? UUID.randomUUID().toString() : idOrNull,
                type,
                cleanName,
                type == CustomType.DEEPL ? "" : cleanUrl,
                type == CustomType.OPENAI ? cleanModel : "",
                cleanKey
        );

        List<CustomProvider> providers = new ArrayList<>(customProviders());
        int index = indexOf(providers, saved.id);
        if (index < 0) {
            providers.add(saved);
        } else {
            providers.set(index, saved);
        }
        writeCustomProviders(providers);

        Map<String, String> keys = readKeys();
        if (saved.apiKey.isEmpty()) {
            keys.remove(saved.id);
        } else {
            keys.put(saved.id, saved.apiKey);
        }
        writeKeys(keys);
        return saved;
    }

    public void removeCustom(String id) {
        List<CustomProvider> providers = new ArrayList<>(customProviders());
        providers.removeIf(provider -> provider.id.equals(id));
        writeCustomProviders(providers);

        Map<String, String> keys = readKeys();
        keys.remove(id);
        writeKeys(keys);

        if (selectedId().equals(id)) select(GOOGLE_ID);
    }

    public String targetLanguageSetting() {
        return targetLanguage.get();
    }

    public void setTargetLanguage(String languageTag) {
        targetLanguage.save(languageTag == null ? "" : languageTag);
    }

    public String resolvedTargetLanguage() {
        String setting = targetLanguage.get();
        return setting.isEmpty() ? Locale.getDefault().toLanguageTag() : setting;
    }

    public static List<String[]> targetLanguageChoices() {
        return new ArrayList<>(Arrays.asList(TARGET_LANGUAGES));
    }

    public static TranslationProvider providerFor(CustomProvider provider) {
        switch (provider.type) {
            case OPENAI:
                return new OpenAiCompatibleProvider(provider);
            case DEEPL:
                return new DeepLProvider(provider);
            case LIBRETRANSLATE:
                return new LibreTranslateProvider(provider);
            default:
                throw new IllegalArgumentException("Unknown translation provider type");
        }
    }

    /** Lowercase primary language subtag, such as "pt" for "pt-BR". */
    static String primaryLanguage(String languageTag) {
        return Locale.forLanguageTag(languageTag).getLanguage();
    }

    /** BCP-47 tag for a language reported by a provider, or null when it is missing or unknown. */
    static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return null;
        String normalized = language.trim().replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalized);
        String languageCode = locale.getLanguage();
        if (languageCode.isEmpty() || "und".equals(languageCode)) return null;
        String languageTag = locale.toLanguageTag();
        return "und".equals(languageTag) ? null : languageTag;
    }

    private Map<String, String> readKeys() {
        Map<String, String> keys = new HashMap<>();
        String serialized = customProviderKeysSetting.get();
        if (serialized.isEmpty()) return keys;
        try {
            JSONObject object = new JSONObject(serialized);
            Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String name = names.next();
                keys.put(name, object.optString(name, ""));
            }
        } catch (JSONException exception) {
            // The JSON parser echoes its input in error messages, so the keys must not be logged.
            NewXLogger.printException(() -> "Failed to read NewX translation API keys");
        }
        return keys;
    }

    private void writeKeys(Map<String, String> keys) {
        customProviderKeysSetting.save(new JSONObject(keys).toString());
    }

    private void writeCustomProviders(List<CustomProvider> providers) {
        JSONArray array = new JSONArray();
        try {
            for (CustomProvider provider : providers) {
                array.put(new JSONObject()
                        .put("id", provider.id)
                        .put("type", provider.type.name())
                        .put("name", provider.name)
                        .put("url", provider.url)
                        .put("model", provider.model));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not save translation providers", exception);
        }
        customProvidersSetting.save(array.toString());
    }

    private static int indexOf(List<CustomProvider> providers, String id) {
        for (int index = 0; index < providers.size(); index++) {
            if (providers.get(index).id.equals(id)) return index;
        }
        return -1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Holder {
        private static final TranslatorConfig INSTANCE = new TranslatorConfig();
    }
}
