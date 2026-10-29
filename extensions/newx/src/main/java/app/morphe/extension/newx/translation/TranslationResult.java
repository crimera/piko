package app.morphe.extension.newx.translation;

import java.util.Objects;

public final class TranslationResult {
    /** Never null. */
    public final String text;
    /** BCP-47 tag, or null when unknown. */
    public final String sourceLanguage;

    public TranslationResult(String text, String sourceLanguage) {
        this.text = Objects.requireNonNull(text);
        this.sourceLanguage = sourceLanguage;
    }
}
