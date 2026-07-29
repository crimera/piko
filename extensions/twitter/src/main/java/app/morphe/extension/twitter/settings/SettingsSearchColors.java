/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.content.Context;

import app.morphe.extension.twitter.Utils;

final class SettingsSearchColors {
    final int settingsBackgroundColor;
    final int searchFieldBackgroundColor;
    final int searchFieldPressedColor;
    final int searchFieldBorderColor;
    final int searchTextColor;
    final int searchHintColor;
    final int searchTargetRowHighlightColor;

    private SettingsSearchColors(
            int settingsBackgroundColor,
            int searchFieldBackgroundColor,
            int searchFieldPressedColor,
            int searchFieldBorderColor,
            int searchTextColor,
            int searchHintColor,
            int searchTargetRowHighlightColor
    ) {
        this.settingsBackgroundColor = settingsBackgroundColor;
        this.searchFieldBackgroundColor = searchFieldBackgroundColor;
        this.searchFieldPressedColor = searchFieldPressedColor;
        this.searchFieldBorderColor = searchFieldBorderColor;
        this.searchTextColor = searchTextColor;
        this.searchHintColor = searchHintColor;
        this.searchTargetRowHighlightColor = searchTargetRowHighlightColor;
    }

    static SettingsSearchColors current(Context context) {
        int settingsBackgroundColor = Utils.resolveColor(context, "coreColorAppBackground");
        int searchTextColor = Utils.resolveColor(context, "coreColorPrimaryText");

        return new SettingsSearchColors(
                settingsBackgroundColor,
                Utils.resolveColor(context, "abstractColorFadedGray"),
                Utils.resolveColor(context, "coreColorPressed"),
                Utils.resolveColor(context, "coreColorBorder"),
                searchTextColor,
                Utils.resolveColor(context, "coreColorSecondaryText"),
                Utils.resolveColor(context, "abstractColorHighlightBackground")
        );
    }
}
