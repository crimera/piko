package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val COMPATIBILITY_NEW_X =
        Compatibility(
            name = "NewX",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets = listOf(
                AppTarget(version = "12.20.5-prod.01"),
                AppTarget(version = "12.21.1-prod.05"),
                AppTarget(version = "12.22.0-beta.01"),
                AppTarget(version = "12.22.0-prod.01"),
                AppTarget(version = "12.23.0-prod.01"),
            ),
        )

    const val EXTENSION_PACKAGE = "Lapp/morphe/extension/newx"
    const val SETTINGS_PACKAGE = "$EXTENSION_PACKAGE/settings"
    const val SETTINGS_REGISTRY_DESCRIPTOR = "$SETTINGS_PACKAGE/SettingsRegistry;"
    const val COMPOSE_SETTINGS_HOOK_DESCRIPTOR = "$SETTINGS_PACKAGE/ComposeSettingsHook;"
    const val TIMELINE_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/NewXTimelineFilter;"
    const val FOR_YOU_TOPIC_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/ForYouTopicFilter;"
    const val FOR_YOU_TOPIC_FILTER_FRAGMENT_DESCRIPTOR = "$EXTENSION_PACKAGE/timeline/ForYouTopicFilterFragment;"
    const val INLINE_ACTION_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/InlineActionFilter;"
    const val MEDIA_THUMBNAIL_LOADER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/MediaThumbnailLoader;"
    const val NAV_BAR_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/NavBarFilter;"
    const val DRAWER_ITEM_FILTER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/DrawerItemFilter;"
    const val REPLY_SORTING_RESOLVER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/ReplySortingResolver;"
    const val MEDIA_TAB_RESOLVER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/MediaTabResolver;"
    const val FONT_CLASS = "$EXTENSION_PACKAGE/misc/UpdateFont"
    const val FONT_UPDATE_DESCRIPTOR = "$FONT_CLASS;"
}
