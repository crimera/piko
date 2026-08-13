package app.morphe.extension.xlite.postfilter;

import java.util.List;

public final class PostFilterMatcher {
    private PostFilterMatcher() {
    }

    public static String findMatchReason(
            String postText,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        if (postText == null || snapshot == null || !snapshot.hasEnabledRules()) return null;
        if (matches(postText, snapshot.contentPhrases())) return "KEYWORD_MAIN_TEXT";
        return null;
    }

    public static String normalize(String value) {
        return PostFilterRule.normalize(value);
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
