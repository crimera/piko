package app.morphe.extension.newx.translation;

import java.io.IOException;

public interface TranslationProvider {
    String id();

    String displayName();

    /** False for providers that can only translate a whole post by id (FxTwitter). */
    boolean supportsText();

    /** sourceHint may be null (auto-detect); targetLanguage is a BCP-47 tag like "en" or "pt-BR". */
    TranslationResult translateText(String text, String sourceHint, String targetLanguage) throws IOException;

    /** postId may be null. Text providers just call translateText(postText, null, targetLanguage). */
    TranslationResult translatePost(String postId, String postText, String targetLanguage) throws IOException;
}
