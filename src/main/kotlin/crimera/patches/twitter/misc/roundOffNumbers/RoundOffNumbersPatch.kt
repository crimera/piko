package crimera.patches.twitter.misc.roundOffNumbers

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object RoundOffNumbersFingerprint: MethodFingerprint(
    strings = listOf(
        "%.1f",
        " ",
    )
)

@Patch(
    name = "Round off numbers",
    description = "Enable or disable rounding off numbers",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object RoundOffNumbersPatch:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint,RoundOffNumbersFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = RoundOffNumbersFingerprint.result
            ?:throw PatchException("RoundOffNumbersFingerprint not found")

        val methods = result.mutableClass.methods

        val method = methods.first { it.parameters == listOf("Landroid/content/res/Resources;","D") && it.returnType == "Ljava/lang/String;" }
        val instructions = method.getInstructions()

        val M = "sget-boolean v1, ${SettingsPatch.PREF_DESCRIPTOR};->ROUND_OFF_NUMBERS:Z"

        val move_res_obj = instructions.first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index
        val inv_vir = instructions.last { it.opcode == Opcode.INVOKE_VIRTUAL }
        val sget_obj = instructions.first { it.opcode == Opcode.SGET_OBJECT }

        method.addInstructionsWithLabels(move_res_obj+1,"""
            $M
            if-nez v1, :cond
            goto :here
        """.trimIndent(), ExternalLabel("here",sget_obj),ExternalLabel("cond",inv_vir)
        )

        SettingsStatusLoadFingerprint.enableSettings("roundOffNumbers")
        //end
    }


}