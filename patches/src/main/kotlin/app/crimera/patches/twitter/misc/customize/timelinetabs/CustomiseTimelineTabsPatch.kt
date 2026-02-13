package app.crimera.patches.twitter.misc.customize.timelinetabs

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private object CustomiseTimelineTabsFingerprint : Fingerprint(
    parameters = listOf("I", "I"),
    filters = listOf(
        string("null cannot be cast to non-null type android.app.Activity")
    )
)

@Suppress("unused")
val customiseTimelineTabsPatch =
    bytecodePatch(
        name = "Customize timeline top bar",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            CustomiseTimelineTabsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                     invoke-static {p1}, ${PREF_DESCRIPTOR};->timelineTab(I)I
                    move-result p1
                    """.trimIndent(),
                )

                SettingsStatusLoadFingerprint.enableSettings("timelineTabCustomisation")
            }
        }
    }
