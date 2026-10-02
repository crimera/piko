package app.morphe.extension.newx.postfilter;

import java.util.List;

/**
 * Substring matcher over a fixed phrase set. Phrases must already be
 * normalized; matching is exact. An empty-string phrase matches everything,
 * mirroring String.contains(""). Instances are immutable after construction.
 */
interface PhraseMatcher {
    static PhraseMatcher of(List<String> phrases) {
        if (phrases.isEmpty()) return NoPhrases.INSTANCE;
        if (phrases.size() == 1) {
            String only = phrases.get(0);
            if (only.isEmpty()) return MatchAll.INSTANCE;
            return new SinglePhrase(only);
        }
        return new AhoMatcher(phrases);
    }

    boolean isEmpty();

    boolean matches(CharSequence text);

    /** No phrases: never matches. */
    enum NoPhrases implements PhraseMatcher {
        INSTANCE;

        @Override public boolean isEmpty() { return true; }

        @Override public boolean matches(CharSequence text) { return false; }
    }

    /** Empty-string phrase: matches everything, like contains(""). */
    enum MatchAll implements PhraseMatcher {
        INSTANCE;

        @Override public boolean isEmpty() { return false; }

        @Override public boolean matches(CharSequence text) { return true; }
    }

    /** Single phrase: plain indexOf beats automaton setup + walk. */
    final class SinglePhrase implements PhraseMatcher {
        private final String phrase;

        SinglePhrase(String phrase) {
            this.phrase = phrase;
        }

        @Override public boolean isEmpty() { return false; }

        @Override public boolean matches(CharSequence text) {
            if (text instanceof String string) return string.contains(phrase);
            return text.toString().contains(phrase);
        }
    }
}
