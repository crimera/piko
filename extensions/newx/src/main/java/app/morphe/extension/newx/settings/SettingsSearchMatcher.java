package app.morphe.extension.newx.settings;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Matches localized setting titles, summaries, hierarchy paths, and option labels. */
final class SettingsSearchMatcher {
    private static final int NO_MATCH = Integer.MAX_VALUE;
    private static final int PATH_RANK = 10;
    private static final int SUMMARY_RANK = 20;
    private static final int KEYWORD_RANK = 30;

    private SettingsSearchMatcher() {
    }

    static List<Match> match(
            List<SettingsSearchIndex.Result> results,
            String query
    ) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return Collections.emptyList();

        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            SettingsSearchIndex.Result result = results.get(index);
            int rank = resultRank(result, normalizedQuery);
            if (rank != NO_MATCH) matches.add(new Match(result, rank, index));
        }
        matches.sort(Comparator
                .comparingInt((Match match) -> match.rank)
                .thenComparingInt(match -> match.originalIndex));
        return matches;
    }

    static List<MatchRange> findHighlightRanges(String text, String query) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return Collections.emptyList();

        String comparableText = comparable(text);
        List<MatchRange> ranges = new ArrayList<>();
        for (String part : queryParts(normalizedQuery)) {
            if (part.isEmpty()) continue;
            int start = comparableText.indexOf(part);
            while (start >= 0) {
                ranges.add(new MatchRange(start, start + part.length()));
                start = comparableText.indexOf(part, start + part.length());
            }
        }
        if (ranges.isEmpty()) return ranges;
        ranges.sort(Comparator
                .comparingInt((MatchRange range) -> range.start)
                .thenComparingInt(range -> range.end));
        return mergeRanges(ranges);
    }

    private static List<MatchRange> mergeRanges(List<MatchRange> ranges) {
        List<MatchRange> merged = new ArrayList<>();
        MatchRange current = ranges.get(0);
        for (int index = 1; index < ranges.size(); index++) {
            MatchRange next = ranges.get(index);
            if (next.start > current.end) {
                merged.add(current);
                current = next;
                continue;
            }
            current = new MatchRange(current.start, Math.max(current.end, next.end));
        }
        merged.add(current);
        return merged;
    }

    private static int resultRank(
            SettingsSearchIndex.Result result,
            String normalizedQuery
    ) {
        int rank = fieldRank(result.title, normalizedQuery);
        if (rank != NO_MATCH) return rank;

        rank = fieldRank(result.path, normalizedQuery);
        if (rank != NO_MATCH) return PATH_RANK + rank;

        rank = fieldRank(result.summary, normalizedQuery);
        if (rank != NO_MATCH) return SUMMARY_RANK + rank;

        rank = fieldRank(result.keywords, normalizedQuery);
        if (rank != NO_MATCH) return KEYWORD_RANK + rank;

        return NO_MATCH;
    }

    private static int fieldRank(String text, String normalizedQuery) {
        String normalizedText = normalize(text);
        if (normalizedText.isEmpty()) return NO_MATCH;
        if (normalizedText.contains(normalizedQuery)) return 0;

        String[] queryParts = queryParts(normalizedQuery);
        boolean allWordPrefixes = true;
        boolean allSubstrings = true;
        for (String queryPart : queryParts) {
            if (!hasWordPrefix(normalizedText, queryPart)) allWordPrefixes = false;
            if (!normalizedText.contains(queryPart)) allSubstrings = false;
        }
        if (allWordPrefixes) return 1;
        if (allSubstrings) return 2;
        return NO_MATCH;
    }

    private static boolean hasWordPrefix(String text, String queryPart) {
        int start = text.indexOf(queryPart);
        while (start >= 0) {
            if (start == 0 || !isWordCharacter(text.codePointBefore(start))) return true;
            start = text.indexOf(queryPart, start + queryPart.length());
        }
        return false;
    }

    private static boolean isWordCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_';
    }

    private static String[] queryParts(String normalizedQuery) {
        return normalizedQuery.split("\\s+");
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return comparable(text).trim();
    }

    private static String comparable(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    static final class Match {
        final SettingsSearchIndex.Result result;
        final int rank;
        final int originalIndex;

        Match(SettingsSearchIndex.Result result, int rank, int originalIndex) {
            this.result = result;
            this.rank = rank;
            this.originalIndex = originalIndex;
        }
    }

    static final class MatchRange {
        final int start;
        final int end;

        MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
