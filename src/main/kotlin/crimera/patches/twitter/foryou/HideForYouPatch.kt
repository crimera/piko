package crimera.patches.twitter.foryou

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.foryou.fingerprints.HideForYouFingerprint

@Patch(
    name = "Hide For You",
    description = "Hides For You tab from timeline",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object HideForYouPatch : BytecodePatch(
    setOf(HideForYouFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideForYouFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions();

        val check = instructions.first { it.opcode == Opcode.CONST_16 }.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(check).registerA

        method.addInstruction(check+1,"""
            const/16 v$reg, 0x22
        """.trimIndent())


    }
}
