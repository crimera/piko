package app.crimera.patches.twitter.misc.roundOffNumbers

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

private object roundOffNumbersFingerprint : Fingerprint(
    strings = listOf(
        "%.1f",
        " ",
    )
)

@Suppress("unused")
val roundOffNumbersPatch =
    bytecodePatch(
        name = "Round off numbers",
        description = "Enable or disable rounding off numbers",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val methods = roundOffNumbersFingerprint.classDef.methods

            val method =
                methods.first {
                    it.parameters ==
                        listOf(
                            "Landroid/content/res/Resources;",
                            "D",
                        ) &&
                        it.returnType == "Ljava/lang/String;"
                }
            val instructions = method.instructions

            val M = "sget-boolean v1, $PREF_DESCRIPTOR;->ROUND_OFF_NUMBERS:Z"

            val move_res_obj = instructions.first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index
            val inv_vir = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }
            val sget_obj = instructions.first { it.opcode == Opcode.SGET_OBJECT }

            method.addInstructionsWithLabels(
                move_res_obj + 1,
                """
                $M
                if-nez v1, :cond
                goto :here
                """.trimIndent(),
                ExternalLabel("here", sget_obj),
                ExternalLabel("cond", inv_vir),
            )

            SettingsStatusLoadFingerprint.enableSettings("roundOffNumbers")
        }
    }
