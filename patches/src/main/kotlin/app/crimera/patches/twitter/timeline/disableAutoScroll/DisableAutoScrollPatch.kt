package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

private val disableAutoScrollFingerprint =
    fingerprint {
        strings(
            "applicationManager",
            "releaseCompletable",
            "preferences",
            "twSystemClock",
            "launchTracker",
            "cold_start_launch_time_millis",
        )

        returns("V")
    }

// credits to @Ouxyl
@Suppress("unused")
val disableAutoScrollPatch =
    bytecodePatch(
        name = "Disable auto timeline scroll on launch",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = disableAutoScrollFingerprint.classDef.methods.last()

            method.addInstructions(
                0,
                """
                const v0,0x0
                return v0
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("disableAutoTimelineScroll")
        }
    }
