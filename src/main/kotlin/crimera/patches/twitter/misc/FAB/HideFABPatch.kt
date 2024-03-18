package crimera.patches.twitter.misc.FAB

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.FAB.fingerprints.HideFABFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hide FAB",
    compatiblePackages = [CompatiblePackage("com.twitter.android")] ,
    use = false
)
@Suppress("unused")
class HideFABPatch :BytecodePatch(
    setOf(HideFABFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideFABFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()
        val constObj = instructions.last { it.opcode == Opcode.CONST_4 }

        method.addInstructionsWithLabels(0,"""
            invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideFAB()Z
            move-result v0
            if-nez v0, :cond_1212
        """.trimIndent(),
            ExternalLabel("cond_1212",constObj)
        )

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideFAB()V"
        )
    }
}