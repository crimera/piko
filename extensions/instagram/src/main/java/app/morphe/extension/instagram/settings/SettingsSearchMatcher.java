/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SettingsSearchMatcher {
    private static final int MIN_SEARCH_QUERY_LENGTH = 2;
    static final int RANK_NONE = Integer.MAX_VALUE;
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

    private SettingsSearchMatcher() {
    }

    static boolean isSearchQueryEmpty(String query) {
        String displayQuery = query == null ? "" : query.trim();
        return Normalizer.normalize(displayQuery, Normalizer.Form.NFC).isEmpty();
    }

    static boolean isSearchQueryReady(String query) {
        String displayQuery = query == null ? "" : query.trim();
        String canonicalQuery = Normalizer.normalize(displayQuery, Normalizer.Form.NFC);
        return canonicalQuery.codePointCount(0, canonicalQuery.length())
                >= MIN_SEARCH_QUERY_LENGTH;
    }

    static int matchRank(SearchDocument result, Query query) {
        if (result == null) {
            return RANK_NONE;
        }

        int rank = searchTextMatchRank(result.title, query, 0, 1);
        if (rank != RANK_NONE) {
            return rank;
        }
        rank = searchTextMatchRank(result.summary, query, 2, 2);
        if (rank != RANK_NONE) {
            return rank;
        }
        rank = searchTextMatchRank(result.parentTitle, query, 3, 3);
        if (rank != RANK_NONE) {
            return rank;
        }
        return visibleFieldsMatchRank(result, query);
    }

    private static int visibleFieldsMatchRank(
            SearchDocument result,
            Query query
    ) {
        if (query.substringSearch || query.words.size() < 2) {
            return RANK_NONE;
        }
        for (String word : query.words) {
            if (!matchesWordPrefix(word, result.title.words)
                    && !matchesWordPrefix(word, result.parentTitle.words)
                    && !matchesWordPrefix(word, result.summary.words)) {
                return RANK_NONE;
            }
        }
        return 4;
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

    private static int searchTextMatchRank(
            SearchText text,
            Query query,
            int firstWordRank,
            int otherWordRank
    ) {
        if (isEmpty(text.normalized) || isEmpty(query.normalized)) {
            return RANK_NONE;
        }

        if (query.substringSearch) {
            int matchIndex = firstSubstringMatchIndex(text.normalized, query.normalized);
            if (matchIndex < 0) {
                return RANK_NONE;
            }
            return matchIndex == 0 ? firstWordRank : otherWordRank;
        }

        if (query.words.isEmpty() || text.words.isEmpty()) {
            return RANK_NONE;
        }
        if (!allQueryPartsMatchWords(query.words, text.words)) {
            return RANK_NONE;
        }
        return text.words.get(0).startsWith(query.words.get(0))
                ? firstWordRank
                : otherWordRank;
    }

    private static boolean allQueryPartsMatchWords(
            List<String> queryParts,
            List<String> words
    ) {
        for (String queryPart : queryParts) {
            if (!matchesWordPrefix(queryPart, words)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesWordPrefix(String queryPart, List<String> words) {
        for (String word : words) {
            if (word.startsWith(queryPart)) {
                return true;
            }
        }
        return false;
    }

    private static int firstSubstringMatchIndex(
            String normalizedText,
            String normalizedQuery
    ) {
        return nextSubstringMatchIndex(normalizedText, normalizedQuery, 0);
    }

    private static int nextSubstringMatchIndex(
            String normalizedText,
            String normalizedQuery,
            int fromIndex
    ) {
        int matchIndex = normalizedText.indexOf(normalizedQuery, fromIndex);
        while (matchIndex >= 0
                && !isAllowedSubstringMatch(normalizedText, matchIndex, normalizedQuery)) {
            matchIndex = normalizedText.indexOf(normalizedQuery, matchIndex + 1);
        }
        return matchIndex;
    }

    private static boolean isAllowedSubstringMatch(
            String normalizedText,
            int matchIndex,
            String normalizedQuery
    ) {
        return !isNegatedMeaningSubstringMatch(
                normalizedText,
                matchIndex,
                normalizedQuery
        );
    }

    private static boolean isNegatedMeaningSubstringMatch(
            String normalizedText,
            int matchIndex,
            String normalizedQuery
    ) {
        if (matchIndex <= 0 || isEmpty(normalizedQuery)) {
            return false;
        }
        return isWeakBoundaryNegationPrefixMatch(
                normalizedText,
                matchIndex,
                normalizedQuery
        ) || isKoreanNegatedActivationSubstringMatch(
                normalizedText,
                matchIndex,
                normalizedQuery
        );
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

    private static boolean isKoreanNegatedActivationSubstringMatch(
            String normalizedText,
            int matchIndex,
            String normalizedQuery
    ) {
        return normalizedQuery.startsWith(KOREAN_ACTIVATION_PREFIX)
                && matchIndex >= KOREAN_NEGATION_PREFIX.length()
                && normalizedText.startsWith(
                KOREAN_NEGATION_PREFIX,
                matchIndex - KOREAN_NEGATION_PREFIX.length()
        );
    }

    private static boolean shouldUseSubstringSearch(String normalizedQuery) {
        return containsWeakWordBoundaryScript(normalizedQuery)
                || !isWordPrefixQuery(normalizedQuery);
    }

    private static boolean containsWeakWordBoundaryScript(String normalizedQuery) {
        for (int index = 0; index < normalizedQuery.length(); ) {
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
        for (int index = 0; index < normalizedQuery.length(); ) {
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

    private static List<String> words(String normalizedText) {
        List<String> words = new ArrayList<>();
        int start = -1;
        for (int index = 0; index < normalizedText.length(); ) {
            int codePoint = normalizedText.codePointAt(index);
            if (isWordCharacter(codePoint)) {
                if (start < 0) {
                    start = index;
                }
                index += Character.charCount(codePoint);
                continue;
            }

            if (start >= 0) {
                words.add(normalizedText.substring(start, index));
                start = -1;
            }
            index += Character.charCount(codePoint);
        }

        if (start >= 0) {
            words.add(normalizedText.substring(start));
        }
        return words;
    }

    private static boolean isWordCharacter(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || isCombiningMark(codePoint);
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isEmpty(CharSequence text) {
        return text == null || text.length() == 0;
    }

    static final class Query {
        private final String normalized;
        private final boolean substringSearch;
        private final List<String> words;

        Query(String query) {
            normalized = normalize(query);
            substringSearch = shouldUseSubstringSearch(normalized);
            words = substringSearch ? Collections.emptyList() : words(normalized);
        }
    }

    static final class SearchDocument {
        private final SearchText title;
        private final SearchText summary;
        private final SearchText parentTitle;

        SearchDocument(SettingsSearchIndex.Result result) {
            title = new SearchText(result.title);
            summary = new SearchText(result.summary);
            parentTitle = new SearchText(result.parentTitle);
        }
    }

    private static final class SearchText {
        final String normalized;
        final List<String> words;

        SearchText(String text) {
            normalized = normalize(text);
            words = words(normalized);
        }
    }

}
