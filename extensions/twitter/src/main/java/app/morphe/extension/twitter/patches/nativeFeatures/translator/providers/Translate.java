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

    /** The service has no translation yet; automatic callers may retry a bounded number of times. */
    final class UnavailableException extends java.io.IOException { }

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