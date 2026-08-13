package app.morphe.extension.xlite.postfilter;

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

    private static String reason(String text, PostFilterRule rule) {
        return PostFilterMatcher.findMatchReason(text, snapshot(rule));
    }

    private static PostFilterRuleStore.Snapshot snapshot(PostFilterRule rule) {
        return PostFilterRuleStore.snapshotOf(List.of(rule));
    }

    private static PostFilterRule contentRule(String phrase) {
        return new PostFilterRule("content-" + phrase, phrase, true, false, true);
    }
}
