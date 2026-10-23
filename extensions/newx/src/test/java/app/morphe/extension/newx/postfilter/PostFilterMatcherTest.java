package app.morphe.extension.newx.postfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;
import java.util.Locale;

public class PostFilterMatcherTest {
    @Test
    public void normalizeUsesNfkcAndLocaleRoot() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("fullwidth", PostFilterMatcher.normalize("ＦＵＬＬＷＩＤＴＨ"));
            assertEquals("i", PostFilterMatcher.normalize("I"));
            assertEquals(
                    PostFilterMatcher.normalize("é"),
                    PostFilterMatcher.normalize("e\u0301")
            );
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void matchesNormalizedMainText() {
        assertEquals("KEYWORD_MAIN_TEXT", reason("Contains ＢＬＯＣＫＥＤ phrase", contentRule("blocked")));
    }

    @Test
    public void doesNotMatchUnrelatedMainText() {
        assertNull(reason("safe post", contentRule("blocked")));
    }

    @Test
    public void disabledRuleDoesNotMatch() {
        PostFilterRule disabled = new PostFilterRule("1", "blocked", true, false, false);
        assertNull(reason("blocked", disabled));
    }

    @Test
    public void usernameOnlyRuleDoesNotMatchMainText() {
        PostFilterRule username = new PostFilterRule("1", "blocked", false, true, true);
        assertNull(reason("blocked", username));
    }

    @Test
    public void nullTextOrSnapshotDoesNotMatch() {
        assertNull(PostFilterMatcher.findMatchReason(null, snapshot(contentRule("blocked"))));
        assertNull(PostFilterMatcher.findMatchReason("blocked", null));
    }

    @Test
    public void matchesUsernameScopeAuthorScreenName() {
        assertEquals(
                "KEYWORD_USERNAME",
                reason("clean body", "@rezero", usernameRule("rezero"))
        );
    }

    @Test
    public void contentScopeDoesNotMatchAuthorScreenName() {
        assertNull(reason("clean body", "@rezero", contentRule("rezero")));
    }

    @Test
    public void usernameScopeDoesNotMatchCleanBody() {
        assertNull(reason("clean body", "@someone", usernameRule("rezero")));
    }

    @Test
    public void matchesMainTextWhenAuthorDoesNotMatch() {
        assertEquals(
                "KEYWORD_MAIN_TEXT",
                reason("rezero is blocked", "@someone", contentRule("rezero"))
        );
    }

    @Test
    public void nullAuthorWithUsernameRuleDoesNotMatch() {
        assertNull(reason("clean body", null, usernameRule("rezero")));
    }

    @Test
    public void legacyOverloadIgnoresUsernameScope() {
        assertNull(PostFilterMatcher.findMatchReason("rezero", snapshot(usernameRule("rezero"))));
        assertEquals(
                "KEYWORD_MAIN_TEXT",
                PostFilterMatcher.findMatchReason("has rezero", snapshot(contentRule("rezero")))
        );
    }

    private static String reason(String text, String authorScreenName, PostFilterRule rule) {
        return PostFilterMatcher.findMatchReason(text, authorScreenName, snapshot(rule));
    }

    private static String reason(String text, PostFilterRule rule) {
        return PostFilterMatcher.findMatchReason(text, snapshot(rule));
    }

    private static PostFilterRuleStore.Snapshot snapshot(PostFilterRule rule) {
        return PostFilterRuleStore.snapshotOf(List.of(rule));
    }

    private static PostFilterRule contentRule(String phrase) {
        return new PostFilterRule("content-" + phrase, phrase, true, false, true);
    }

    private static PostFilterRule usernameRule(String phrase) {
        return new PostFilterRule("username-" + phrase, phrase, false, true, true);
    }
}
