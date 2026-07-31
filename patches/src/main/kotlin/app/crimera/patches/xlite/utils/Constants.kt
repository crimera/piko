package app.crimera.patches.xlite.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val COMPATIBILITY_X_LITE =
        Compatibility(
            name = "X-Lite",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets =
                listOf(
                    AppTarget(version = "12.7.1-release.0"),
                    AppTarget(version = "12.10.1-release.0"),
                    AppTarget(version = "12.11.0-release.0"),
                    AppTarget(version = "12.12.0-release.0"),
                    AppTarget(version = "12.13.0-beta.0"),
                ),
        )

    const val EXTENSION_PACKAGE = "Lapp/morphe/extension/xlite"
    const val SETTINGS_PACKAGE = "$EXTENSION_PACKAGE/settings"
    const val SETTINGS_REGISTRY_DESCRIPTOR = "$SETTINGS_PACKAGE/SettingsRegistry;"
    const val COMPOSE_SETTINGS_HOOK_DESCRIPTOR = "$SETTINGS_PACKAGE/ComposeSettingsHook;"
    const val TIMELINE_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/XLiteTimelineFilter;"
    const val INLINE_ACTION_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/InlineActionFilter;"
    const val NAV_BAR_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/NavBarFilter;"
    const val DRAWER_ITEM_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/DrawerItemFilter;"
    const val REPLY_SORTING_RESOLVER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/ReplySortingResolver;"
}
