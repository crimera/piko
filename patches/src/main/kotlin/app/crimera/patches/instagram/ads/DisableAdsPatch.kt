/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
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
        dependsOn(settingsPatch)
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
            }
        }
    }
