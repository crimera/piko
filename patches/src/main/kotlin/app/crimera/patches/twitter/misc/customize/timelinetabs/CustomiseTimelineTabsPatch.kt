package app.crimera.patches.twitter.misc.customize.timelinetabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

private val customiseTimelineTabsFingerprint =
    fingerprint {
        parameters("I", "I")
        strings(
            "null cannot be cast to non-null type android.app.Activity",
        )
    }

@Suppress("unused")
val customiseTimelineTabsPatch =
    bytecodePatch(
        name = "Customize timeline top bar",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            customiseTimelineTabsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                     invoke-static {p1}, ${PREF_DESCRIPTOR};->timelineTab(I)I
                    move-result p1
                    """.trimIndent(),
                )

                settingsStatusLoadFingerprint.enableSettings("timelineTabCustomisation")
            }
        }
    }
