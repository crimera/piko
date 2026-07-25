/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.shared.ResourceUtils;

final class SettingsSearchColors {
       private static final SettingsSearchColors CLR = new SettingsSearchColors(
            Utils.resolveColor("coreColorAppBackground"),
            ResourceUtils.getColor("black_opacity_15"),
            Utils.resolveColor("coreColorSelectionHighlightBackground"),
            Utils.resolveColor("coreColorSelectionHighlightText"),
            Utils.resolveColor("coreColorSelectionHighlightText"),
            Utils.resolveColor("coreColorSelectionHighlightBackground")
    );

    final int settingsBackgroundColor;
    final int searchFieldBackgroundColor;
    final int searchFieldTapHighlightColor;
    final int searchTextColor;
    final int searchHintColor;
    final int searchTargetRowHighlightColor;

    private SettingsSearchColors(
            int settingsBackgroundColor,
            int searchFieldBackgroundColor,
            int searchFieldTapHighlightColor,
            int searchTextColor,
            int searchHintColor,
            int searchTargetRowHighlightColor
    ) {
        this.settingsBackgroundColor = settingsBackgroundColor;
        this.searchFieldBackgroundColor = searchFieldBackgroundColor;
        this.searchFieldTapHighlightColor = searchFieldTapHighlightColor;
        this.searchTextColor = searchTextColor;
        this.searchHintColor = searchHintColor;
        this.searchTargetRowHighlightColor = searchTargetRowHighlightColor;
    }

    static SettingsSearchColors current() {
        return CLR;
    }
}
