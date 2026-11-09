/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.preference.widgets.ButtonPref;

public final class SettingsSearchIndex {
    private static final String PIKO_FRAGMENT_KEY_PREFIX = "piko_frag_";

    private final List<IndexedResult> results;

    public interface ScreenFactory {
        PreferenceScreen build(String fragmentName);
    }

    public static SettingsSearchIndex create(
            PreferenceScreen root,
            ScreenFactory factory
    ) {
        List<IndexedResult> collectedResults = new ArrayList<>();
        if (root == null || factory == null) {
            return new SettingsSearchIndex(collectedResults);
        }

        int order = 0;
        for (int index = 0; index < root.getPreferenceCount(); index++) {
            Preference preference = root.getPreference(index);
            if (!(preference instanceof ButtonPref)) {
                continue;
            }

            String fragmentName = preference.getKey();
            String title = preferenceText(preference.getTitle());
            if (!isFragmentKey(fragmentName) || isEmpty(title)) {
                continue;
            }

            Result category = new Result(
                    title,
                    preferenceText(preference.getSummary()),
                    "",
                    fragmentName,
                    null,
                    ButtonPref.iconResourceNameForKey(fragmentName)
            );

            PreferenceScreen screen = factory.build(fragmentName);
            if (screen != null) {
                order = collectPreferences(
                        screen,
                        category,
                        factory,
                        collectedResults,
                        order
                );
            }
        }
        return new SettingsSearchIndex(collectedResults);
    }

    public List<Result> search(String query) {
        if (SettingsSearchMatcher.isSearchQueryEmpty(query)) {
            return Collections.emptyList();
        }
        if (!SettingsSearchMatcher.isSearchQueryReady(query)) {
            return Collections.emptyList();
        }

        SettingsSearchMatcher.Query preparedQuery = new SettingsSearchMatcher.Query(query);
        List<RankedResult> matches = new ArrayList<>();
        for (IndexedResult indexedResult : results) {
            int rank = SettingsSearchMatcher.matchRank(indexedResult.searchDocument, preparedQuery);
            if (rank != SettingsSearchMatcher.RANK_NONE) {
                matches.add(new RankedResult(indexedResult, rank));
            }
        }

        Collections.sort(matches, new Comparator<RankedResult>() {
            @Override
            public int compare(RankedResult left, RankedResult right) {
                int rankComparison = Integer.compare(left.rank, right.rank);
                if (rankComparison != 0) {
                    return rankComparison;
                }
                return Integer.compare(left.indexedResult.order, right.indexedResult.order);
            }
        });

        List<Result> matchedResults = new ArrayList<>(matches.size());
        for (RankedResult match : matches) {
            matchedResults.add(match.indexedResult.result);
        }
        return Collections.unmodifiableList(matchedResults);
    }

    private SettingsSearchIndex(List<IndexedResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    private static int collectPreferences(
            PreferenceGroup group,
            Result category,
            ScreenFactory factory,
            List<IndexedResult> results,
            int order
    ) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (preference instanceof PreferenceGroup) {
                if (Constants.PIKO_PATCH_INFO_PREFERENCE_CATEGORY.equals(preference.getKey())) {
                    continue;
                }
                order = collectPreferences(
                        (PreferenceGroup) preference,
                        category,
                        factory,
                        results,
                        order
                );
                continue;
            }

            String title = preferenceText(preference.getTitle());
            String preferenceKey = preference.getKey();
            if (isEmpty(title)
                    || isEmpty(preferenceKey)
                    || isExcludedPreference(preferenceKey)) {
                continue;
            }

            if (preference instanceof ButtonPref && isFragmentKey(preferenceKey)) {
                PreferenceScreen nestedScreen = factory.build(preferenceKey);
                if (nestedScreen != null) {
                    String nestedIcon = ButtonPref.iconResourceNameForKey(preferenceKey);
                    Result nestedCategory = new Result(
                            title,
                            preferenceText(preference.getSummary()),
                            "",
                            preferenceKey,
                            null,
                            isEmpty(nestedIcon)
                                    ? category.categoryIconResourceName
                                    : nestedIcon
                    );
                    order = collectPreferences(
                            nestedScreen,
                            nestedCategory,
                            factory,
                            results,
                            order
                    );
                }
                continue;
            }

            results.add(new IndexedResult(
                    new Result(
                            title,
                            preferenceText(preference.getSummary()),
                            category.title,
                            category.fragmentName,
                            preferenceKey,
                            category.categoryIconResourceName
                    ),
                    order++
            ));
        }
        return order;
    }

    private static boolean isFragmentKey(String key) {
        return key != null && key.startsWith(PIKO_FRAGMENT_KEY_PREFIX);
    }

    private static boolean isExcludedPreference(String preferenceKey) {
        return Constants.PIKO_APP_VERSION_PREFERENCE.equals(preferenceKey)
                || Constants.PIKO_PATCH_VERSION_PREFERENCE.equals(preferenceKey);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String preferenceText(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    public static final class Result {
        public final String title;
        public final String summary;
        public final String parentTitle;
        public final String fragmentName;
        public final String preferenceKey;
        public final String categoryIconResourceName;

        private Result(
                String title,
                String summary,
                String parentTitle,
                String fragmentName,
                String preferenceKey,
                String categoryIconResourceName
        ) {
            this.title = title;
            this.summary = summary;
            this.parentTitle = parentTitle;
            this.fragmentName = fragmentName;
            this.preferenceKey = preferenceKey;
            this.categoryIconResourceName = categoryIconResourceName;
        }
    }

    private static final class IndexedResult {
        final Result result;
        final SettingsSearchMatcher.SearchDocument searchDocument;
        final int order;

        IndexedResult(Result result, int order) {
            this.result = result;
            this.searchDocument = new SettingsSearchMatcher.SearchDocument(result);
            this.order = order;
        }
    }

    private static final class RankedResult {
        final IndexedResult indexedResult;
        final int rank;

        RankedResult(IndexedResult indexedResult, int rank) {
            this.indexedResult = indexedResult;
            this.rank = rank;
        }
    }
}
