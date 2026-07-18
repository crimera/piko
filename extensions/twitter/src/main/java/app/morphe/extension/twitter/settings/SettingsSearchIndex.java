/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import static app.morphe.extension.shared.StringRef.str;

import android.preference.Preference;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.settings.SettingsSearchMatcher.SearchResult;

final class SettingsSearchIndex implements PreferenceBuildTarget {
    private static final String UNDO_POST_SETTINGS_ACTIVITY_CLASS = "com.twitter.feature.subscriptions.settings.undotweet.UndoTweetSettingsActivity";

    private List<SearchResult> cachedResults;
    private List<SearchResult> collectedResults;
    private SectionContext currentSection;

    @Nullable
    List<SearchResult> cachedResults() {
        return cachedResults;
    }

    void beginCollection() {
        collectedResults = new ArrayList<>();
        currentSection = null;
    }

    List<SearchResult> finishCollection() {
        List<SearchResult> results = collectedResults == null
                ? Collections.emptyList()
                : new ArrayList<>(collectedResults);
        cachedResults = Collections.unmodifiableList(results);
        clearCollectionState();
        return cachedResults;
    }

    void abortCollection() {
        clearCollectionState();
    }

    void invalidate() {
        cachedResults = null;
    }

    private void clearCollectionState() {
        collectedResults = null;
        currentSection = null;
    }

    @Override
    public void beginSection(SectionContext section) {
        currentSection = section;
    }

    @Override
    public boolean acceptsSectionContents(SectionContext section) {
        return section != null && !TextUtils.equals(Settings.PATCH_INFO, section.destinationKey);
    }

    @Override
    public void endSection() {
        collectPatchInfoResults(currentSection);
        collectNativeSettingsResults(currentSection);
        currentSection = null;
    }

    @Override
    public void addCategory(LegacyTwitterPreferenceCategory category) {
    }

    @Override
    public void addPreference(@Nullable LegacyTwitterPreferenceCategory category, Preference preference) {
        if (currentSection == null || preference == null) {
            return;
        }

        String title = preferenceText(preference.getTitle());
        if (TextUtils.isEmpty(title)) {
            return;
        }

        String summary = preferenceText(preference.getSummary());
        String categoryTitle = category == null ? currentSection.sectionTitle : preferenceText(category.getTitle());
        String sectionTitle = sectionPath(currentSection.sectionTitle, categoryTitle);

        addResult(SearchResult.builder(title, currentSection.destinationKey)
                .summary(summary)
                .sectionTitle(sectionTitle)
                .iconName(currentSection.iconName)
                .preferenceKey(preference.getKey())
                .build());
    }

    static String sectionPath(String parentTitle, String childTitle) {
        if (SettingsSearchMatcher.isEmpty(parentTitle)) {
            return childTitle == null ? "" : childTitle;
        }
        if (SettingsSearchMatcher.isEmpty(childTitle)
                || SettingsSearchMatcher.textEquals(parentTitle, childTitle)) {
            return parentTitle;
        }
        return parentTitle + SettingsSearchMatcher.NESTED_SEARCH_SUMMARY_SEPARATOR + childTitle;
    }

    private void collectNativeSettingsResults(SectionContext section) {
        if (section == null) {
            return;
        }

        if (TextUtils.equals(Settings.PREMIUM_SECTION, section.destinationKey)
                && SettingsStatus.enableUndoPosts) {
            collectUndoPostSettingsResults(section);
        }
    }

    private void collectPatchInfoResults(SectionContext section) {
        for (SearchResult result : patchInfoResults(
                section,
                str("piko_debug"),
                Settings.PIKO_DEBUG.key,
                str(Settings.SUPPORTED_LINKS),
                Settings.SUPPORTED_LINKS
        )) {
            addResult(result);
        }
    }

    static List<SearchResult> patchInfoResults(
            SectionContext section,
            String debugTitle,
            String debugPreferenceKey,
            String supportedLinksTitle,
            String supportedLinksPreferenceKey
    ) {
        if (section == null
                || !SettingsSearchMatcher.textEquals(Settings.PATCH_INFO, section.destinationKey)) {
            return Collections.emptyList();
        }

        String sectionTitle = SettingsSearchMatcher.isEmpty(section.rowTitle)
                ? section.sectionTitle
                : section.rowTitle;
        List<SearchResult> results = new ArrayList<>(2);
        addPatchInfoResult(results, section, sectionTitle, debugTitle, debugPreferenceKey);
        addPatchInfoResult(
                results,
                section,
                sectionTitle,
                supportedLinksTitle,
                supportedLinksPreferenceKey
        );
        return results;
    }

    private static void addPatchInfoResult(
            List<SearchResult> results,
            SectionContext section,
            String sectionTitle,
            String title,
            String preferenceKey
    ) {
        if (SettingsSearchMatcher.isEmpty(title) || SettingsSearchMatcher.isEmpty(preferenceKey)) {
            return;
        }

        results.add(SearchResult.builder(title, section.destinationKey)
                .sectionTitle(sectionTitle)
                .iconName(section.iconName)
                .preferenceKey(preferenceKey)
                .build());
    }

    private void collectUndoPostSettingsResults(SectionContext section) {
        String undoPostSettingsTitle = str("piko_pref_undo_posts_btn");
        String sectionTitle = section.sectionTitle;
        String keywords = joinSearchText(
                section.sectionTitle,
                section.rowTitle,
                undoPostSettingsTitle,
                resourceString("undo_tweet"),
                resourceString("undo_tweet_title"),
                resourceString("early_access_undo_tweet_title"),
                resourceString("early_access_undo_tweet_subtitle")
        );

        addExternalSettingsResult(
                resourceString("undo_tweet_period", "Undo post period"),
                undoPostSettingsTitle,
                resourceString("undo_tweet_pref_summary"),
                keywords,
                sectionTitle,
                section,
                UNDO_POST_SETTINGS_ACTIVITY_CLASS
        );
        addExternalSettingsResult(
                resourceString("original_tweets", "Original posts"),
                undoPostSettingsTitle,
                "",
                keywords,
                sectionTitle,
                section,
                UNDO_POST_SETTINGS_ACTIVITY_CLASS
        );
        addExternalSettingsResult(
                resourceString("replies", "Replies"),
                undoPostSettingsTitle,
                "",
                keywords,
                sectionTitle,
                section,
                UNDO_POST_SETTINGS_ACTIVITY_CLASS
        );
    }

    private void addExternalSettingsResult(
            String title,
            String displaySummary,
            String searchSummary,
            String keywords,
            String sectionTitle,
            SectionContext section,
            String externalActivityClassName
    ) {
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(externalActivityClassName)) {
            return;
        }

        addResult(SearchResult.builder(title, section.destinationKey)
                .summary(displaySummary)
                .searchSummary(searchSummary)
                .searchKeywords(keywords)
                .sectionTitle(sectionTitle)
                .iconName(section.iconName)
                .summarySeparator(SettingsSearchMatcher.NESTED_SEARCH_SUMMARY_SEPARATOR)
                .externalDestination(externalActivityClassName, title)
                .build());
    }

    private void addResult(SearchResult result) {
        if (collectedResults == null || TextUtils.isEmpty(result.title) || TextUtils.isEmpty(result.destinationKey)) {
            return;
        }

        for (SearchResult existing : collectedResults) {
            if (existing.sameTarget(result)) {
                return;
            }
        }
        collectedResults.add(result);
    }

    private static String resourceString(String resourceName) {
        return resourceString(resourceName, "");
    }

    private static String resourceString(String resourceName, String fallback) {
        try {
            String value = ResourceUtils.getString(resourceName);
            if (!TextUtils.isEmpty(value) && !TextUtils.equals(value, resourceName)) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static String joinSearchText(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private static String preferenceText(CharSequence text) {
        return text == null ? "" : text.toString();
    }

}
