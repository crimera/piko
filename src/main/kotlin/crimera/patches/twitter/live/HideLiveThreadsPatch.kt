package crimera.patches.twitter.live

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
import crimera.patches.twitter.live.fingerprints.HideLiveThreadsFingerprint

@Patch(
    name = "Hide Live Threads",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
@Suppress("unused")
object HideLiveThreadsPatch :  BytecodePatch(
    setOf(HideLiveThreadsFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideLiveThreadsFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val loc = instructions.first{it.opcode == Opcode.IGET_OBJECT}.location.index
        val reg = method.getInstruction<OneRegisterInstruction>(loc).registerA
        
        method.addInstruction(loc+1,"""
            const v$reg, 0x0
        """.trimIndent())

    }
}