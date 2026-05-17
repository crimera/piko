/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("SponsoredContentController.insertItem"),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Disable ads",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
        )
        execute {

            DisableAdsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )

                enableSettings("disableAds")
                addFlags("adsFlags")
            }
        }
    }
