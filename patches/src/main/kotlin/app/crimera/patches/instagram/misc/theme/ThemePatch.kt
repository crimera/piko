/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch

@Suppress("unused")
val themePatch =
    resourcePatch(
        name = "Theme",
        description =
            "Adds Material You and AMOLED controls to Piko settings " +
                "on Android 12 and later. On Android 8–11, it applies a fixed " +
                "Material You-style theme or an optional AMOLED theme.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        val amoled by booleanOption(
            key = "amoled",
            default = false,
            title = "Pure-black AMOLED theme for Android 8–11",
            description =
                "Use a fixed pure-black AMOLED theme instead of the fixed Material " +
                    "You-style theme on Android 8–11. On Android 12 and later, use " +
                    "the AMOLED control in Piko settings.",
        )

        var bytecodePatchContext: BytecodePatchContext? = null

        dependsOn(
            settingsPatch,
            sharedExtensionPatch,
            resourceMappingPatch,
            bytecodePatch {
                execute {
                    bytecodePatchContext = this
                }
            },
        )

        execute {
            try {
                val originalApi31Base = captureApi31Base()

                forceWhiteOnMediaChrome()
                preserveCreationButtonContrast()
                preserveLightClipsComposerContrast()
                preserveLightOverflowStampBackgrounds()
                applyLegacyTheme(amoled == true)

                restoreApi31Base(
                    snapshot = originalApi31Base,
                )
                writeMaterialYouOverlay(night = false)
                writeMaterialYouOverlay(night = true)
                writeAmoledOverlay(originalApi31Base)
                writeAmoledMaterialYouOverlay()

                context(requireNotNull(bytecodePatchContext)) {
                    installComposePrismBlackRuntime(legacyAmoled = amoled == true)
                    installSystemDefaultUiModeHook()
                    installThemeLifecycleHooks()
                    installNativeThemeModeSync()
                }
            } finally {
                bytecodePatchContext = null
            }
        }
    }
