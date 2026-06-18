package app.crimera.patches.twitter.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_X =
        Compatibility(
            name = "X",
            packageName = "com.twitter.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0x000000,
            targets =
                listOf(
                    AppTarget(
                        version = "11.81.0-release.0",
                        description = "Last stable version without PairIP protection",
                    ),
                    // PairIP-ripped version
                    AppTarget(
                        version = "11.99.0-release-ripped.1",
                        description = "Make sure the APK is PairIP bypassed (Check the support group)",
                    ),
                    // Shim version, can be dropped once APKMirror uploads 12.0.0-release.0
                    AppTarget(
                        version = "11.99.0-release.1",
                        description = "Requires Piko-Shim to be included",
                    ),
                    // Shim version
                    AppTarget(
                        version = "12.0.0-release.0",
                        description = "Requires Piko-Shim to be included",
                    ),
                ),
        )
    val COMPATIBILITY_X_11_69 =
        COMPATIBILITY_X
            .including(
                AppTarget(
                    version = "11.69.0-release.0",
                    description = "Last stable version which had old share sheet and DMs (Disunify XChat Subsystem patch)",
                ),
            ).excluding(null)

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
}
