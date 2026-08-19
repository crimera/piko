package app.crimera.patches.xlite.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    /*
     * Compatibility paths:
     * - BETA PATH (12.18.0-beta.0): current path; keep this as the source for future updates.
     * - ALPHA PATH (12.17.3-alpha.01): temporary backwards-compatibility path.
     *
     * Deprecation note: if future releases continue the BETA PATH structure, remove the alpha
     * target and all ALPHA PATH branches after the last alpha release is dropped.
     * TODO: Reconfirm the target list and run both compatibility checks before removing alpha.
     */
    val COMPATIBILITY_X_LITE =
        Compatibility(
            name = "X-Lite",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets = listOf(
                // ALPHA PATH: temporary compatibility target.
                AppTarget(version = "12.17.3-alpha.01"),
                // BETA PATH: current compatibility target.
                AppTarget(version = "12.18.0-beta.0"),
                AppTarget(version = "12.19.0-beta.0"),
            ),
        )

    const val EXTENSION_PACKAGE = "Lapp/morphe/extension/xlite"
    const val SETTINGS_PACKAGE = "$EXTENSION_PACKAGE/settings"
    const val SETTINGS_REGISTRY_DESCRIPTOR = "$SETTINGS_PACKAGE/SettingsRegistry;"
    const val COMPOSE_SETTINGS_HOOK_DESCRIPTOR = "$SETTINGS_PACKAGE/ComposeSettingsHook;"
    const val TIMELINE_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/XLiteTimelineFilter;"
    const val FOR_YOU_TOPIC_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/ForYouTopicFilter;"
    const val FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/ForYouTopicFilterFragment;"
    const val INLINE_ACTION_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/InlineActionFilter;"
    const val NAV_BAR_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/NavBarFilter;"
    const val DRAWER_ITEM_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/DrawerItemFilter;"
    const val REPLY_SORTING_RESOLVER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/ReplySortingResolver;"
    const val MEDIA_TAB_RESOLVER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/MediaTabResolver;"
    const val FONT_CLASS = "$EXTENSION_PACKAGE/misc/UpdateFont"
    const val FONT_UPDATE_DESCRIPTOR = "$FONT_CLASS;"
}
