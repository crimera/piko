package app.morphe.extension.xlite.postfilter;

import java.util.List;

import app.morphe.extension.shared.Logger;

public final class PostFilterMatcher {
    private PostFilterMatcher() {
    }

    public static String findMatchReason(
            Object post,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        if (post == null || snapshot == null || !snapshot.hasEnabledRules()) return null;

        List<String> contentPhrases = snapshot.contentPhrases();
        try {
            if (matches(getPostText(post), contentPhrases)) return "KEYWORD_MAIN_TEXT";
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to inspect an X-Lite post for filtering", exception);
        }

        return null;
    }

    public static String normalize(String value) {
        return PostFilterRule.normalize(value);
    }

    private static String getPostText(Object post) {
        return null;
    }

    private static boolean matches(String candidate, List<String> phrases) {
        if (candidate == null || candidate.isEmpty() || phrases.isEmpty()) return false;

        String normalizedCandidate = normalize(candidate);
        for (String phrase : phrases) {
            if (normalizedCandidate.contains(phrase)) return true;
        }
        return false;
    }
}
