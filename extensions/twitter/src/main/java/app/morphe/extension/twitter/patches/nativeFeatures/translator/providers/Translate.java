/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
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