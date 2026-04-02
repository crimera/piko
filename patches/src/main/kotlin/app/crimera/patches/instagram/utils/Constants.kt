/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_INSTAGRAM =
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets =
                listOf(
                    // Stable
                    AppTarget(
                        version = "422.0.0.44.64",
                        description = "Instagram Stable version (all archs)"
                    ),
                    // Alpha
                    AppTarget(
                        version = "424.0.0.0.39",
                        description = "Instagram Alpha version (arm64-v8a only)"
                    ),
                ),
        )

    // Instagram classes.
    const val FRIENDSHIP_STATUS_CLASS = "Lcom/instagram/user/model/FriendshipStatus;"

    // Extension classes.
    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/instagram"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val ACTIVITY_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/ActivityHook;"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/utils"

    const val PREF_DESCRIPTOR = "$UTILS_DESCRIPTOR/Pref;"
    const val PREF_CALL_DESCRIPTOR = "invoke-static {}, $PREF_DESCRIPTOR"

    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val JSONPARSER_CHECK_DESCRIPTOR = """invoke-static {v%s}, $PATCHES_DESCRIPTOR/Block;->replaceJsonParserKey(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v%s"""

    const val LINKS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/Links;"
    const val DOWNLOAD_DESCRIPTOR = "$PATCHES_DESCRIPTOR/download"
    const val UI_CONSTANTS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/constants/UI;"

    const val ACTIVITY_SETTINGS_STATUS_CLASS = "$ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val ENTITY_CLASS = "$INTEGRATIONS_PACKAGE/entity"
    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_STATUS_CLASS->%s()V"
}
