/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.nativeFeatures.translator.providers;



public interface Translate{
    /**
     * Asynchronous translation method
     * @param query The text to be translated
     * @param toLang Target language code
     * @param callback Callback to handle translation result
     */
    void translate(Long tweetId, String query, String toLang, TranslationCallback callback);

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