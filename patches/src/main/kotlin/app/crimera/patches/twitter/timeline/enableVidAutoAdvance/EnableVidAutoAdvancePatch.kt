package app.crimera.patches.twitter.timeline.enableVidAutoAdvance

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object EnableVidAutoAdvancePatchFingerprint : Fingerprint(
    filters = listOf(
        string("immersive_video_auto_advance_duration_threshold"),
        opcode(Opcode.MOVE_RESULT)
    )
)

@Suppress("unused")
val enableVidAutoAdvancePatch =
    bytecodePatch(
        name = "Control video auto scroll",
        description = "Control video auto scroll in immersive view",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = EnableVidAutoAdvancePatchFingerprint.method
            val matches = EnableVidAutoAdvancePatchFingerprint.instructionMatches
            val loc = matches[1].index
            method.addInstruction(
                loc,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->enableVidAutoAdvance()I
                """.trimIndent(),
            )

            SettingsStatusLoadFingerprint.enableSettings("enableVidAutoAdvance")
        }
    }
