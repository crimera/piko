package app.crimera.patches.twitter.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_X =
        Compatibility(
            name = "Twitter",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets =
                listOf(
                    AppTarget(
                        version = "12.19.1-release.0",
                    ),
                ),
        )

    const val INTEGRATIONS_PACKAGE = "Lapp/morphe/extension/twitter"
    const val STRING_REF_DESCRIPTOR = "Lapp/morphe/extension/shared/StringRef"
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val ACTIVITY_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/ActivityHook;"
    const val DEEPLINK_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/DeepLink;"

    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val CUSTOMISE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/customise/Customise"
    const val NATIVE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/nativeFeatures"

    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val FSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/patches/FeatureSwitchPatch;"

    const val ENTITY_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/entity/"
}
