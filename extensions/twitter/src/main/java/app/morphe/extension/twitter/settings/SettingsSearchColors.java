/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

final class SettingsSearchColors {
    private static final SettingsSearchColors DARK = new SettingsSearchColors(
            0xff000000,
            0xff202327,
            0x2aeff3f4,
            0xffe7e9ea,
            0xff71767b,
            0x2eeff3f4
    );
    private static final SettingsSearchColors DIM = new SettingsSearchColors(
            0xff15202b,
            0xff273340,
            0x2aeff3f4,
            0xffe7e9ea,
            0xff71767b,
            0x2eeff3f4
    );
    private static final SettingsSearchColors LIGHT = new SettingsSearchColors(
            0xffffffff,
            0xffeff3f4,
            0x220f1419,
            0xff0f1419,
            0xff536471,
            0x1e0f1419
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
        return forTheme(app.morphe.extension.twitter.Utils.getTheme());
    }

    static SettingsSearchColors forTheme(String theme) {
        if ("dark".equals(theme)) {
            return DARK;
        }
        if ("dim".equals(theme)) {
            return DIM;
        }
        return LIGHT;
    }
}
