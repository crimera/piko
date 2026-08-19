package app.morphe.extension.xlite.postfilter;

import java.util.List;

public final class PostFilterMatcher {
    private PostFilterMatcher() {
    }

    public static String findMatchReason(
            String postText,
            String authorScreenName,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        if (snapshot == null || !snapshot.hasEnabledRules()) return null;
        if (postText != null && matches(postText, snapshot.contentPhrases())) return "KEYWORD_MAIN_TEXT";
        if (authorScreenName != null && matches(authorScreenName, snapshot.usernamePhrases())) {
            return "KEYWORD_USERNAME";
        }
        return null;
    }

    public static String findMatchReason(
            String postText,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        return findMatchReason(postText, null, snapshot);
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
