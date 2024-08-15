package crimera.patches.twitter.timeline.disableAutoScroll


import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val disableAutoScrollFingerprint = fingerprint {
    returns("V")
    strings(
        "applicationManager",
        "releaseCompletable",
        "preferences",
        "twSystemClock",
        "launchTracker",
        "cold_start_launch_time_millis",
    )
}

//credits to @Ouxyl
// TODO: make separate integration
@Suppress("unused")
val disableAutoScrollPatch = bytecodePatch(
    name = "Disable auto timeline scroll on launch",
) {
    compatibleWith("com.twitter.android")

    val result by disableAutoScrollFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = result.mutableClass.methods.last()

        method.addInstructions(
            0, """
        const v0,0x0
        return v0
        """.trimIndent()
        )

        settingsStatusMatch.enableSettings("disableAutoTimelineScroll")
    }
}