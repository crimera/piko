/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi.ARM64_V8A

object Constants {
    val COMPATIBILITY_INSTAGRAM =
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0xFC483C,
            targets =
                listOf(
                    // Stable
                    AppTarget(
                        version = "435.0.0.37.76",
                        versionCodes =
                            mapOf(
                                ARM64_V8A to 384109456,
                            ),
                    ),
                ),
        )

    // Instagram classes.
    const val FRAGMENT_ACTIVITY = "Landroidx/fragment/app/FragmentActivity;"
    const val FRIENDSHIP_STATUS_CLASS = "Lcom/instagram/user/model/FriendshipStatus;"
    const val EDIT_MEDIA_INFO_FRAGMENT_CLASS = "Linstagram/features/creation/fragment/EditMediaInfoFragment;"
    const val EXTENDED_IMAGE_URL_CLASS = "Lcom/instagram/model/mediasize/ExtendedImageUrl;"
    const val MEDIA_OPTIONS_CLASS = "Lcom/instagram/feed/media/mediaoption/MediaOption\$Option;"
    const val USER_SESSION_CLASS = "Lcom/instagram/common/session/UserSession;"
    const val USER_DETAIL_VIEW_MODEL_CLASS = "Lcom/instagram/profile/fragment/UserDetailViewModel;"

    // Extension classes.
    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/instagram"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/utils"
    const val CONSTANTS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/constants"
    const val ENTITY_CLASS = "$INTEGRATIONS_PACKAGE/entity"

    const val PREF_DESCRIPTOR = "$UTILS_DESCRIPTOR/Pref;"
    const val PREF_CALL_DESCRIPTOR = "invoke-static {}, $PREF_DESCRIPTOR"

    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val JSONPARSER_CHECK_DESCRIPTOR = """invoke-static {v%s}, $PATCHES_DESCRIPTOR/Block;->replaceJsonParserKey(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v%s"""

    const val LINKS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/Links;"
    const val DOWNLOAD_DESCRIPTOR = "$PATCHES_DESCRIPTOR/download"
    const val ACTIONBAR_DESCRIPTOR = "$PATCHES_DESCRIPTOR/actionbar"

    const val OVERFLOW_MENU_BUTTON_CLASS = "$PATCHES_DESCRIPTOR/overflowMenuButton"
    const val ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS = "$OVERFLOW_MENU_BUTTON_CLASS/reels/AddReelButton;"
    const val FEED_OVERFLOW_MENU_BUTTON_CLASS = "$OVERFLOW_MENU_BUTTON_CLASS/FeedButton;"
    const val ACTIVITY_SETTINGS_STATUS_CLASS = "$ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_STATUS_CLASS->%s()V"
    const val HOOK_FLAGS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/HookFlags;"
    const val LOAD_FLAGS_DESCRIPTOR = "invoke-static {}, $HOOK_FLAGS_DESCRIPTOR->%s()V"

    const val COMMENT_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/comment"
}
