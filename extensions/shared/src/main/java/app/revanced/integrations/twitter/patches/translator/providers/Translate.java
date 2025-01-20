package app.revanced.integrations.twitter.patches.translator.providers;



public interface Translate{
    /**
     * Asynchronous translation method
     * @param query The text to be translated
     * @param toLang Target language code
     * @param callback Callback to handle translation result
     */
    void translate(String query, String toLang, TranslationCallback callback);

    // Returns  provider name
    String getProviderName();
    /**
     * Callback interface for translation results
     */
    interface TranslationCallback {
        void onTranslationComplete(String translatedText);
        void onError(Exception error);
    }
}