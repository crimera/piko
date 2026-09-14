package app.morphe.extension.newx.postfilter;

public final class PostFilterMatcher {
    private PostFilterMatcher() {
    }

    public static String findMatchReason(
            String postText,
            String authorScreenName,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        if (snapshot == null || !snapshot.hasEnabledRules()) return null;
        PhraseMatcher content = snapshot.contentMatcher();
        if (!content.isEmpty() && postText != null && !postText.isEmpty()
                && content.matches(PostFilterRule.normalize(postText))) {
            return "KEYWORD_MAIN_TEXT";
        }
        PhraseMatcher usernames = snapshot.usernameMatcher();
        if (!usernames.isEmpty() && authorScreenName != null && !authorScreenName.isEmpty()
                && usernames.matches(PostFilterRule.normalize(authorScreenName))) {
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
}
