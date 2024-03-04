package crimera.patches.twitter.misc.FAB

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.FAB.fingerprints.HideFABFingerprint

@Patch(
    name = "Hide FAB Menu Buttons",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
class HideFABMenuButtonsPatch : BytecodePatch(
    setOf(HideFABFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideFABFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()
        val loc = instructions.last { it.opcode == Opcode.CONST_STRING }.location.index+2
        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA

        method.addInstructions(loc+1,"""
            const v$reg, false
        """.trimIndent(),
        )

    }
}