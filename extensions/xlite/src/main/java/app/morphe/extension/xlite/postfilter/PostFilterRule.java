package app.morphe.extension.xlite.postfilter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

public final class PostFilterRule {
    private final String id;
    private final String phrase;
    private final String normalizedPhrase;
    private final boolean matchContent;
    private final boolean matchUsernames;
    private final boolean enabled;

    public PostFilterRule(
            String id,
            String phrase,
            boolean matchContent,
            boolean matchUsernames,
            boolean enabled
    ) {
        this.id = Objects.requireNonNull(id);
        this.phrase = Objects.requireNonNull(phrase);
        this.normalizedPhrase = normalize(phrase);
        this.matchContent = matchContent;
        this.matchUsernames = matchUsernames;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getPhrase() {
        return phrase;
    }

    public String getNormalizedPhrase() {
        return normalizedPhrase;
    }

    public boolean matchesContent() {
        return matchContent;
    }

    public boolean matchesUsernames() {
        return matchUsernames;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PostFilterRule withEnabled(boolean newEnabled) {
        return new PostFilterRule(id, phrase, matchContent, matchUsernames, newEnabled);
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
