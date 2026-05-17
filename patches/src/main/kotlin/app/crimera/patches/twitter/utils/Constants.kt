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
            signatures =
                setOf(
                    // APK
                    "0fd9a0cfb07b65950997b4eaebdc53931392391aa406538a3b04073bc2ce2fe9",
                    // APKM
                    "45c53db089fb8d63a1c71154037c414b1b0646a141a73ae6e3bcbf8cd148402f",
                ),
            targets =
                listOf(
                    // Beta
                    AppTarget(
                        version = "11.80.0-alpha.1",
                        description = "Last alpha version without PairIP protection",
                    ),
                    // Beta
                    AppTarget(
                        version = "11.82.0-beta.1",
                        description = "Last beta version without PairIP protection",
                    ),
                    // Stable
                    AppTarget(
                        version = "11.81.0-release.0",
                        description = "Last stable version without PairIP protection",
                    ),
                    // PairIP version
                    AppTarget(
                        version = "11.91.0-release.0",
                        description = "Make sure the APK is PairIP bypassed (Check the support group)",
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
    const val UTILS_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Utils"
    const val ACTIVITY_SETTINGS_CLASS = "$INTEGRATIONS_PACKAGE/settings"
    const val ACTIVITY_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/ActivityHook;"
    const val DEEPLINK_HOOK_CLASS = "$ACTIVITY_SETTINGS_CLASS/DeepLink;"
    const val ADD_PREF_DESCRIPTOR =
        "$UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;"

    const val PREF_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/Pref"
    const val PATCHES_DESCRIPTOR = "$INTEGRATIONS_PACKAGE/patches"
    const val CUSTOMISE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/customise/Customise"
    const val NATIVE_DESCRIPTOR = "$PATCHES_DESCRIPTOR/nativeFeatures"

    const val SSTS_DESCRIPTOR = "invoke-static {}, $ACTIVITY_SETTINGS_CLASS/SettingsStatus;"
    const val FSTS_DESCRIPTOR = "invoke-static {}, $INTEGRATIONS_PACKAGE/patches/FeatureSwitchPatch;"
}
