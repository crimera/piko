/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.graphics.Color;
import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class SettingsSearchMatcher {
    private static final int MIN_SEARCH_QUERY_LENGTH = 2;
    private static final int SEARCH_RANK_TITLE_FIRST_WORD = 1;
    private static final int SEARCH_RANK_TITLE_OTHER_WORD = 2;
    private static final int SEARCH_RANK_SECTION = 3;
    private static final int SEARCH_RANK_SUMMARY = 4;
    private static final int SEARCH_RANK_KEYWORDS = 5;
    private static final int SEARCH_RANK_NONE = Integer.MAX_VALUE;
    private static final String[] WEAK_BOUNDARY_NEGATION_PREFIXES = {
            normalizeCase("\u975e"),
            normalizeCase("\u672a"),
            normalizeCase("\u4e0d"),
            normalizeCase("\u7121"),
            normalizeCase("\u65e0"),
            normalizeCase("\u0e44\u0e21\u0e48")
    };
    private static final String KOREAN_NEGATION_PREFIX = normalizeCase("\ube44");
    private static final String KOREAN_ACTIVATION_PREFIX = normalizeCase("\ud65c\uc131");
    private static final String DEFAULT_SEARCH_SUMMARY_SEPARATOR = " - ";
    static final String NESTED_SEARCH_SUMMARY_SEPARATOR = " \u2192 ";

    private SettingsSearchMatcher() {
    }

    static boolean isSearchQueryReady(String query) {
        String displayQuery = query == null ? "" : query.trim();
        String canonicalQuery = Normalizer.normalize(displayQuery, Normalizer.Form.NFC);
        return canonicalQuery.codePointCount(0, canonicalQuery.length()) >= MIN_SEARCH_QUERY_LENGTH;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return normalizeCase(text.trim());
    }

    private static String normalizeCase(String text) {
        if (isEmpty(text)) {
            return "";
        }

        StringBuilder normalizedText = new StringBuilder();
        int originalStart = 0;
        while (originalStart < text.length()) {
            int originalEnd = normalizedSegmentEnd(text, originalStart);
            normalizedText.append(normalizeSegment(text, originalStart, originalEnd));
            originalStart = originalEnd;
        }
        return normalizedText.toString();
    }

    private static NormalizedText normalizeCaseWithOffsets(String text) {
        if (isEmpty(text)) {
            return new NormalizedText("", new int[0], new int[0]);
        }

        StringBuilder normalizedText = new StringBuilder();
        List<Integer> originalStarts = new ArrayList<>();
        List<Integer> originalEnds = new ArrayList<>();
        int originalStart = 0;

        while (originalStart < text.length()) {
            int originalEnd = normalizedSegmentEnd(text, originalStart);
            String normalizedSegment = normalizeSegment(text, originalStart, originalEnd);
            normalizedText.append(normalizedSegment);

            for (int index = 0; index < normalizedSegment.length(); index++) {
                originalStarts.add(originalStart);
                originalEnds.add(originalEnd);
            }
            originalStart = originalEnd;
        }

        return new NormalizedText(
                normalizedText.toString(),
                toIntArray(originalStarts),
                toIntArray(originalEnds)
        );
    }

    private static int normalizedSegmentEnd(String text, int start) {
        int end = start + Character.charCount(text.codePointAt(start));
        while (end < text.length() && isCombiningMark(text.codePointAt(end))) {
            end += Character.charCount(text.codePointAt(end));
        }
        return end;
    }

    private static String normalizeSegment(String text, int start, int end) {
        String sourceSegment = Normalizer.normalize(
                text.substring(start, end),
                Normalizer.Form.NFC
        ).replace('\u0130', 'I');
        String normalizedSegment = Normalizer.normalize(sourceSegment, Normalizer.Form.NFD)
                .toUpperCase(Locale.ROOT);
        return Normalizer.normalize(normalizedSegment, Normalizer.Form.NFD);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    static List<SearchMatch> matchResults(List<SearchResult> results, String query) {
        String normalizedQuery = normalize(query);
        List<SearchMatch> matchedResults = new ArrayList<>();
        int resultIndex = 0;

        for (SearchResult result : results) {
            int rank = result.matchRank(normalizedQuery);
            if (rank != SEARCH_RANK_NONE) {
                matchedResults.add(new SearchMatch(result, rank, resultIndex));
            }
            resultIndex++;
        }

        Collections.sort(matchedResults, (left, right) -> {
            int rankComparison = Integer.compare(left.rank, right.rank);
            if (rankComparison != 0) {
                return rankComparison;
            }
            return Integer.compare(left.originalIndex, right.originalIndex);
        });
        return matchedResults;
    }

    static CharSequence highlightMatches(String text, String query) {
        List<MatchRange> ranges = findHighlightRanges(text, query);
        if (ranges.isEmpty()) {
            return text;
        }

        SpannableString highlighted = new SpannableString(text);
        for (MatchRange range : ranges) {
            highlighted.setSpan(
                    new ForegroundColorSpan(searchMatchTextColor()),
                    range.start,
                    range.end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return highlighted;
    }

    static List<MatchRange> findHighlightRanges(String text, String query) {
        String normalizedQuery = normalize(query);
        if (isEmpty(text) || isEmpty(normalizedQuery)) {
            return Collections.emptyList();
        }

        NormalizedText normalizedText = normalizeCaseWithOffsets(text);
        List<MatchRange> normalizedRanges;
        if (shouldUseSubstringSearch(normalizedQuery)) {
            normalizedRanges = findSubstringMatchRanges(normalizedText.text, normalizedQuery);
        } else {
            normalizedRanges = findWordPrefixMatchRanges(normalizedText.text, normalizedQuery);
        }
        return normalizedText.toOriginalRanges(normalizedRanges);
    }

    private static List<MatchRange> findSubstringMatchRanges(String normalizedText, String normalizedQuery) {
        List<MatchRange> ranges = new ArrayList<>();
        int start = firstSubstringMatchIndex(normalizedText, normalizedQuery);
        while (start >= 0) {
            int end = start + normalizedQuery.length();
            ranges.add(new MatchRange(start, end));
            start = nextSubstringMatchIndex(normalizedText, normalizedQuery, end);
        }
        return ranges;
    }

    private static List<MatchRange> findWordPrefixMatchRanges(String normalizedText, String normalizedQuery) {
        List<MatchRange> ranges = new ArrayList<>();
        List<WordRange> textWords = wordRanges(normalizedText);
        String[] queryParts = wordQueryParts(normalizedQuery);

        for (String queryPart : queryParts) {
            addWordPrefixMatchRanges(textWords, queryPart, ranges);
        }
        return ranges;
    }

    private static void addWordPrefixMatchRanges(
            List<WordRange> textWords,
            String queryPart,
            List<MatchRange> ranges
    ) {
        if (isEmpty(queryPart)) {
            return;
        }

        for (WordRange textWord : textWords) {
            if (!textWord.text.startsWith(queryPart)) {
                continue;
            }
            int start = textWord.start;
            int end = start + queryPart.length();
            if (isRangeAvailable(start, end, ranges)) {
                ranges.add(new MatchRange(start, end));
            }
        }
    }

    private static boolean isRangeAvailable(int start, int end, List<MatchRange> ranges) {
        for (MatchRange range : ranges) {
            if (start < range.end && end > range.start) {
                return false;
            }
        }
        return true;
    }

    private static int searchMatchTextColor() {
        return Color.rgb(29, 155, 240);
    }

    private static boolean matchesSearchText(String text, String normalizedQuery) {
        return searchTextMatchRank(text, normalizedQuery, 1, 1) != SEARCH_RANK_NONE;
    }

    private static int searchTextMatchRank(String text, String normalizedQuery, int firstWordRank, int otherWordRank) {
        if (isEmpty(text) || isEmpty(normalizedQuery)) {
            return SEARCH_RANK_NONE;
        }

        String normalizedText = normalize(text);
        if (isEmpty(normalizedText)) {
            return SEARCH_RANK_NONE;
        }

        if (shouldUseSubstringSearch(normalizedQuery)) {
            int matchIndex = firstSubstringMatchIndex(normalizedText, normalizedQuery);
            if (matchIndex < 0) {
                return SEARCH_RANK_NONE;
            }
            return matchIndex == 0 ? firstWordRank : otherWordRank;
        }

        String[] queryParts = wordQueryParts(normalizedQuery);
        List<WordRange> textWords = wordRanges(normalizedText);
        if (queryParts.length == 0 || textWords.isEmpty()) {
            return SEARCH_RANK_NONE;
        }

        if (!allQueryPartsMatchWords(queryParts, textWords)) {
            return SEARCH_RANK_NONE;
        }

        return textWords.get(0).text.startsWith(queryParts[0]) ? firstWordRank : otherWordRank;
    }

    private static boolean allQueryPartsMatchWords(String[] queryParts, List<WordRange> words) {
        for (String queryPart : queryParts) {
            boolean partMatches = false;
            for (WordRange word : words) {
                if (word.text.startsWith(queryPart)) {
                    partMatches = true;
                    break;
                }
            }
            if (!partMatches) {
                return false;
            }
        }
        return true;
    }

    private static int firstSubstringMatchIndex(String normalizedText, String normalizedQuery) {
        return nextSubstringMatchIndex(normalizedText, normalizedQuery, 0);
    }

    private static int nextSubstringMatchIndex(String normalizedText, String normalizedQuery, int fromIndex) {
        int matchIndex = normalizedText.indexOf(normalizedQuery, fromIndex);
        while (matchIndex >= 0 && !isAllowedSubstringMatch(normalizedText, matchIndex, normalizedQuery)) {
            matchIndex = normalizedText.indexOf(normalizedQuery, matchIndex + 1);
        }
        return matchIndex;
    }

    private static boolean isAllowedSubstringMatch(String normalizedText, int matchIndex, String normalizedQuery) {
        return !isNegatedMeaningSubstringMatch(normalizedText, matchIndex, normalizedQuery);
    }

    private static boolean isNegatedMeaningSubstringMatch(String normalizedText, int matchIndex, String normalizedQuery) {
        if (matchIndex <= 0 || isEmpty(normalizedQuery)) {
            return false;
        }
        return isWeakBoundaryNegationPrefixMatch(normalizedText, matchIndex, normalizedQuery)
                || isKoreanNegatedActivationSubstringMatch(normalizedText, matchIndex, normalizedQuery);
    }

    private static boolean isWeakBoundaryNegationPrefixMatch(
            String normalizedText,
            int matchIndex,
            String normalizedQuery
    ) {
        for (String prefix : WEAK_BOUNDARY_NEGATION_PREFIXES) {
            int prefixStart = matchIndex - prefix.length();
            if (!normalizedQuery.startsWith(prefix)
                    && prefixStart >= 0
                    && normalizedText.startsWith(prefix, prefixStart)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKoreanNegatedActivationSubstringMatch(String normalizedText, int matchIndex, String normalizedQuery) {
        return normalizedQuery.startsWith(KOREAN_ACTIVATION_PREFIX)
                && matchIndex >= KOREAN_NEGATION_PREFIX.length()
                && normalizedText.startsWith(
                        KOREAN_NEGATION_PREFIX,
                        matchIndex - KOREAN_NEGATION_PREFIX.length()
                );
    }

    private static boolean shouldUseSubstringSearch(String normalizedQuery) {
        return containsWeakWordBoundaryScript(normalizedQuery) || !isWordPrefixQuery(normalizedQuery);
    }

    private static boolean containsWeakWordBoundaryScript(String normalizedQuery) {
        for (int index = 0; index < normalizedQuery.length();) {
            int codePoint = normalizedQuery.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || script == Character.UnicodeScript.THAI) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isWordPrefixQuery(String normalizedQuery) {
        boolean hasWordCharacter = false;
        for (int index = 0; index < normalizedQuery.length();) {
            int codePoint = normalizedQuery.codePointAt(index);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                index += Character.charCount(codePoint);
                continue;
            }
            if (isWordCharacter(codePoint)) {
                hasWordCharacter = true;
                index += Character.charCount(codePoint);
                continue;
            }
            return false;
        }
        return hasWordCharacter;
    }

    private static String[] wordQueryParts(String normalizedQuery) {
        List<WordRange> queryWords = wordRanges(normalizedQuery);
        String[] parts = new String[queryWords.size()];
        for (int index = 0; index < queryWords.size(); index++) {
            parts[index] = queryWords.get(index).text;
        }
        return parts;
    }

    private static List<WordRange> wordRanges(String normalizedText) {
        List<WordRange> words = new ArrayList<>();
        int start = -1;
        for (int index = 0; index < normalizedText.length();) {
            int codePoint = normalizedText.codePointAt(index);
            if (isWordCharacter(codePoint)) {
                if (start < 0) {
                    start = index;
                }
                index += Character.charCount(codePoint);
                continue;
            }

            if (start >= 0) {
                words.add(new WordRange(normalizedText.substring(start, index), start));
                start = -1;
            }
            index += Character.charCount(codePoint);
        }

        if (start >= 0) {
            words.add(new WordRange(normalizedText.substring(start), start));
        }
        return words;
    }

    private static boolean isWordCharacter(int codePoint) {
        if (Character.isLetterOrDigit(codePoint)) {
            return true;
        }
        return isCombiningMark(codePoint);
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    static boolean textEquals(CharSequence left, CharSequence right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        for (int index = 0; index < left.length(); index++) {
            if (left.charAt(index) != right.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    static class SearchMatch {
        final SearchResult result;
        private final int rank;
        private final int originalIndex;

        SearchMatch(SearchResult result, int rank, int originalIndex) {
            this.result = result;
            this.rank = rank;
            this.originalIndex = originalIndex;
        }
    }

    private static class WordRange {
        final String text;
        final int start;

        WordRange(String text, int start) {
            this.text = text;
            this.start = start;
        }
    }

    private static class NormalizedText {
        final String text;
        private final int[] originalStarts;
        private final int[] originalEnds;

        NormalizedText(String text, int[] originalStarts, int[] originalEnds) {
            this.text = text;
            this.originalStarts = originalStarts;
            this.originalEnds = originalEnds;
        }

        List<MatchRange> toOriginalRanges(List<MatchRange> normalizedRanges) {
            if (normalizedRanges.isEmpty()) {
                return Collections.emptyList();
            }

            List<MatchRange> originalRanges = new ArrayList<>(normalizedRanges.size());
            for (MatchRange normalizedRange : normalizedRanges) {
                if (normalizedRange.start < 0
                        || normalizedRange.start >= originalStarts.length
                        || normalizedRange.end <= normalizedRange.start
                        || normalizedRange.end > originalEnds.length) {
                    continue;
                }
                originalRanges.add(new MatchRange(
                        originalStarts[normalizedRange.start],
                        originalEnds[normalizedRange.end - 1]
                ));
            }
            return originalRanges;
        }
    }

    static class MatchRange {
        final int start;
        final int end;

        MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    static class SearchResult {
        final String title;
        final String summary;
        final String searchSummary;
        final String searchKeywords;
        final String sectionTitle;
        final String destinationKey;
        final String preferenceKey;
        final String iconName;
        final String summarySeparator;
        final String externalActivityClassName;
        final String externalSearchTargetTitle;

        static Builder builder(String title, String destinationKey) {
            return new Builder(title, destinationKey);
        }

        private SearchResult(Builder builder) {
            title = builder.title;
            summary = builder.summary;
            searchSummary = builder.searchSummary;
            searchKeywords = builder.searchKeywords;
            sectionTitle = builder.sectionTitle;
            destinationKey = builder.destinationKey;
            preferenceKey = builder.preferenceKey;
            iconName = builder.iconName;
            summarySeparator = builder.summarySeparator;
            externalActivityClassName = builder.externalActivityClassName;
            externalSearchTargetTitle = isEmpty(builder.externalSearchTargetTitle)
                    ? title
                    : builder.externalSearchTargetTitle;
        }

        boolean sameTarget(SearchResult other) {
            return other != null
                    && textEquals(title, other.title)
                    && textEquals(summary, other.summary)
                    && textEquals(sectionTitle, other.sectionTitle)
                    && textEquals(destinationKey, other.destinationKey)
                    && textEquals(preferenceKey, other.preferenceKey)
                    && textEquals(summarySeparator, other.summarySeparator)
                    && textEquals(externalActivityClassName, other.externalActivityClassName)
                    && textEquals(externalSearchTargetTitle, other.externalSearchTargetTitle);
        }

        boolean opensExternalSettings() {
            return !isEmpty(externalActivityClassName);
        }

        String externalSearchTargetTitle() {
            return externalSearchTargetTitle;
        }

        int matchRank(String normalizedQuery) {
            int rank = searchTextMatchRank(title, normalizedQuery, SEARCH_RANK_TITLE_FIRST_WORD, SEARCH_RANK_TITLE_OTHER_WORD);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            rank = searchTextMatchRank(sectionTitle, normalizedQuery, SEARCH_RANK_SECTION, SEARCH_RANK_SECTION);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            rank = visibleFieldsMatchRank(normalizedQuery);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            rank = searchTextMatchRank(summary, normalizedQuery, SEARCH_RANK_SUMMARY, SEARCH_RANK_SUMMARY);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            rank = searchTextMatchRank(searchSummary, normalizedQuery, SEARCH_RANK_SUMMARY, SEARCH_RANK_SUMMARY);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            rank = searchTextMatchRank(searchKeywords, normalizedQuery, SEARCH_RANK_KEYWORDS, SEARCH_RANK_KEYWORDS);
            if (rank != SEARCH_RANK_NONE) {
                return rank;
            }

            return SEARCH_RANK_NONE;
        }

        private int visibleFieldsMatchRank(String normalizedQuery) {
            if (shouldUseSubstringSearch(normalizedQuery)) {
                return SEARCH_RANK_NONE;
            }

            String[] queryParts = wordQueryParts(normalizedQuery);
            if (queryParts.length < 2) {
                return SEARCH_RANK_NONE;
            }

            List<WordRange> visibleWords = new ArrayList<>();
            visibleWords.addAll(wordRanges(normalize(title)));
            visibleWords.addAll(wordRanges(normalize(sectionTitle)));
            visibleWords.addAll(wordRanges(normalize(summary)));
            if (visibleWords.isEmpty()) {
                return SEARCH_RANK_NONE;
            }

            if (!allQueryPartsMatchWords(queryParts, visibleWords)) {
                return SEARCH_RANK_NONE;
            }
            return SEARCH_RANK_SUMMARY;
        }

        String summaryForDisplay(String query, boolean isRtl) {
            String displaySummary = isEmpty(summary) ? searchSummary : summary;
            String normalizedQuery = normalize(query);
            if (!isEmpty(normalizedQuery)
                    && !visibleTextMatches(displaySummary, normalizedQuery)
                    && matchesSearchText(searchSummary, normalizedQuery)
                    && !textEquals(displaySummary, searchSummary)) {
                displaySummary = appendSearchHint(displaySummary, searchSummary);
            }
            if (!isEmpty(normalizedQuery)
                    && !visibleTextMatches(displaySummary, normalizedQuery)
                    && matchesSearchText(searchKeywords, normalizedQuery)) {
                displaySummary = appendSearchHint(displaySummary, query == null ? "" : query.trim());
            }
            String displaySectionTitle = formatHierarchyText(sectionTitle, isRtl);
            if (isEmpty(displaySummary)) {
                return displaySectionTitle;
            }
            BidiFormatter formatter = BidiFormatter.getInstance(isRtl);
            String displaySeparator = textEquals(summarySeparator, NESTED_SEARCH_SUMMARY_SEPARATOR)
                    ? nestedSearchSummarySeparator(isRtl)
                    : summarySeparator;
            return displaySectionTitle
                    + displaySeparator
                    + formatter.unicodeWrap(displaySummary);
        }

        private static String formatHierarchyText(String text, boolean isRtl) {
            if (isEmpty(text)) {
                return "";
            }

            BidiFormatter formatter = BidiFormatter.getInstance(isRtl);
            String separator = nestedSearchSummarySeparator(isRtl);
            StringBuilder formatted = new StringBuilder();
            int segmentStart = 0;
            int separatorStart = text.indexOf(NESTED_SEARCH_SUMMARY_SEPARATOR);
            while (separatorStart >= 0) {
                formatted.append(formatter.unicodeWrap(text.substring(segmentStart, separatorStart)));
                formatted.append(separator);
                segmentStart = separatorStart + NESTED_SEARCH_SUMMARY_SEPARATOR.length();
                separatorStart = text.indexOf(NESTED_SEARCH_SUMMARY_SEPARATOR, segmentStart);
            }
            formatted.append(formatter.unicodeWrap(text.substring(segmentStart)));
            return formatted.toString();
        }

        private static String nestedSearchSummarySeparator(boolean isRtl) {
            return isRtl ? " \u2190 " : NESTED_SEARCH_SUMMARY_SEPARATOR;
        }

        private boolean visibleTextMatches(String displaySummary, String normalizedQuery) {
            return matchesSearchText(title, normalizedQuery)
                    || matchesSearchText(displaySummary, normalizedQuery)
                    || matchesSearchText(sectionTitle, normalizedQuery);
        }

        private String appendSearchHint(String displaySummary, String hint) {
            if (isEmpty(hint)) {
                return displaySummary;
            }
            if (isEmpty(displaySummary)) {
                return hint;
            }
            return displaySummary + " - " + hint;
        }

        static final class Builder {
            private final String title;
            private final String destinationKey;
            private String summary = "";
            private String searchSummary = "";
            private String searchKeywords = "";
            private String sectionTitle = "";
            private String preferenceKey = "";
            private String iconName = "";
            private String summarySeparator = DEFAULT_SEARCH_SUMMARY_SEPARATOR;
            private String externalActivityClassName = "";
            private String externalSearchTargetTitle = "";

            private Builder(String title, String destinationKey) {
                this.title = title == null ? "" : title;
                this.destinationKey = destinationKey == null ? "" : destinationKey;
            }

            Builder summary(String value) {
                summary = value == null ? "" : value;
                return this;
            }

            Builder searchSummary(String value) {
                searchSummary = value == null ? "" : value;
                return this;
            }

            Builder searchKeywords(String value) {
                searchKeywords = value == null ? "" : value;
                return this;
            }

            Builder sectionTitle(String value) {
                sectionTitle = value == null ? "" : value;
                return this;
            }

            Builder preferenceKey(String value) {
                preferenceKey = value == null ? "" : value;
                return this;
            }

            Builder iconName(String value) {
                iconName = value == null ? "" : value;
                return this;
            }

            Builder summarySeparator(String value) {
                summarySeparator = isEmpty(value) ? DEFAULT_SEARCH_SUMMARY_SEPARATOR : value;
                return this;
            }

            Builder externalDestination(String activityClassName, String targetTitle) {
                externalActivityClassName = activityClassName == null ? "" : activityClassName;
                externalSearchTargetTitle = targetTitle == null ? "" : targetTitle;
                return this;
            }

            SearchResult build() {
                return new SearchResult(this);
            }
        }
    }
}
