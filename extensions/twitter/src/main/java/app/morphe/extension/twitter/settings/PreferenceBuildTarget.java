/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.preference.Preference;

import androidx.annotation.Nullable;

import com.twitter.ui.widget.LegacyTwitterPreferenceCategory;

interface PreferenceBuildTarget {
    default void beginSection(SectionContext section) {
    }

    default boolean acceptsSectionContents(SectionContext section) {
        return true;
    }

    default void endSection() {
    }

    void addCategory(LegacyTwitterPreferenceCategory category);

    void addPreference(@Nullable LegacyTwitterPreferenceCategory category, Preference preference);

    final class SectionContext {
        final String rowTitle;
        final String sectionTitle;
        final String destinationKey;
        final String iconName;

        SectionContext(String rowTitle, String sectionTitle, String destinationKey, String iconName) {
            this.rowTitle = rowTitle;
            this.sectionTitle = sectionTitle;
            this.destinationKey = destinationKey;
            this.iconName = iconName;
        }
    }
}
