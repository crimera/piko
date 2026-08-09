package app.morphe.extension.xlite.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class SettingsSearchMatcherTest {
    @Test
    public void ranksTitleMatchesBeforeSummaryMatches() {
        SettingsSearchIndex.Result summaryMatch = result(
                "Timeline",
                "Timeline",
                "Hide sensitive media",
                ""
        );
        SettingsSearchIndex.Result titleMatch = result(
                "Hide sensitive media",
                "Timeline",
                "Controls media visibility",
                ""
        );

        List<SettingsSearchMatcher.Match> matches = SettingsSearchMatcher.match(
                List.of(summaryMatch, titleMatch),
                "hide"
        );

        assertEquals(2, matches.size());
        assertSame(titleMatch, matches.get(0).result);
        assertSame(summaryMatch, matches.get(1).result);
    }

    @Test
    public void matchesAllQueryWordsAtWordBoundaries() {
        SettingsSearchIndex.Result result = result(
                "Hide new posts pill",
                "Timeline",
                "Shows when newer posts are available",
                ""
        );

        List<SettingsSearchMatcher.Match> matches = SettingsSearchMatcher.match(
                List.of(result),
                "new pill"
        );

        assertEquals(1, matches.size());
        assertSame(result, matches.get(0).result);
    }

    @Test
    public void matchesLocalizedTextCaseInsensitively() {
        SettingsSearchIndex.Result result = result(
                "Sensitive Media",
                "Content",
                "Allow media marked as sensitive",
                ""
        );

        assertEquals(
                1,
                SettingsSearchMatcher.match(List.of(result), "SENSITIVE").size()
        );
    }

    @Test
    public void highlightsEachQueryPart() {
        List<SettingsSearchMatcher.MatchRange> ranges =
                SettingsSearchMatcher.findHighlightRanges("Hide new posts pill", "new pill");

        assertEquals(2, ranges.size());
        assertEquals(5, ranges.get(0).start);
        assertEquals(8, ranges.get(0).end);
        assertEquals(15, ranges.get(1).start);
        assertEquals(19, ranges.get(1).end);
    }

    @Test
    public void ignoresBlankQueries() {
        SettingsSearchIndex.Result result = result(
                "Timeline",
                "Timeline",
                "Timeline settings",
                ""
        );

        assertTrue(SettingsSearchMatcher.match(List.of(result), "   ").isEmpty());
    }

    private static SettingsSearchIndex.Result result(
            String title,
            String path,
            String summary,
            String keywords
    ) {
        return new SettingsSearchIndex.Result(null, title, summary, path, keywords);
    }
}
