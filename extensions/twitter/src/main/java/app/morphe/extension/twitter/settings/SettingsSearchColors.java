/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.content.Context;

import app.morphe.extension.twitter.Utils;

final class SettingsSearchColors {
    private static final int LIGHT_ROW_HIGHLIGHT_ALPHA = 0x1e;
    private static final int DARK_ROW_HIGHLIGHT_ALPHA = 0x2e;

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
                withAlpha(
                        searchTextColor,
                        isLightColor(settingsBackgroundColor)
                                ? LIGHT_ROW_HIGHLIGHT_ALPHA
                                : DARK_ROW_HIGHLIGHT_ALPHA
                )
        );
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static boolean isLightColor(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        return red * 299 + green * 587 + blue * 114 >= 128000;
    }
}
