package app.crimera.patches.twitter.timeline.hideSocialProof

import app.crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

internal val hideSocialProofPatchFingerprint =
    fingerprint {

        custom { methodDef, classDef ->
            classDef.type.endsWith("SocialProofView;") && methodDef.name == "setSocialProofData"
        }
    }

@Suppress("unused")
val hideSocialProofPatch =
    bytecodePatch(
        name = "Hide followed by context",
        description = "Hides followed by context under profile",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val HOOK_DESCRIPTOR =
                "invoke-static {}, $PREF_DESCRIPTOR;->hideSocialProof()Z"
            val method = hideSocialProofPatchFingerprint.method
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
            settingsStatusLoadFingerprint.method.enableSettings("hideSocialProof")
        }
    }
