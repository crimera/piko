package crimera.patches.twitter.misc.FAB

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.FAB.fingerprints.HideFABFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hide FAB Menu Buttons",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
class HideFABMenuButtonsPatch : BytecodePatch(
    setOf(HideFABFingerprint, SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideFABFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideFABBtn()Z"

        val method = result.mutableMethod
        val instructions = method.getInstructions()
        val loc = instructions.last { it.opcode == Opcode.CONST_STRING }.location.index+2

        method.addInstructions(loc,PREF)

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideFABBtns()V"
        )
    //end
    }
}