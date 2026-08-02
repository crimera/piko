/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import static app.morphe.extension.twitter.settings.SettingsSearchMatcher.isEmpty;
import static app.morphe.extension.twitter.settings.SettingsSearchMatcher.textEquals;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;

import app.morphe.extension.twitter.settings.SettingsSearchMatcher.SearchResult;

public final class SettingsSearchNavigator {
    private static final int HIGHLIGHT_START_DELAY_MS = 300;
    private static final int HIGHLIGHT_DURATION_MS = 2000;

    private SettingsSearchNavigator() {
    }

    static void openResult(Context context, SearchResult result) {
        if (result == null) {
            return;
        }

        if (result.opensExternalSettings()) {
            startExternalSettingsActivity(
                    context,
                    result.externalActivityClassName,
                    result.externalSearchTargetTitle()
            );
            return;
        }

        Bundle bundle = new Bundle();
        addSearchTarget(bundle, result);
        SettingsSearchUIController.reset();
        ActivityHook.startActivity(result.destinationKey, bundle);
    }

    @Nullable
    public static Preference findTargetPreference(PreferenceGroup group, @Nullable Bundle bundle) {
        if (group == null || bundle == null) {
            return null;
        }

        String targetKey = bundle.getString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_KEY, "");
        String targetTitle = bundle.getString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_TITLE, "");
        if (TextUtils.isEmpty(targetKey) && TextUtils.isEmpty(targetTitle)) {
            return null;
        }

        String targetSummary = bundle.getString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_SUMMARY, "");
        return findTargetPreference(group, targetKey, targetTitle, targetSummary);
    }

    public static void scrollToPreferenceAndHighlight(ListView listView, Preference searchTargetPreference) {
        if (listView == null || searchTargetPreference == null) {
            return;
        }

        listView.post(() -> {
            ListAdapter adapter = listView.getAdapter();
            if (adapter == null) {
                return;
            }

            for (int index = 0; index < adapter.getCount(); index++) {
                if (adapter.getItem(index) == searchTargetPreference) {
                    int targetIndex = index;
                    listView.setSelection(Math.max(targetIndex - 1, 0));
                    listView.postDelayed(
                            () -> highlightVisibleSearchTargetRow(
                                    listView,
                                    searchTargetPreference,
                                    targetIndex,
                                    0
                            ),
                            120
                    );
                    return;
                }
            }
        });
    }

    static void highlightRow(View row, HighlightTargetValidator targetValidator) {
        if (row == null || targetValidator == null) {
            return;
        }

        new RowHighlightOperation(row, targetValidator).schedule();
    }

    private static void addSearchTarget(Bundle bundle, SearchResult result) {
        bundle.putString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_KEY, result.preferenceKey);
        bundle.putString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_TITLE, result.title);
        bundle.putString(ActivityHook.EXTRA_SETTINGS_SEARCH_TARGET_SUMMARY, result.summary);
    }

    private static void startExternalSettingsActivity(Context context, String activityClassName, String targetTitle) {
        if (TextUtils.isEmpty(activityClassName)) {
            return;
        }
        ExternalSettingsRowHighlighter.schedule(
                context instanceof Activity ? (Activity) context : null,
                activityClassName,
                targetTitle
        );
        SettingsSearchUIController.reset();
        app.morphe.extension.twitter.Utils.startActivityFromClassName(activityClassName);
    }

    @Nullable
    private static Preference findTargetPreference(PreferenceGroup group, String targetKey, String targetTitle, String targetSummary) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (matchesSearchTarget(preference, targetKey, targetTitle, targetSummary)) {
                return preference;
            }
            if (preference instanceof PreferenceGroup) {
                Preference child = findTargetPreference((PreferenceGroup) preference, targetKey, targetTitle, targetSummary);
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private static boolean matchesSearchTarget(Preference preference, String targetKey, String targetTitle, String targetSummary) {
        CharSequence title = preference.getTitle();
        CharSequence summary = preference.getSummary();
        return matchesSearchTargetValues(
                preference.getKey(),
                title == null ? "" : title.toString(),
                summary == null ? "" : summary.toString(),
                targetKey,
                targetTitle,
                targetSummary
        );
    }

    static boolean matchesSearchTargetValues(
            String preferenceKey,
            String title,
            String summary,
            String targetKey,
            String targetTitle,
            String targetSummary
    ) {
        if (!isEmpty(targetKey)) {
            return textEquals(preferenceKey, targetKey)
                    && (isEmpty(targetTitle) || textEquals(title, targetTitle));
        }
        return textEquals(title, targetTitle)
                && (isEmpty(targetSummary) || textEquals(summary, targetSummary));
    }

    private static void highlightVisibleSearchTargetRow(
            ListView listView,
            Preference searchTargetPreference,
            int targetIndex,
            int attempts
    ) {
        int childIndex = targetIndex - listView.getFirstVisiblePosition();
        if (childIndex < 0 || childIndex >= listView.getChildCount()) {
            if (attempts < 5) {
                listView.postDelayed(
                        () -> highlightVisibleSearchTargetRow(
                                listView,
                                searchTargetPreference,
                                targetIndex,
                                attempts + 1
                        ),
                        120
                );
            }
            return;
        }

        View targetRow = listView.getChildAt(childIndex);
        if (!isCurrentVisibleTarget(listView, searchTargetPreference, targetIndex, targetRow)) {
            return;
        }

        new RowHighlightOperation(
                targetRow,
                () -> isCurrentVisibleTarget(listView, searchTargetPreference, targetIndex, targetRow)
        ).schedule();
    }

    private static boolean isCurrentVisibleTarget(
            ListView listView,
            Preference searchTargetPreference,
            int targetIndex,
            View targetRow
    ) {
        ListAdapter adapter = listView.getAdapter();
        if (adapter == null || targetIndex < 0 || targetIndex >= adapter.getCount()) {
            return false;
        }

        int firstVisiblePosition = listView.getFirstVisiblePosition();
        int childIndex = targetIndex - firstVisiblePosition;
        View visibleRow = childIndex >= 0 && childIndex < listView.getChildCount()
                ? listView.getChildAt(childIndex)
                : null;
        return matchesVisibleTarget(
                targetIndex,
                firstVisiblePosition,
                childIndex,
                adapter.getItem(targetIndex),
                searchTargetPreference,
                visibleRow,
                targetRow
        );
    }

    static boolean matchesVisibleTarget(
            int targetIndex,
            int firstVisiblePosition,
            int childIndex,
            Object adapterItem,
            Object expectedItem,
            Object visibleRow,
            Object expectedRow
    ) {
        return childIndex == targetIndex - firstVisiblePosition
                && adapterItem == expectedItem
                && visibleRow == expectedRow;
    }

    interface HighlightTargetValidator {
        boolean isCurrent();
    }

    private static final class RowHighlightOperation implements View.OnAttachStateChangeListener {
        private final View row;
        private final HighlightTargetValidator targetValidator;
        private final Runnable startRunnable = this::start;
        private final Runnable finishRunnable = this::finish;
        private Drawable originalBackground;
        private Drawable highlightBackground;

        RowHighlightOperation(View row, HighlightTargetValidator targetValidator) {
            this.row = row;
            this.targetValidator = targetValidator;
        }

        void schedule() {
            row.addOnAttachStateChangeListener(this);
            row.postDelayed(startRunnable, HIGHLIGHT_START_DELAY_MS);
        }

        private void start() {
            if (!row.isAttachedToWindow() || !targetValidator.isCurrent()) {
                cleanup();
                return;
            }

            originalBackground = row.getBackground();
            highlightBackground = new ColorDrawable(
                    SettingsSearchColors.current(row.getContext()).searchTargetRowHighlightColor
            );
            row.setBackground(highlightBackground);
            row.invalidate();
            row.postDelayed(finishRunnable, HIGHLIGHT_DURATION_MS);
        }

        private void finish() {
            restoreBackground();
            cleanup();
        }

        private void restoreBackground() {
            if (highlightBackground != null && row.getBackground() == highlightBackground) {
                row.setBackground(originalBackground);
                row.invalidate();
            }
            highlightBackground = null;
        }

        private void cleanup() {
            row.removeCallbacks(startRunnable);
            row.removeCallbacks(finishRunnable);
            row.removeOnAttachStateChangeListener(this);
        }

        @Override
        public void onViewAttachedToWindow(View view) {
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            restoreBackground();
            cleanup();
        }
    }
}
