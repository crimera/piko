package crimera.patches.twitter.premium.undoposts

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost1Fingerprint
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost2Fingerprint
import crimera.patches.twitter.premium.undoposts.fingerprints.UndoPost3Fingerprint

@Patch(
    name = "Enable Undo Posts",
    description = "Enable ability to undo posts before it gets posted",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object EnableUndoPostPatch :BytecodePatch(
    setOf(UndoPost1Fingerprint,UndoPost2Fingerprint,UndoPost3Fingerprint)
){
    override fun execute(context: BytecodeContext) {

        val result1 = UndoPost1Fingerprint.result
            ?: throw PatchException("UndoPost1Fingerprint not found")

        //removes flag check
        val method1 = result1.mutableMethod
        val loc1 = method1.getInstructions().first { it.opcode == Opcode.IF_EQZ }.location.index
        method1.removeInstruction(loc1)



        val result2 = UndoPost2Fingerprint.result
            ?: throw PatchException("UndoPost2Fingerprint not found")

        //removes flag check
        val method2 = result2.mutableMethod
        val loc2 = method2.getInstructions().first { it.opcode == Opcode.IF_EQZ }.location.index
        method2.removeInstruction(loc2)

        val result3 = UndoPost3Fingerprint.result
            ?: throw PatchException("UndoPost2Fingerprint not found")



        //removes flag check and always return true
        val method3 = result3.mutableMethod

        val instructions = method3.getInstructions()
        method3.removeInstructions(0, instructions.count())

       method3.addInstructions(0,"""
           sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
           return-object v0
       """.trimIndent())



        //end
    }

}