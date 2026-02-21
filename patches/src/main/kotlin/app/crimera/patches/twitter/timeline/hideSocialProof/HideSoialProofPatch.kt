package app.crimera.patches.twitter.timeline.hideSocialProof

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private object HideSocialProofPatchFingerprint : Fingerprint(
    definingClass = "SocialProofView;",
    name = "setSocialProofData"
)

@Suppress("unused")
val hideSocialProofPatch =
    bytecodePatch(
        name = "Hide followed by context",
        description = "Hides followed by context under profile",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val HOOK_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->hideSocialProof()Z"
            val method = HideSocialProofPatchFingerprint.method
            val instructions = method.instructions

            method.addInstructionsWithLabels(
                0,
                """
                $HOOK_DESCRIPTOR
                move-result v0
                if-eqz v0, :piko
                return-void
                """.trimIndent(),
                ExternalLabel("piko", instructions.first { it.opcode == Opcode.CONST_4 }),
            )
            SettingsStatusLoadFingerprint.enableSettings("hideSocialProof")
        }
    }
