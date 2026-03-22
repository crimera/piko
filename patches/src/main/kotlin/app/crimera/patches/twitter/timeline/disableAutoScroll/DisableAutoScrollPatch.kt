/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private object DisableAutoScrollFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "applicationManager",
        "releaseCompletable",
        "preferences",
        "twSystemClock",
        "launchTracker",
        "cold_start_launch_time_millis",
    )
)

// credits to @Ouxyl
@Suppress("unused")
val disableAutoScrollPatch =
    bytecodePatch(
        name = "Disable auto timeline scroll on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = DisableAutoScrollFingerprint.classDef.methods.last()

            method.addInstructions(
                0,
                """
                const v0,0x0
                return v0
                """.trimIndent(),
            )
            SettingsStatusLoadFingerprint.enableSettings("disableAutoTimelineScroll")
        }
    }
